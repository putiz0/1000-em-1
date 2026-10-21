package com.playbox.offline.core.downloads

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Download de um pack em HTTP com retomada por `Range`: o arquivo parcial
 * (`download-temp/{gameId}-{versão}.zip`) é reaberto na posição já baixada
 * e o servidor recebe `Range: bytes=offset-`.
 *
 * Comportamentos cobertos:
 *  - 206 (partial)            => anexa a partir do offset;
 *  - 200 com Range pedido     => servidor não suporta Range: recomeça do zero;
 *  - 416 (offset inválido)    => recomeça do zero;
 *  - a retomada usa o TAMANHO REAL do arquivo parcial (o disco manda, o
 *    offset do banco é só indicador) e a escrita nunca estende o arquivo
 *    (zero-pad criava zip corrompido após morte do processo/limpeza);
 *  - cancelamento externo ([cancelCurrent] ou cancel da corrotina) lança
 *    [CancellationException] rápido (pausa/retomada/limpeza responsáveis);
 *  - cada bloco baixado chama [onProgress] com o total absoluto de bytes;
 *    a gravação de progresso no banco é throttle pelo coordenador.
 *
 * A integridade final é garantida por SHA-256 (verificador do pacote).
 */
class HttpPackDownloader(private val client: OkHttpClient) {

    @Volatile
    private var currentCall: Call? = null

    /** Interrompe imediatamente a transferência em andamento (pausa). */
    fun cancelCurrent() {
        currentCall?.cancel()
    }

    data class Result(
        /** Offset absoluto final do arquivo (bytes efetivamente gravados). */
        val finalOffset: Long,
        /** true = servidor não suportou Range e o download recomecou do zero. */
        val restartedFromZero: Boolean
    )

    suspend fun downloadAppend(
        url: String,
        partFile: File,
        startOffset: Long,
        onProgress: suspend (absoluteBytes: Long) -> Unit
    ): Result = withContext(Dispatchers.IO) {
        // FONTE DA VERDADE: o arquivo em disco, não o offset salvo no banco.
        // O processo pode ter morrido com bytes não persistidos, o usuário pode
        // ter apagado os temporários ("Limpar arquivos temporários") ou uma
        // falha anterior pode ter deixado o arquivo curto. Retomar do offset
        // do banco nesses cenários EXPANDIRIA o arquivo com zeros (buraco) e
        // o zip viraria corrompido no verificador. Sempre retomamos do tamanho
        // real do arquivo (0 quando ele não existe).
        var offset = if (startOffset > 0) {
            partFile.parentFile?.mkdirs()
            if (partFile.exists()) partFile.length() else 0L
        } else {
            0L
        }
        var restartedFromZero = false

        while (true) {
            partFile.parentFile?.mkdirs()

            val request = Request.Builder()
                .url(url)
                .get()
                .apply { if (offset > 0) header("Range", "bytes=$offset-") }
                .build()

            val call = client.newCall(request)
            currentCall = call

            val restartFromZeroNow = try {
                call.execute().use { response ->
                    when {
                        // fluxo normal: 206 com offset, ou 200 começando do zero
                        response.code == 206 || (response.code in 200..299 && offset == 0L) -> {
                            val body = response.body ?: throw IOException("Resposta do servidor sem corpo.")
                            offset = writeStreamToFile(body, offset, partFile, onProgress)
                            false
                        }

                        // servidor recebeu Range e devolveu 200 completo: recomeçar do zero
                        response.code in 200..299 -> true

                        // offset marcado como inválido pelo servidor: recomeçar do zero
                        response.code == 416 && offset > 0 -> true

                        else -> throw IOException("Servidor de packs respondeu HTTP ${response.code}.")
                    }
                }
            } catch (e: IOException) {
                if (call.isCanceled()) {
                    throw CancellationException("download interrompido (pausa/cancelamento)")
                }
                throw e
            } finally {
                currentCall = null
            }

            if (!restartFromZeroNow) break

            // recomeço do zero acontece no máximo uma vez por download
            if (restartedFromZero) {
                throw IOException("Servidor não suporta retomada e o download já foi reiniciado.")
            }
            restartedFromZero = true
            offset = 0
            partFile.delete()
        }

        Result(finalOffset = offset, restartedFromZero = restartedFromZero)
    }

    private suspend fun writeStreamToFile(
        body: ResponseBody,
        offset: Long,
        partFile: File,
        onProgress: suspend (absoluteBytes: Long) -> Unit
    ): Long {
        RandomAccessFile(partFile, "rw").use { raf ->
            // JAMAIS estenda o arquivo aqui: estender criava um buraco de zeros
            // (quando o offset chega maior que o disco) e o zip viraria
            // corrompido. Encurtar é sempre seguro — o Range reenvia os bytes.
            val safeStart = minOf(offset, raf.length())
            raf.setLength(safeStart)
            raf.seek(safeStart)
            val buffer = ByteArray(64 * 1024)
            var written = 0L
            body.byteStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    raf.write(buffer, 0, read)
                    written += read
                    onProgress(safeStart + written)
                }
            }
            return safeStart + written
        }
    }
}

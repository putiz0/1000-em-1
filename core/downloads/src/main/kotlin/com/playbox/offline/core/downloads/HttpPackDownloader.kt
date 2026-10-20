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
        var offset = startOffset
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
            // o arquivo deve conter exatamente "offset" bytes antes de anexar
            raf.setLength(offset)
            raf.seek(offset)
            val buffer = ByteArray(64 * 1024)
            var written = 0L
            body.byteStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    raf.write(buffer, 0, read)
                    written += read
                    onProgress(offset + written)
                }
            }
            return offset + written
        }
    }
}

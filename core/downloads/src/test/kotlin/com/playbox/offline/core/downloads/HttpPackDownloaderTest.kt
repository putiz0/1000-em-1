package com.playbox.offline.core.downloads

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testes do download com e sem Range (fluxo de retomada real, via HTTP).
 */
class HttpPackDownloaderTest {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun tempDir(): File = Files.createTempDirectory("playbox-test").toFile()

    @Test
    fun `download completo do zero`() = runBlocking {
        val content = HttpTestServer.fakeBytes(512 * 1024 + 17) // > 1 chunk de 64KB
        val temp = tempDir()
        HttpTestServer(content).use { server ->
            val downloader = HttpPackDownloader(client)
            val partFile = File(temp, "part.zip")

            var progressCalls = 0
            var lastProgress = 0L
            val result = downloader.downloadAppend(
                url = server.url,
                partFile = partFile,
                startOffset = 0L
            ) { absolute ->
                progressCalls++
                lastProgress = absolute
            }

            assertTrue(partFile.exists())
            assertEquals(content.size.toLong(), partFile.length())
            assertContentEquals(content, partFile.readBytes())
            assertEquals(content.size.toLong(), result.finalOffset)
            assertTrue(!result.restartedFromZero)
            assertTrue(progressCalls > 1)
            assertEquals(content.size.toLong(), lastProgress)
        }
    }

    @Test
    fun `retomada com Range continua do offset salvo`() = runBlocking {
        val content = HttpTestServer.fakeBytes(400 * 1024)
        val temp = tempDir()
        HttpTestServer(content).use { server ->
            val downloader = HttpPackDownloader(client)
            val partFile = File(temp, "part.zip")

            // simula um download pausado na metade
            val offset = content.size.toLong() / 2
            partFile.writeBytes(content.slice(IntRange(0, offset.toInt() - 1)).toByteArray())

            val result = downloader.downloadAppend(
                url = server.url,
                partFile = partFile,
                startOffset = offset
            ) { }

            assertEquals(content.size.toLong(), result.finalOffset)
            assertTrue(!result.restartedFromZero)
            assertContentEquals(content, partFile.readBytes())
        }
    }

    @Test
    fun `servidor sem Range reinicia do zero`() = runBlocking {
        val content = HttpTestServer.fakeBytes(300 * 1024)
        val temp = tempDir()
        HttpTestServer(content, honorRange = false).use { server ->
            val downloader = HttpPackDownloader(client)
            val partFile = File(temp, "part.zip")

            val offset = 100_000L
            partFile.writeBytes(content.slice(IntRange(0, 99_999)).toByteArray())

            val result = downloader.downloadAppend(
                url = server.url,
                partFile = partFile,
                startOffset = offset
            ) { }

            assertEquals(content.size.toLong(), result.finalOffset)
            assertTrue(result.restartedFromZero)
            assertContentEquals(content, partFile.readBytes())
        }
    }

    @Test
    fun `retomada segue o tamanho real do disco e nunca cria buraco de zeros`() = runBlocking {
        val content = HttpTestServer.fakeBytes(400 * 1024)
        val temp = tempDir()
        HttpTestServer(content).use { server ->
            val downloader = HttpPackDownloader(client)
            val partFile = File(temp, "part.zip")

            // cenário real do erro "pacote corrompido": o banco acha que já
            // baixou mais bytes do que o disco tem (processo morto no meio da
            // escrita, temporário apagado, etc.). Sem a correção, setLength
            // estendia o arquivo com zeros e o hash final divergia.
            val realOnDisk = content.size / 4
            partFile.writeBytes(content.slice(IntRange(0, realOnDisk - 1)).toByteArray())
            val offsetSalvoNoBanco = realOnDisk.toLong() + 15_000L

            val result = downloader.downloadAppend(
                url = server.url,
                partFile = partFile,
                startOffset = offsetSalvoNoBanco
            ) { }

            // o arquivo final tem que ser EXATAMENTE o conteúdo completo
            assertEquals(content.size.toLong(), result.finalOffset)
            assertTrue(!result.restartedFromZero)
            assertContentEquals(content, partFile.readBytes())
        }
    }

    @Test
    fun `arquivo parcial apagado com offset salvo no banco recomeca do zero`() = runBlocking {
        val content = HttpTestServer.fakeBytes(300 * 1024)
        val temp = tempDir()
        HttpTestServer(content).use { server ->
            val downloader = HttpPackDownloader(client)
            val partFile = File(temp, "part.zip") // deliberadamente NÃO criado
            // (o caso "Limpar arquivos temporários" seguido de retomada)

            val result = downloader.downloadAppend(
                url = server.url,
                partFile = partFile,
                startOffset = 30_000L
            ) { }

            assertEquals(content.size.toLong(), result.finalOffset)
            assertTrue(!result.restartedFromZero)
            assertContentEquals(content, partFile.readBytes())
        }
    }
}

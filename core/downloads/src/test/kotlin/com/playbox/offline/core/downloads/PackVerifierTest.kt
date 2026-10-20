package com.playbox.offline.core.downloads

import com.playbox.offline.core.gameapi.ManifestFile
import com.playbox.offline.core.gameapi.PackManifest
import com.playbox.offline.core.gameapi.RemotePack
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Integridade de pacotes: SHA-256 do zip, descompactação segura e
 * conferência completa contra o `manifest.json`.
 */
class PackVerifierTest {

    private val temp: File = Files.createTempDirectory("playbox-verify").toFile()

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun createZip(
        files: Map<String, ByteArray>,
        gameId: String = "damas",
        contentVersion: Int = 1
    ): File {
        val manifest = PackManifest(
            gameId = gameId,
            contentVersion = contentVersion,
            files = files.map { (path, bytes) -> ManifestFile(path, bytes.size.toLong(), sha256(bytes)) }
        )
        val manifestBytes = Json.encodeToString(PackManifest.serializer(), manifest).encodeToByteArray()

        val zipFile = File(temp, "pack-${System.nanoTime()}.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifestBytes)
            zip.closeEntry()
            for ((path, bytes) in files) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return zipFile
    }

    private val files = mapOf(
        "regras/damas.txt" to "Regras do jogo: captura obrigatória.\n".encodeToByteArray(),
        "tabuleiro/cores.json" to "{\"claro\":\"#F5E9D0\"}".encodeToByteArray()
    )

    @Test
    fun `sha256 do arquivo confere com o calculado do conteudo`() {
        val bytes = "conteudo-estavel".encodeToByteArray()
        val file = File(temp, "arquivo.txt")
        file.writeBytes(bytes)
        assertEquals(sha256(bytes), PackVerifier.sha256Hex(file))
    }

    @Test
    fun `descompacta e valida pacote integro`() {
        val zip = createZip(files)
        val staging = File(temp, "staging-ok").apply { mkdirs() }

        val manifest = PackVerifier.unzipVerified(zip, staging, expectedGameId = "damas", expectedContentVersion = 1)

        assertEquals(2, manifest.files.size)
        assertTrue(File(staging, "regras/damas.txt").readBytes().contentEquals(files.getValue("regras/damas.txt")))
        assertTrue(File(staging, "tabuleiro/cores.json").isFile)
        assertTrue(File(staging, "manifest.json").isFile) // manifesto fica junto ao conteúdo
    }

    @Test
    fun `conteudo divergente do manifesto deve recusar`() {
        // adultera o PRIMEIRO byte mantendo o MESMO tamanho: a checagem de
        // tamanho passa e quem deve recusar é a verificação de SHA-256.
        val original = files.getValue("regras/damas.txt")
        val tampered = original.copyOf().also { it[0] = 'X'.code.toByte() }
        assertEquals(original.size, tampered.size)

        val zip = createZip(files)
        val badZip = File(temp, "pack-bad.zip")
        rewriteZip(zip, mapOf("regras/damas.txt" to tampered), extraFile = null, target = badZip)

        val staging = File(temp, "staging-bad").apply { mkdirs() }
        val error = assertFailsWith<PackIntegrityException> {
            PackVerifier.unzipVerified(badZip, staging, expectedGameId = "damas", expectedContentVersion = 1)
        }
        assertTrue(error.message!!.contains("SHA-256"))
    }

    @Test
    fun `arquivo extra fora do manifesto deve recusar`() {
        val zip = createZip(files)
        val badZip = File(temp, "pack-extra.zip")
        rewriteZip(zip, emptyMap(), extraFile = ("intruso.txt" to "não foi anunciado no manifesto".encodeToByteArray()), target = badZip)

        val staging = File(temp, "staging-extra").apply { mkdirs() }
        val error = assertFailsWith<PackIntegrityException> {
            PackVerifier.unzipVerified(badZip, staging, expectedGameId = "damas", expectedContentVersion = 1)
        }
        assertTrue(error.message!!.contains("manifesto"))
    }

    @Test
    fun `gameId divergente deve recusar`() {
        val zip = createZip(files, gameId = "xadrez")
        val staging = File(temp, "staging-id").apply { mkdirs() }
        val error = assertFailsWith<PackIntegrityException> {
            PackVerifier.unzipVerified(zip, staging, expectedGameId = "damas", expectedContentVersion = 1)
        }
        assertTrue(error.message!!.contains("xadrez"))
    }

    @Test
    fun `zip contra o catalogo remoto divergente deve recusar`() {
        val zip = createZip(files)
        val realSha = PackVerifier.sha256Hex(zip)
        val pack = RemotePack(
            gameId = "damas",
            contentVersion = 1,
            fileName = "damas-1.zip",
            packBytes = zip.length(),
            packSha256 = "ab".repeat(32)
        )
        assertFailsWith<PackIntegrityException> {
            PackVerifier.verifyZipAgainstRemote(zip, pack)
        }
        // com o hash correto passa
        PackVerifier.verifyZipAgainstRemote(zip, pack.copy(packSha256 = realSha))
    }

    // ------------------------------------------------------------------
    // helpers para montar zips "estragados" com determinismo
    // ------------------------------------------------------------------

    /**
     * Reescreve o zip mantendo o manifesto original, trocando o conteúdo
     * de arquivos [changedContents] e/ou adicionando [extraFile].
     */
    private fun rewriteZip(
        source: File,
        changedContents: Map<String, ByteArray>,
        extraFile: Pair<String, ByteArray>?,
        target: File
    ) {
        ZipOutputStream(FileOutputStream(target)).use { zipOut ->
            ZipInputStream(source.inputStream()).use { zipIn ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val entry = zipIn.nextEntry ?: break
                    zipOut.putNextEntry(ZipEntry(entry.name))
                    val replacement = changedContents[entry.name]
                    if (replacement != null) {
                        zipOut.write(replacement)
                    } else {
                        var n = zipIn.read(buffer)
                        while (n > 0) {
                            zipOut.write(buffer, 0, n)
                            n = zipIn.read(buffer)
                        }
                    }
                    zipOut.closeEntry()
                    zipIn.closeEntry()
                }
            }
            if (extraFile != null) {
                zipOut.putNextEntry(ZipEntry(extraFile.first))
                zipOut.write(extraFile.second)
                zipOut.closeEntry()
            }
        }
    }
}

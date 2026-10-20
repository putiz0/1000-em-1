package com.playbox.offline.core.downloads

import com.playbox.offline.core.gameapi.PackManifest
import com.playbox.offline.core.gameapi.RemotePack
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Falha de integridade (hash divergente, zip inválido, zip-slip etc.). */
class PackIntegrityException(message: String) : IOException(message)

/**
 * Verificação de integridade do pacote baixado.
 *
 * Formato do pacote (contrato da Fase 3):
 *  - zip contém `manifest.json` na RAIZ + os arquivos de conteúdo
 *    com caminho relativo direto (sem pasta envolvente);
 *  - `manifest.json` lista TODO arquivo que deve existir (path, bytes, sha256);
 *  - o SHA-256 do zip inteiro está no catálogo remoto.
 *
 * Pipeline do coordenador:
 *  1. hash do zip comparado ao catálogo remoto ([verifyZipAgainstRemote]);
 *  2. descompactação segura em staging ([unzipVerified]), com defesa
 *     contra zip-slip;
 *  3. conferência arquivo a arquivo contra o manifesto.
 */
object PackVerifier {

    private val manifestJson = Json { ignoreUnknownKeys = true }
    private const val MANIFEST_NAME = "manifest.json"

    /** SHA-256 de um arquivo, em hex minúsculo (leitura em blocos de 64 KB). */
    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Checa o hash do zip inteiro contra o catálogo remoto. */
    fun verifyZipAgainstRemote(zipFile: File, pack: RemotePack) {
        val sha = sha256Hex(zipFile)
        if (sha != pack.packSha256.lowercase()) {
            throw PackIntegrityException(
                "O arquivo baixado não corresponde ao SHA-256 oficial do pacote " +
                    "(download interrompido ou modificado). Rebaixe o jogo."
            )
        }
    }

    /**
     * Descompacta o zip em [stagingDir] e valida contra o manifesto interno.
     * Em caso de sucesso, staging contém exatamente os arquivos listados
     * + `manifest.json` (mantido junto ao conteúdo para reverificações).
     * Qualquer divergência lança [PackIntegrityException].
     */
    fun unzipVerified(
        zipFile: File,
        stagingDir: File,
        expectedGameId: String,
        expectedContentVersion: Int
    ): PackManifest {
        require(stagingDir.isDirectory) { "Diretório de staging inválido." }
        val stagingCanonical = stagingDir.canonicalPath + File.separator

        ZipFile(zipFile).use { zip ->
            val entries = zip.entries().asSequence().toList()

            val manifestEntry = entries.firstOrNull { it.name == MANIFEST_NAME }
                ?: throw PackIntegrityException("manifest.json ausente dentro do pacote.")

            val manifestText = try {
                zip.getInputStream(manifestEntry).bufferedReader(Charsets.UTF_8).readText()
            } catch (e: Exception) {
                throw PackIntegrityException("manifest.json não pôde ser lido (pacote corrompido).")
            }
            val manifest = try {
                manifestJson.decodeFromString<PackManifest>(manifestText)
            } catch (e: Exception) {
                throw PackIntegrityException("manifest.json em formato inválido (pacote corrompido).")
            }

            if (manifest.gameId != expectedGameId) {
                throw PackIntegrityException(
                    "O pacote recebido é de \"${manifest.gameId}\", esperado \"$expectedGameId\"."
                )
            }
            if (manifest.contentVersion != expectedContentVersion) {
                throw PackIntegrityException(
                    "O pacote recebido é a versão ${manifest.contentVersion}, esperada $expectedContentVersion."
                )
            }

            val listed = manifest.files.associateBy { it.path }
            val extracted = mutableSetOf<String>()

            for (entry in entries) {
                if (entry.isDirectory) continue
                if (entry.name == MANIFEST_NAME) continue

                val relativePath = entry.name
                if (listed.containsKey(relativePath).not()) {
                    throw PackIntegrityException("Arquivo que não está no manifesto: $relativePath")
                }
                if (!extracted.add(relativePath)) {
                    throw PackIntegrityException("Duplicidade no pacote: $relativePath")
                }

                val target = File(stagingDir, relativePath)
                val targetCanonical = target.canonicalPath
                if (!targetCanonical.startsWith(stagingCanonical)) {
                    // defesa contra zip-slip (arquivos invadindo diretórios acima do staging)
                    throw PackIntegrityException("Caminho suspeito no pacote: ${entry.name}")
                }
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }

            // comparação completa: nada está a menos, nada está a mais
            for ((path, descriptor) in listed) {
                val file = File(stagingDir, path)
                if (!file.isFile) throw PackIntegrityException("Arquivo do manifesto ausente no pacote: $path")
                if (file.length() != descriptor.bytes) {
                    throw PackIntegrityException("Tamanho divergente do manifesto: $path")
                }
                if (sha256Hex(file) != descriptor.sha256.lowercase()) {
                    throw PackIntegrityException("Integridade SHA-256 divergente no arquivo: $path")
                }
            }

            // o manifesto acompanha o conteúdo instalado
            File(stagingDir, MANIFEST_NAME).outputStream().use { output ->
                zip.getInputStream(manifestEntry).copyTo(output)
            }
            return manifest
        }
    }
}

package com.playbox.offline.core.packs

import com.playbox.offline.core.gameapi.PackManifest
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Autoridade do sistema de arquivos para pacotes instalados.
 *
 * Layout acordado (Fase 3):
 *
 *   filesDir/
 *     download-temp/            (,.part + zips em verificação; apagável)
 *     packs/{gameId}/{versão}/  (conteúdo instalado, atômico)
 *       manifest.json  + arquivos de conteúdo
 *
 * O módulo é "burro": só sabe ler/escrever no layout. Toda a lógica de
 * máquina de estados de download mora em :core:downloads.
 */
class PackManager(baseDir: File) {

    val packsRoot: File = File(baseDir, "packs")
    val tempRoot: File = File(baseDir, "download-temp")

    fun installedPackDir(gameId: String, contentVersion: Int): File =
        File(packsRoot, "$gameId/$contentVersion")

    /** Diretórios de versões instaladas de um jogo, mais recente primeiro. */
    fun installedVersions(gameId: String): List<Int> =
        File(packsRoot, gameId)
            .takeIf { it.isDirectory }
            ?.listFiles()
            ?.filter { it.isDirectory && it.name.toIntOrNull() != null }
            ?.map { it.name.toInt() }
            ?.sortedDescending()
            ?: emptyList()

    /** Arquivo parcial do download (neste módulo: download-temp/{gameId}-{v}.zip). */
    fun partFile(gameId: String, contentVersion: Int): File =
        File(tempRoot, "$gameId-$contentVersion.zip")

    /** Diretório da versão mais recente instalada de um jogo (ou null). */
    fun latestInstalledDir(gameId: String): File? =
        installedVersions(gameId).firstOrNull()?.let { installedPackDir(gameId, it) }

    /** Lê um arquivo de texto (UTF-8) do pacote mais recente instalado, ou null. */
    fun readTextFromLatestPack(gameId: String, fileName: String): String? =
        latestInstalledDir(gameId)?.let { dir ->
            File(dir, fileName).takeIf { it.isFile }?.readText(Charsets.UTF_8)
        }

    /** Lê e valida o manifest.json do pacote instalado. */
    fun readManifest(packDir: File): PackManifest {
        val file = File(packDir, MANIFEST_FILE)
        require(file.isFile) { "manifest.json ausente em ${packDir.path}" }
        val manifest = json.decodeFromString<PackManifest>(file.readText(Charsets.UTF_8))
        require(manifest.gameId == packDir.parentFile?.name) {
            "manifesto de \"${manifest.gameId}\" está na pasta de \"${packDir.parentFile?.name}\""
        }
        require(manifest.contentVersion.toString() == packDir.name) {
            "manifesto declara versão ${manifest.contentVersion} para a pasta ${packDir.name}"
        }
        return manifest
    }

    /** Tamanho ocupado por um diretório (bytes somados dos arquivos). */
    fun dirBytes(dir: File): Long = dir.takeIf { it.exists() }?.let { folder ->
        folder.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    } ?: 0L

    /** Apaga recursivamente sem lançar erro se não existir; retorna true se apagou algo. */
    fun deleteRecursively(file: File): Boolean =
        if (file.exists()) file.deleteRecursively() else false

    /** Garante que os diretórios base existam (chamado no arranque). */
    fun ensureBaseDirs() {
        packsRoot.mkdirs()
        tempRoot.mkdirs()
    }

    companion object {
        const val MANIFEST_FILE = "manifest.json"

        /** JSON tolerante: packs gerados por ferramentas podem evoluir. */
        val json = Json { ignoreUnknownKeys = true }
    }
}

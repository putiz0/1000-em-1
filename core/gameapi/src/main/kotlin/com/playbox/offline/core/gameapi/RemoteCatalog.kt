package com.playbox.offline.core.gameapi

import kotlinx.serialization.Serializable

/**
 * Catálogo remoto de pacotes disponíveis para download, publicado junto
 * aos packs (no servidor de desenvolvimento e, no futuro, no GitHub
 * Releases). É fetchado como `remote-catalog.json` na raiz do servidor.
 *
 * Enquanto o catálogo embutido (catalog.json no APK) descreve jogos e
 * textos, o catálogo REMOTO diz onde os dados do pacote estão hoje
 * (URL real, tamanho real e SHA-256 do zip).
 */
@Serializable
data class RemoteCatalog(
    val schemaVersion: Int = 1,
    /**
     * URL base dos packs declarada pelo próprio servidor (permite mover
     * os arquivos de hosting — local -> GitHub — sem reescrever nada).
     */
    val baseUrl: String = "",
    val packs: List<RemotePack> = emptyList()
) {
    fun pack(gameId: String): RemotePack? = packs.firstOrNull { it.gameId == gameId }
}

@Serializable
data class RemotePack(
    val gameId: String,
    val contentVersion: Int,
    /** Nome do arquivo do zip na base (ex.: "packs/damas-1.zip"). */
    val fileName: String,
    /** Tamanho real do zip em bytes (autoridade para progresso/espaço). */
    val packBytes: Long,
    /** SHA-256 em hex do zip completo (integridade do download). */
    val packSha256: String
)

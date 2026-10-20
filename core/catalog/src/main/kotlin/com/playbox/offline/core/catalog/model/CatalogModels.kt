package com.playbox.offline.core.catalog.model

import kotlinx.serialization.Serializable

/**
 * Catálogo embutido no aplicativo (assets/catalog/catalog.json).
 * Na Fase 3 um catálogo remoto será mesclado a estes dados; a estrutura
 * foi desenhada para aceitar ambos sem migrações.
 */

@Serializable
data class CatalogData(
    val schemaVersion: Int = 1,
    val categories: List<CategoryInfo> = emptyList(),
    val games: List<GameCatalogEntry> = emptyList()
)

@Serializable
data class CategoryInfo(
    /** Identificador estável da categoria (ex.: "tabuleiro"). */
    val id: String,
    /** Nome exibido em português (ex.: "Jogos de Tabuleiro"). */
    val name: String,
    /** Símbolo visual (emoji) usado enquanto não há arte própria. */
    val glyph: String = ""
)

@Serializable
data class GameCatalogEntry(
    /** Identificador único do jogo (ex.: "damas"). */
    val id: String,
    val name: String,
    /** id da categoria à qual o jogo pertence. */
    val categoryId: String,
    /** Descrição curta (cartões). */
    val shortDescription: String,
    /** Descrição longa (tela de detalhes). Parágrafos separados por \n. */
    val longDescription: String = "",
    /** Símbolo visual (emoji) usado na capa enquanto não há arte baixada. */
    val coverGlyph: String = "",
    /** Cores hexadecimais ("#RRGGBB") do gradiente da capa. */
    val coverColors: List<String> = emptyList(),
    /** Tamanho estimado do pacote de download, em bytes. */
    val estimatedBytes: Long = 0,
    /**
     * false = conteúdo ainda não implementado ("Em breve").
     * Nenhum jogo fictício é apresentado como instalável.
     */
    val available: Boolean = false,
    /** Versão do conteúdo publicado no catálogo remoto (0 = nenhuma). */
    val contentVersion: Int = 0,
    /** Posição de popularidade (1 = mais popular). */
    val popularity: Int = Int.MAX_VALUE,
    /** Ordem de adição ao catálogo (maior = adicionado por último). */
    val addedOrder: Int = 0,

    /** Portas para download na Fase 3 (não usadas nesta etapa). */
    val downloadUrl: String? = null,
    val sha256: String? = null
)

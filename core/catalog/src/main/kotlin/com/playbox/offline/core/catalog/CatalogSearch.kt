package com.playbox.offline.core.catalog

import java.text.Normalizer
import java.util.Locale

/**
 * Busca por nome + filtros por disponibilidade/instalação.
 * Puro Kotlin (JVM) — coberto por testes de unidade.
 */
object CatalogSearch {

    /** Filtros de disponibilidade exibidos na pesquisa. */
    enum class Scope { ALL, AVAILABLE, INSTALLED }

    /** Remove acentos/múltiplos espaços e normaliza caixa para comparação. */
    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
        val noAccents = REGEX_ACCENTS.replace(decomposed, "")
        return noAccents.replace(WHITESPACE, " ").lowercase(Locale.ROOT)
    }

    fun matchesName(name: String, query: String): Boolean {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isEmpty()) return true
        return normalize(name).contains(normalizedQuery)
    }

    fun filter(
        games: List<GameWithStatus>,
        query: String,
        scope: Scope = Scope.ALL
    ): List<GameWithStatus> = games
        .filter { matchesName(it.entry.name, query) }
        .filter { game ->
            when (scope) {
                Scope.ALL -> true
                Scope.AVAILABLE -> game.available
                Scope.INSTALLED -> game.isInstalled
            }
        }

    private val REGEX_ACCENTS = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val WHITESPACE = Regex("\\s+")
}

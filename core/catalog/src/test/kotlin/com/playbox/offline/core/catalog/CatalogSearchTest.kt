package com.playbox.offline.core.catalog

import com.playbox.offline.core.catalog.model.GameCatalogEntry
import com.playbox.offline.core.database.InstallStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogSearchTest {

    private fun game(
        id: String,
        name: String,
        available: Boolean = true,
        status: InstallStatus? = null
    ) = GameWithStatus(
        entry = GameCatalogEntry(
            id = id,
            name = name,
            categoryId = "tabuleiro",
            shortDescription = "",
            available = available
        ),
        status = status
    )

    private val games = listOf(
        game("xadrez", "Xadrez"),
        game("damas", "Damas"),
        game("velha", "Jogo da Velha", available = false),
        game("sudoku", "Sudoku", status = InstallStatus.COMPLETED)
    )

    @Test
    fun `normalize remove acentos e caixa`() {
        assertEquals("xadrez", CatalogSearch.normalize("  Xadréz  "))
        assertEquals("jogo da velha", CatalogSearch.normalize("Jogo   da VÉLHA"))
    }

    @Test
    fun `busca localiza por nome ignorando acento e caixa`() {
        assertTrue(CatalogSearch.matchesName("Xadrez", "xadrez"))
        assertTrue(CatalogSearch.matchesName("Jogo da Velha", "velhA"))
        assertFalse(CatalogSearch.matchesName("Damas", "xad"))
    }

    @Test
    fun `escopo AVAILABLE descarta jogos em breve`() {
        val result = CatalogSearch.filter(games, query = "", scope = CatalogSearch.Scope.AVAILABLE)
        // "Disponível" = existe no catálogo como instalável (mesmo já instalado, pode ser atualizado)
        assertEquals(listOf("xadrez", "damas", "sudoku"), result.map { it.entry.id })
    }

    @Test
    fun `escopo INSTALLED aceita apenas concluídos ou com atualização`() {
        val result = CatalogSearch.filter(games, query = "", scope = CatalogSearch.Scope.INSTALLED)
        assertEquals(listOf("sudoku"), result.map { it.entry.id })
    }

    @Test
    fun `query vazia no escopo ALL traz todos com ordem preservada`() {
        val result = CatalogSearch.filter(games, query = "", scope = CatalogSearch.Scope.ALL)
        assertEquals(listOf("xadrez", "damas", "velha", "sudoku"), result.map { it.entry.id })
    }
}

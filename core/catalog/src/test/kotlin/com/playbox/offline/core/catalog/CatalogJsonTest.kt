package com.playbox.offline.core.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogJsonTest {

    private val sample = """
        {
          "schemaVersion": 1,
          "futureFieldTopLevel": 1,
          "categories": [
            { "id": "tabuleiro", "name": "Jogos de Tabuleiro", "glyph": "♟️" },
            { "id": "casuais", "name": "Jogos Casuais", "glyph": "🎮" }
          ],
          "games": [
            {
              "id": "damas",
              "name": "Damas",
              "categoryId": "tabuleiro",
              "shortDescription": "Damas clássicas 8×8.",
              "estimatedBytes": 1800000,
              "available": true,
              "popularity": 1,
              "addedOrder": 10,
              "coverGlyph": "⚫",
              "coverColors": ["#F59E0B", "#B45309"]
            },
            {
              "id": "jogo-da-velha",
              "name": "Jogo da Velha",
              "categoryId": "tabuleiro",
              "shortDescription": "Velha 3×3.",
              "available": false,
              "popularity": 3,
              "addedOrder": 6
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parse lê catálogo completo com valores padrão`() {
        val data = CatalogJson.parse(sample)

        assertEquals(1, data.schemaVersion)
        assertEquals(2, data.categories.size)
        assertEquals("tabuleiro", data.categories.first().id)
        assertEquals(2, data.games.size)

        val damas = data.games.first { it.id == "damas" }
        assertEquals("Damas", damas.name)
        assertEquals(1_800_000L, damas.estimatedBytes)
        assertEquals(true, damas.available)
        assertEquals(listOf("#F59E0B", "#B45309"), damas.coverColors)

        val velha = data.games.first { it.id == "jogo-da-velha" }
        assertEquals(false, velha.available)
        assertEquals(0L, velha.estimatedBytes)
    }

    @Test
    fun `campos desconhecidos são ignorados`() {
        val data = CatalogJson.parse(sample)
        // futureFieldTopLevel não existe no modelo e não pode quebrar o parse.
        assertEquals("Jogos de Tabuleiro", data.categories.first().name)
    }

    @Test
    fun `catalogo monta o snapshot com estados ausentes`() {
        val snapshot = CatalogSnapshot.of(CatalogJson.parse(sample), installs = emptyList())

        assertEquals(2, snapshot.games.size)
        assertTrue(snapshot.games.none { it.isInstalled })
    }
}

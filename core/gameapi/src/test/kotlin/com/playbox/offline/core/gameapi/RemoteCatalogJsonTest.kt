package com.playbox.offline.core.gameapi

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Testes de parse do catálogo remoto e do manifesto de pack.
 * Formatos acordados no plano da Fase 3 (Fase 3 do cronograma).
 */
class RemoteCatalogJsonTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parse de remote-catalog completo`() {
        val text = """
            {
              "schemaVersion": 1,
              "baseUrl": "http://localhost:9080/files/",
              "packs": [
                {
                  "gameId": "damas",
                  "contentVersion": 1,
                  "fileName": "packs/damas-1.zip",
                  "packBytes": 123456,
                  "packSha256": "abc123"
                },
                {
                  "gameId": "xadrez",
                  "contentVersion": 2,
                  "fileName": "packs/xadrez-2.zip",
                  "packBytes": 999,
                  "packSha256": "def456"
                }
              ]
            }
        """.trimIndent()

        val catalog = json.decodeFromString<RemoteCatalog>(text)

        assertEquals(1, catalog.schemaVersion)
        assertEquals("http://localhost:9080/files/", catalog.baseUrl)
        assertEquals(2, catalog.packs.size)
        assertEquals("damas-1.zip".let { "packs/$it" }, catalog.pack("damas")?.fileName)
        assertEquals(1, catalog.pack("damas")?.contentVersion)
        assertEquals(123456, catalog.pack("damas")?.packBytes)
        assertEquals("def456", catalog.pack("xadrez")?.packSha256)
        assertNull(catalog.pack("domino"))
    }

    @Test
    fun `parse tolera campos novos sem quebrar`() {
        val text = """
            {
              "schemaVersion": 1,
              "baseUrl": "",
              "packs": [],
              "campoFuturo": 42
            }
        """.trimIndent()

        val catalog = json.decodeFromString<RemoteCatalog>(text)

        assertEquals(0, catalog.packs.size)
    }

    @Test
    fun `pack sem gameId procurado nao existe`() {
        val catalog = RemoteCatalog(
            baseUrl = "http://x/",
            packs = listOf(
                RemotePack(
                    gameId = "damas",
                    contentVersion = 1,
                    fileName = "damas-1.zip",
                    packBytes = 10,
                    packSha256 = "hhh"
                )
            )
        )

        assertNull(catalog.pack("nao-existe"))
    }
}

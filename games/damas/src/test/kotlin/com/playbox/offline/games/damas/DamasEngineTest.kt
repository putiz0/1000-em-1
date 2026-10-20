package com.playbox.offline.games.damas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DamasEngineTest {

    private fun estadoDe(
        pecas: List<Pair<Casa, Peca>>,
        vez: CorPeca = CorPeca.CLARA,
        semCaptura: Int = 0,
    ): EstadoDamas {
        val casas: List<MutableList<Peca?>> =
            List(8) { MutableList(8) { null } }
        pecas.forEach { (casa, peca) -> casas[casa.linha][casa.coluna] = peca }
        return EstadoDamas(casas, vez, semCaptura)
    }

    @Test
    fun `posição inicial tem 12 peças de cada cor e vez das claras`() {
        val estado = DamasRegras.inicial()
        assertEquals(12, estado.pecasClaras)
        assertEquals(12, estado.pecasEscuras)
        assertEquals(CorPeca.CLARA, estado.vez)
        assertTrue(estado.casas[0][1] != null) // casa escura da primeira linha
        assertTrue(estado.casas[0][0] == null)
    }

    @Test
    fun `peão avança em diagonal para frente`() {
        val estado = estadoDe(listOf(Casa(5, 0) to Peca(CorPeca.CLARA)))
        val jogadas = DamasRegras.jogadasLegais(estado)
        assertTrue(jogadas.any { it.origem == Casa(5, 0) && it.destino == Casa(4, 1) })
        assertEquals(1, jogadas.size) // (4,-1) está fora do tabuleiro
    }

    @Test
    fun `captura é obrigatória`() {
        val estado = estadoDe(
            listOf(
                Casa(4, 1) to Peca(CorPeca.CLARA),
                Casa(3, 2) to Peca(CorPeca.ESCURA),
            )
        )
        val jogadas = DamasRegras.jogadasLegais(estado)
        assertEquals(
            listOf(Jogada(Casa(4, 1), Casa(2, 3), listOf(Casa(3, 2)))),
            jogadas
        )
    }

    @Test
    fun `peão captura para trás`() {
        val estado = estadoDe(
            listOf(
                Casa(4, 1) to Peca(CorPeca.CLARA),
                Casa(5, 2) to Peca(CorPeca.ESCURA),
            )
        )
        val jogadas = DamasRegras.jogadasLegais(estado)
        assertTrue(jogadas.any {
            it.origem == Casa(4, 1) && it.destino == Casa(6, 3) &&
                it.capturas == listOf(Casa(5, 2))
        })
    }

    @Test
    fun `maioria obriga a cadeia mais longa`() {
        val estado = estadoDe(
            listOf(
                Casa(4, 1) to Peca(CorPeca.CLARA),
                Casa(4, 5) to Peca(CorPeca.CLARA),
                Casa(3, 2) to Peca(CorPeca.ESCURA),
                Casa(1, 4) to Peca(CorPeca.ESCURA),
                Casa(3, 6) to Peca(CorPeca.ESCURA),
            )
        )
        val jogadas = DamasRegras.jogadasLegais(estado)
        // Somente a cadeia (4,1)→(2,3)→(0,5) captura 2 peças.
        assertEquals(1, jogadas.size)
        assertEquals(Casa(0, 5), jogadas[0].destino)
        assertEquals(2, jogadas[0].capturas.size)
        assertTrue(Casa(3, 2) in jogadas[0].capturas)
        assertTrue(Casa(1, 4) in jogadas[0].capturas)
    }

    @Test
    fun `dama voa e captura à distância`() {
        val estado = estadoDe(
            listOf(
                Casa(7, 0) to Peca(CorPeca.CLARA, dama = true),
                Casa(4, 3) to Peca(CorPeca.ESCURA),
            )
        )
        val jogadas = DamasRegras.jogadasLegais(estado)
        assertTrue(jogadas.isNotEmpty())
        val destinos = jogadas.map { it.destino }.toSet()
        assertEquals(
            setOf(Casa(3, 4), Casa(2, 5), Casa(1, 6), Casa(0, 7)),
            destinos
        )
        assertTrue(jogadas.all { it.capturas == listOf(Casa(4, 3)) })
    }

    @Test
    fun `peão promove ao terminar a cadeia na última linha`() {
        val estado = estadoDe(
            listOf(
                Casa(1, 0) to Peca(CorPeca.CLARA),
            )
        )
        val destino = Casa(0, 1)
        assertTrue(DamasRegras.jogadasLegais(estado).any { it.destino == destino })
        val depois = DamasRegras.aplicar(estado, Jogada(Casa(1, 0), destino))
        assertEquals(Peca(CorPeca.CLARA, dama = true), depois.casas[0][1])
        assertEquals(CorPeca.ESCURA, depois.vez)
    }

    @Test
    fun `bloqueio total dá vitória do adversário`() {
        val estado = estadoDe(
            listOf(
                // Escuras: peões sem lance legal.
                Casa(0, 1) to Peca(CorPeca.ESCURA),
                Casa(2, 3) to Peca(CorPeca.ESCURA),
                // Claras bloqueiam avanços e pousos das escuras.
                Casa(1, 0) to Peca(CorPeca.CLARA),
                Casa(1, 2) to Peca(CorPeca.CLARA),
                Casa(3, 2) to Peca(CorPeca.CLARA),
                Casa(3, 4) to Peca(CorPeca.CLARA),
                Casa(4, 1) to Peca(CorPeca.CLARA),
                Casa(4, 5) to Peca(CorPeca.CLARA),
            ),
            vez = CorPeca.ESCURA
        )
        assertEquals(ResultadoDamas.VITORIA_CLARAS, DamasRegras.resultado(estado))
        assertTrue(DamasRegras.jogadasLegais(estado).isEmpty())
    }

    @Test
    fun `empate por limite de lances sem captura`() {
        val estado = estadoDe(
            listOf(
                Casa(7, 0) to Peca(CorPeca.CLARA, dama = true),
                Casa(0, 7) to Peca(CorPeca.ESCURA, dama = true),
            ),
            semCaptura = 60
        )
        assertEquals(ResultadoDamas.EMPATE, DamasRegras.resultado(estado))
    }

    @Test
    fun `aplicar captura remove as peças e passa a vez`() {
        val estado = estadoDe(
            listOf(
                Casa(4, 1) to Peca(CorPeca.CLARA),
                Casa(3, 2) to Peca(CorPeca.ESCURA),
            )
        )
        val depois = DamasRegras.aplicar(estado, Jogada(Casa(4, 1), Casa(2, 3), listOf(Casa(3, 2))))
        assertEquals(null, depois.casas[3][2])
        assertEquals(Peca(CorPeca.CLARA), depois.casas[2][3])
        assertEquals(null, depois.casas[4][1])
        assertEquals(CorPeca.ESCURA, depois.vez)
        assertEquals(0, depois.pecasEscuras)
    }
}

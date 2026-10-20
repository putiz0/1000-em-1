package com.playbox.offline.games.xadrez

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XadrezEngineTest {

    private fun estadoDe(
        pecas: List<Pair<Pos, PecaXadrez>>,
        vez: CorXadrez = CorXadrez.BRANCAS,
        roques: Set<RoqueDireito> = emptySet(),
        alvoEnPassant: Pos? = null,
        relogio: Int = 0,
    ): EstadoXadrez {
        val casas: List<MutableList<PecaXadrez?>> =
            List(8) { MutableList(8) { null } }
        pecas.forEach { (pos, peca) -> casas[pos.linha][pos.coluna] = peca }
        return EstadoXadrez(casas, vez, roques, alvoEnPassant, relogio, 1)
    }

    private fun perft(estado: EstadoXadrez, profundidade: Int): Int =
        if (profundidade == 0) 1 else XadrezRegras.jogadasLegais(estado).sumOf {
            perft(XadrezRegras.aplicar(estado, it), profundidade - 1)
        }

    @Test
    fun `perft da posição inicial bate com as tabelas padrão`() {
        val inicial = XadrezRegras.inicial()
        assertEquals(20, perft(inicial, 1))
        assertEquals(400, perft(inicial, 2))
        assertEquals(8902, perft(inicial, 3))
    }

    @Test
    fun `roque pequeno e grande são legais e aplicados`() {
        val estado = estadoDe(
            listOf(
                Pos(7, 4) to PecaXadrez(CorXadrez.BRANCAS, TipoPeca.REI),
                Pos(7, 7) to PecaXadrez(CorXadrez.BRANCAS, TipoPeca.TORRE),
                Pos(7, 0) to PecaXadrez(CorXadrez.BRANCAS, TipoPeca.TORRE),
                Pos(0, 4) to PecaXadrez(CorXadrez.NEGRAS, TipoPeca.REI),
            ),
            roques = setOf(
                RoqueDireito.BRANCO_REI, RoqueDireito.BRANCO_DAMA,
                RoqueDireito.PRETO_REI, RoqueDireito.PRETO_DAMA,
            )
        )
        val lances = XadrezRegras.jogadasLegais(estado)
        assertTrue(lances.any { it.roque == RoqueDireito.BRANCO_REI })
        assertTrue(lances.any { it.roque == RoqueDireito.BRANCO_DAMA })
        val depois = XadrezRegras.aplicar(
            estado,
            lances.first { it.roque == RoqueDireito.BRANCO_REI }
        )
        assertEquals(PecaXadrez(CorXadrez.BRANCAS, TipoPeca.REI), depois.casas[7][6])
        assertEquals(PecaXadrez(CorXadrez.BRANCAS, TipoPeca.TORRE), depois.casas[7][5])
        assertFalse(depois.roques.contains(RoqueDireito.BRANCO_REI))
        assertFalse(depois.roques.contains(RoqueDireito.BRANCO_DAMA))
    }

    @Test
    fun `roque é barrado se a casa atravessada estiver atacada`() {
        val estado = estadoDe(
            listOf(
                Pos(7, 4) to PecaXadrez(CorXadrez.BRANCAS, TipoPeca.REI),
                Pos(7, 7) to PecaXadrez(CorXadrez.BRANCAS, TipoPeca.TORRE),
                Pos(7, 0) to PecaXadrez(CorXadrez.BRANCAS, TipoPeca.TORRE),
                Pos(0, 4) to PecaXadrez(CorXadrez.NEGRAS, TipoPeca.REI),
                Pos(0, 5) to PecaXadrez(CorXadrez.NEGRAS, TipoPeca.TORRE), // f8 ataca f1
            ),
            roques = setOf(RoqueDireito.BRANCO_REI, RoqueDireito.BRANCO_DAMA)
        )
        val lances = XadrezRegras.jogadasLegais(estado)
        assertTrue(lances.none { it.roque == RoqueDireito.BRANCO_REI })
        assertTrue(lances.any { it.roque == RoqueDireito.BRANCO_DAMA })
    }

    @Test
    fun `en passant captura o peão que passou`() {
        val estado = estadoDe(
            listOf(
                Pos(3, 4) to PecaXadrez(CorXadrez.BRANCAS, TipoPeca.PEAO),
                Pos(3, 3) to PecaXadrez(CorXadrez.NEGRAS, TipoPeca.PEAO),
                Pos(7, 4) to PecaXadrez(CorXadrez.BRANCAS, TipoPeca.REI),
                Pos(0, 3) to PecaXadrez(CorXadrez.NEGRAS, TipoPeca.REI),
            ),
            alvoEnPassant = Pos(2, 3)
        )
        val lance = XadrezRegras.jogadasLegais(estado).first {
            it.enPassant && it.destino == Pos(2, 3)
        }
        assertEquals(Pos(3, 4), lance.origem)
        val depois = XadrezRegras.aplicar(estado, lance)
        assertEquals(null, depois.casas[3][3], "peão negro em d5 deveria ter saído")
        assertEquals(PecaXadrez(CorXadrez.BRANCAS, TipoPeca.PEAO), depois.casas[2][3])
    }

    @Test
    fun `promoção gera as quatro peças e aplica a dama`() {
        val estado = estadoDe(
            listOf(
                Pos(1, 0) to PecaXadrez(CorXadrez.BRANCAS, TipoPeca.PEAO),
                Pos(7, 4) to PecaXadrez(CorXadrez.BRANCAS, TipoPeca.REI),
                Pos(0, 4) to PecaXadrez(CorXadrez.NEGRAS, TipoPeca.REI),
            )
        )
        val promocoes = XadrezRegras.jogadasLegais(estado).filter {
            it.origem == Pos(1, 0) && it.destino == Pos(0, 0)
        }
        assertEquals(
            setOf(TipoPeca.DAMA, TipoPeca.TORRE, TipoPeca.BISPO, TipoPeca.CAVALO),
            promocoes.mapNotNull { it.promover }.toSet()
        )
        val depois = XadrezRegras.aplicar(
            estado,
            promocoes.first { it.promover == TipoPeca.DAMA }
        )
        assertEquals(PecaXadrez(CorXadrez.BRANCAS, TipoPeca.DAMA), depois.casas[0][0])
    }

    @Test
    fun `mate do tolo dá vitória das negras`() {
        var estado = XadrezRegras.inicial()
        val sequencia = listOf(
            LanceXadrez(Pos(6, 5), Pos(5, 5)), // Brancas: f2-f3
            LanceXadrez(Pos(1, 4), Pos(3, 4)), // Negras: e7-e5
            LanceXadrez(Pos(6, 6), Pos(4, 6)), // Brancas: g2-g4
            LanceXadrez(Pos(0, 3), Pos(4, 7)), // Negras: Qd8-h4#
        )
        for (lance in sequencia) {
            estado = XadrezRegras.aplicar(estado, lance)
        }
        assertEquals(ResultadoXadrez.VITORIA_NEGRAS, XadrezRegras.resultado(estado))
        assertTrue(XadrezRegras.inCheck(estado, CorXadrez.BRANCAS))
    }

    @Test
    fun `posição sem lances e sem xeque é pat`() {
        val estado = estadoDe(
            listOf(
                Pos(0, 7) to PecaXadrez(CorXadrez.BRANCAS, TipoPeca.REI), // h8
                Pos(1, 5) to PecaXadrez(CorXadrez.NEGRAS, TipoPeca.REI),  // f7
                Pos(2, 6) to PecaXadrez(CorXadrez.NEGRAS, TipoPeca.DAMA), // g6
            )
        )
        assertEquals(ResultadoXadrez.EMPATE_PAT, XadrezRegras.resultado(estado))
    }

    @Test
    fun `regra dos 50 lances empata`() {
        val estado = estadoDe(
            listOf(
                Pos(7, 4) to PecaXadrez(CorXadrez.BRANCAS, TipoPeca.REI),
                Pos(7, 7) to PecaXadrez(CorXadrez.BRANCAS, TipoPeca.TORRE),
                Pos(0, 4) to PecaXadrez(CorXadrez.NEGRAS, TipoPeca.REI),
                Pos(0, 0) to PecaXadrez(CorXadrez.NEGRAS, TipoPeca.TORRE),
            ),
            relogio = 100
        )
        assertEquals(ResultadoXadrez.EMPATE_50_LANCES, XadrezRegras.resultado(estado))
    }

    @Test
    fun `apenas reis é empate por material insuficiente`() {
        val estado = estadoDe(
            listOf(
                Pos(7, 4) to PecaXadrez(CorXadrez.BRANCAS, TipoPeca.REI),
                Pos(0, 4) to PecaXadrez(CorXadrez.NEGRAS, TipoPeca.REI),
            )
        )
        assertEquals(ResultadoXadrez.EMPATE_MATERIAL, XadrezRegras.resultado(estado))
    }

    @Test
    fun `peça pregada não pode sair da linha de xaque`() {
        // Rei branco em d1, cavalo branco em d2, torre negra em d8:
        // o cavalo está pregado e nenhum salto é legal.
        val estado = estadoDe(
            listOf(
                Pos(7, 3) to PecaXadrez(CorXadrez.BRANCAS, TipoPeca.REI),
                Pos(6, 3) to PecaXadrez(CorXadrez.BRANCAS, TipoPeca.CAVALO),
                Pos(0, 3) to PecaXadrez(CorXadrez.NEGRAS, TipoPeca.TORRE),
                Pos(0, 7) to PecaXadrez(CorXadrez.NEGRAS, TipoPeca.REI),
            )
        )
        val lancesDoCavalo = XadrezRegras.jogadasLegais(estado)
            .filter { it.origem == Pos(6, 3) }
        assertEquals(0, lancesDoCavalo.size, "cavalo pregado não pode se mover")
        // O rei ainda pode sair da linha de xeque pelo lado.
        val lancesDoRei = XadrezRegras.jogadasLegais(estado)
            .filter { it.origem == Pos(7, 3) }
            .map { it.destino }
        assertEquals(4, lancesDoRei.size)
        assertTrue(lancesDoRei.contains(Pos(6, 2)))
        assertTrue(lancesDoRei.contains(Pos(6, 4)))
    }
}

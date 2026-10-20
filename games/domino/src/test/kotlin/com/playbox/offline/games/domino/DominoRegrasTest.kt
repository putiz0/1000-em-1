package com.playbox.offline.games.domino

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DominoRegrasTest {

    private fun estadoDe(
        maos: List<List<PedraDomino>>,
        monte: List<PedraDomino> = emptyList(),
        mesa: List<PedraDomino> = emptyList(),
        vez: Int = 0,
        passesSeguidos: Int = 0,
        ultimaFoiCarroca: Boolean = false,
        numeroRodada: Int = 1,
    ): EstadoDomino = EstadoDomino(
        jogadores = maos.mapIndexed { i, mao ->
            JogadorDomino("Jogador ${i + 1}", mao.sortedBy { it.soma })
        },
        monte = monte,
        mesa = mesa,
        vez = vez,
        passesSeguidos = passesSeguidos,
        ultimaFoiCarroca = ultimaFoiCarroca,
        numeroRodada = numeroRodada,
    )

    @Test
    fun `novo jogo distribui 7 pedras por jogador em baralho completo`() {
        for (n in 2..4) {
            val estado = DominoRegras.novoJogo((1..n).map { "J$it" })
            estado.jogadores.forEach { assertEquals(7, it.mao.size) }
            assertEquals(28 - 7 * n, estado.monte.size)
            val todas = estado.jogadores.flatMap { it.mao } + estado.monte
            assertEquals(28, todas.size)
            assertEquals(28, todas.toSet().size, "pedras repetidas na distribuição")
        }
    }

    @Test
    fun `começa quem tem a maior carroça e pode jogar qualquer pedra`() {
        val estado = estadoDe(
            maos = listOf(
                listOf(PedraDomino(0, 0)),
                listOf(PedraDomino(6, 6)),
            )
        )
        assertEquals(1, DominoRegras.primeiroJogador(estado.jogadores))
        val possiveis = DominoRegras.jogadasPossiveis(estado.copy(vez = 1), 1)
        assertEquals(listOf(LadoMesa.DIREITA), possiveis[PedraDomino(6, 6)])
    }

    @Test
    fun `sem carroça começa quem tem a pedra de maior soma`() {
        val estado = estadoDe(
            maos = listOf(
                listOf(PedraDomino(3, 2), PedraDomino(0, 1)),
                listOf(PedraDomino(4, 4)), // carroça conta mais que soma 5
                listOf(PedraDomino(5, 4)),
            )
        )
        assertEquals(1, DominoRegras.primeiroJogador(estado.jogadores))
    }

    @Test
    fun `jogada exige o número da ponta`() {
        val estado = estadoDe(
            maos = listOf(
                listOf(PedraDomino(0, 2), PedraDomino(1, 3)),
                listOf(PedraDomino(4, 4)),
            ),
            mesa = listOf(PedraDomino(3, 1)), // pontas: 3 e 1
        )
        val possiveis = DominoRegras.jogadasPossiveis(estado, 0)
        assertFalse(possiveis.containsKey(PedraDomino(0, 2)))
        assertEquals(
            setOf(LadoMesa.ESQUERDA, LadoMesa.DIREITA),
            possiveis[PedraDomino(1, 3)]?.toSet(),
            "a pedra 1|3 casa com as duas pontas (3 e 1)"
        )
    }

    @Test
    fun `pedra que casa nos dois lados oferece os dois lados`() {
        val estado = estadoDe(
            maos = listOf(listOf(PedraDomino(4, 2))),
            mesa = listOf(PedraDomino(4, 4)),
        )
        val lados = DominoRegras.jogadasPossiveis(estado, 0)[PedraDomino(4, 2)]
        assertEquals(setOf(LadoMesa.ESQUERDA, LadoMesa.DIREITA), lados?.toSet())
    }

    @Test
    fun `jogar à esquerda preserva as pontas`() {
        var estado = estadoDe(
            maos = listOf(
                listOf(PedraDomino(2, 4), PedraDomino(3, 3)),
                listOf(PedraDomino(5, 5)),
            ),
            mesa = listOf(PedraDomino(4, 4)),
        )
        estado = DominoRegras.jogar(estado, 0, PedraDomino(2, 4), LadoMesa.ESQUERDA)
        assertEquals(listOf(PedraDomino(2, 4), PedraDomino(4, 4)), estado.mesa)
        assertEquals(2, estado.pontaEsquerda)
        assertEquals(4, estado.pontaDireita)
        assertEquals(1, estado.vez)
        assertEquals(1, estado.jogadores[0].mao.size, "restou a carroça 3|3 na mão")
    }

    @Test
    fun `compra até ter jogada possível e mantém a vez`() {
        val estado = estadoDe(
            maos = listOf(listOf(PedraDomino(0, 0))),
            monte = listOf(PedraDomino(0, 1), PedraDomino(1, 5)),
            mesa = listOf(PedraDomino(5, 3)),
        )
        val novo = DominoRegras.comprar(estado)
        assertEquals(0, novo.monte.size)
        assertEquals(
            listOf(PedraDomino(0, 0), PedraDomino(0, 1), PedraDomino(1, 5)),
            novo.jogadores[0].mao.sortedBy { it.soma }
        )
        assertEquals(0, novo.vez, "a vez continua com quem comprou")
    }

    @Test
    fun `toco de todos tranca e o menor ganha os pontos dos outros`() {
        val estado = estadoDe(
            maos = listOf(
                listOf(PedraDomino(0, 0), PedraDomino(6, 6)),
                listOf(PedraDomino(1, 1), PedraDomino(2, 2)),
            ),
            mesa = listOf(PedraDomino(5, 3)),
        )
        val depois = DominoRegras.passar(DominoRegras.passar(estado))
        val rodada = depois.rodadaFechada
        assertTrue(rodada != null, "duas passes seguidos com 2 jogadores fecham a rodada")
        assertEquals(1, rodada!!.ganhador, "Jogador 2 tem mão de menor soma (6 < 12)")
        assertEquals(12, rodada.pontosGanhos[1])
        assertEquals(12, depois.jogadores[1].pontos)
        assertEquals(0, depois.jogadores[0].pontos)
    }

    @Test
    fun `bater soma os pontos dos outros com bônus de carroça`() {
        val estado = estadoDe(
            maos = listOf(
                listOf(PedraDomino(3, 3)),
                listOf(PedraDomino(4, 4)),
                listOf(PedraDomino(5, 5)),
            ),
            mesa = listOf(PedraDomino(3, 2), PedraDomino(2, 1)), // pontas 3 e 1
        )
        val novo = DominoRegras.jogar(estado, 0, PedraDomino(3, 3), LadoMesa.ESQUERDA)
        val rodada = requireNotNull(novo.rodadaFechada)
        assertEquals(0, rodada.ganhador)
        assertEquals(8 + 10 + 1, rodada.pontosGanhos[0], "soma dos outros mais bônus da carroça")
        assertEquals(19, novo.jogadores[0].pontos)
        assertEquals(null, novo.campeao)
    }

    @Test
    fun `meta de pontos fecha o jogo com campeão`() {
        val estado = estadoDe(
            maos = listOf(
                listOf(PedraDomino(4, 4)),
                listOf(PedraDomino(5, 5)),
            ),
            mesa = listOf(PedraDomino(4, 1), PedraDomino(1, 0)), // pontas 4 e 0
            numeroRodada = 3,
        )
        val jogadores = estado.jogadores.mapIndexed { i, j ->
            JogadorDomino(j.nome, j.mao, if (i == 0) 95 else 20)
        }
        val preparado = estado.copy(jogadores = jogadores)
        val novo = DominoRegras.jogar(preparado, 0, PedraDomino(4, 4), LadoMesa.ESQUERDA)
        assertEquals(95 + 10 + 1, novo.jogadores[0].pontos, "95 + 10 dos outros + 1 bônus de carroça")
        assertEquals(0, novo.campeao)
    }

    @Test
    fun `próxima rodada redistribui com o ganhador começando`() {
        val base = estadoDe(
            maos = listOf(
                listOf(PedraDomino(3, 3)),
                listOf(PedraDomino(4, 4)),
            ),
            mesa = listOf(PedraDomino(3, 2), PedraDomino(2, 1)),
        )
        val fechada = DominoRegras.jogar(base, 0, PedraDomino(3, 3), LadoMesa.ESQUERDA)
        val nova = DominoRegras.iniciarProximaRodada(fechada)
        assertEquals(2, nova.numeroRodada)
        assertEquals(0, nova.vez, "quem bateu começa a rodada seguinte")
        assertEquals(7, nova.jogadores[0].mao.size)
        assertEquals(7, nova.jogadores[1].mao.size)
        assertTrue(nova.mesa.isEmpty())
        assertEquals(9, nova.jogadores[0].pontos, "8 dos outros + 1 bônus de carroça")
    }
}

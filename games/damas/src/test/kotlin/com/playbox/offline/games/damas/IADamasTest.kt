package com.playbox.offline.games.damas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IADamasTest {

    @Test
    fun `IA respeita a captura obrigatória em todas as dificuldades`() {
        val casas: List<MutableList<Peca?>> =
            List(8) { MutableList(8) { null } }
        casas[4][1] = Peca(CorPeca.CLARA)
        casas[3][2] = Peca(CorPeca.ESCURA)
        val estado = EstadoDamas(casas, CorPeca.ESCURA)

        for (ia in listOf(IADamas.facil(), IADamas.medio(), IADamas.dificil())) {
            val escolhida = ia.escolherJogada(estado)
            assertEquals(Casa(5, 0), escolhida.destino)
            assertEquals(listOf(Casa(4, 1)), escolhida.capturas)
        }
    }

    @Test
    fun `IA devolve lance legal na posição inicial`() {
        val estado = DamasRegras.inicial()
        val legais = DamasRegras.jogadasLegais(estado)
        for (ia in listOf(IADamas.facil(), IADamas.medio(), IADamas.dificil())) {
            val escolhida = ia.escolherJogada(estado)
            assertTrue(escolhida in legais, "lance da IA fora do conjunto legal")
        }
    }

    @Test
    fun `partida entre duas IAs termina com um resultado definido`() {
        var estado = DamasRegras.inicial()
        val iaA = IADamas(profundidade = 2) // joga com as claras
        val iaB = IADamas(profundidade = 2) // joga com as escuras
        var guardas = 0
        while (guardas < 600) {
            if (DamasRegras.resultado(estado) != null) break
            val jogadas = DamasRegras.jogadasLegais(estado)
            assertTrue(jogadas.isNotEmpty(), "sem resultado e sem lances")
            val ia = if (estado.vez == CorPeca.CLARA) iaA else iaB
            val lance = ia.escolherJogada(estado)
            assertTrue(lance in jogadas)
            estado = DamasRegras.aplicar(estado, lance)
            guardas++
        }
        assertTrue(guardas < 600, "a partida não terminou dentro do limite")
        assertTrue(DamasRegras.resultado(estado) != null)
    }
}

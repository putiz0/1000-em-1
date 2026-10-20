package com.playbox.offline.games.xadrez

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IAXadrezTest {

    @Test
    fun `IA devolve lance legal na posição inicial nos três níveis`() {
        val estado = XadrezRegras.inicial()
        val legais = XadrezRegras.jogadasLegais(estado)
        for (ia in listOf(IAXadrez.facil(), IAXadrez.medio(), IAXadrez.dificil())) {
            val escolhido = ia.escolherLance(estado)
            assertTrue(escolhido in legais, "lance da IA fora do conjunto legal")
        }
    }

    @Test
    fun `IA na posição de mate do tolo captura a dama que ameaça o rei`() {
        // Depois de f3 e5 g4, a negra mate em h4 está disponível e deve ser escolhida
        // por pelo menos o nível médio (buscar mate em profundidade 3).
        var estado = XadrezRegras.inicial()
        XadrezRegras.aplicar(estado, LanceXadrez(Pos(6, 5), Pos(5, 5))).also { estado = it }
        XadrezRegras.aplicar(estado, LanceXadrez(Pos(1, 4), Pos(3, 4))).also { estado = it }
        XadrezRegras.aplicar(estado,
            LanceXadrez(Pos(6, 6), Pos(4, 6))).also { estado = it }
        var estadoDessas = estado
        val legs = XadrezRegras.jogadasLegais(estadoDessas)
        val matePossivel = legs.first { it.origem == Pos(0, 3) && it.destino == Pos(4, 7) }
        val escolhido = IAXadrez.medio().escolherLance(estadoDessas)
        assertEquals(matePossivel, escolhido)
    }

    @Test
    fun `partida entre duas IAs termina em resultado definido`() {
        var estado = XadrezRegras.inicial()
        val iaBrancas = IAXadrez(profundidadeTotal = 1)
        val iaNegras = IAXadrez(profundidadeTotal = 1)
        var passos = 0
        while (passos < 600) {
            val resultado = XadrezRegras.resultado(estado)
            if (resultado != null) break
            val jogadas = XadrezRegras.jogadasLegais(estado)
            assertTrue(jogadas.isNotEmpty(), "sem resultado e sem lances legais")
            val ia = if (estado.vez == CorXadrez.BRANCAS) iaBrancas else iaNegras
            val lance = ia.escolherLance(estado)
            assertTrue(lance in jogadas)
            estado = XadrezRegras.aplicar(estado, lance)
            passos++
        }
        assertTrue(passos < 600, "a partida não terminou dentro do limite")
        assertTrue(XadrezRegras.resultado(estado) != null)
    }
}

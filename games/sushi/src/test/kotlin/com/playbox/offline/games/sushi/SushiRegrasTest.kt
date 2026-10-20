package com.playbox.offline.games.sushi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Testes do motor do Festival de Sushi: draft/pares, pontuação por prato,
 * bônus de pudim e IA heurística. Mãos são montadas à mão para deduzir.
 */
class SushiRegrasTest {

    private val config = SushiConfig()

    private fun novo(contraApp: Boolean = false): EstadoSushi =
        SushiRegras.novoJogo(config, listOf("A", "B"), contraApp)

    @Test
    fun partidaComecaComMaosDaComposicao() {
        val e = novo()
        assertEquals(8, e.mao[0].size)
        assertEquals(8, e.mao[1].size)
        assertEquals(1, e.rodada)
        assertEquals(listOf(null, null), e.picks)
        assertEquals(0, e.pudim[0] + e.pudim[1])
        // a mesa da rodada segue a composição do pacote
        val todas = e.mao[0] + e.mao[1]
        config.composicao.forEach { (id, qtd) -> assertEquals(qtd, todas.count { it == id }) }
    }

    @Test
    fun parDeEscolhasRevelaAplicaETrocaMaos() {
        var e = novo().copy(
            mao = listOf(listOf("rolinho", "temaki"), listOf("niguiri", "pudim"))
        )
        e = SushiRegras.escolher(e, 0, 0) // guarda pick, ainda não revela
        assertEquals(listOf(0, null), e.picks)
        assertEquals(1, e.vez)

        e = SushiRegras.escolher(e, 1, 1) // completa o par: revela
        // troca: o assento 0 recebe o restante da mão do assento 1 (sem o pudim)
        assertEquals(listOf("niguiri"), e.mao[0])
        assertEquals(listOf("temaki"), e.mao[1], "mão trocada: restante do assento 0")
        assertEquals(mapOf("rolinho" to 1), e.prato[0])
        assertTrue(e.prato[1].isEmpty())
        assertEquals(1, e.pudim[1], "pudim fica separado do prato")
        assertEquals(listOf("rolinho", "pudim"), e.ultimaRevelacao)
        assertEquals(listOf(null, null), e.picks)
        assertEquals(0, e.vez, "picks limpos: vez volta ao assento 0")
    }

    @Test
    fun rodadaFechaQuandoMaosEsvaziamEPontua() {
        var e = novo().copy(
            mao = listOf(listOf("rolinho", "niguiri"), listOf("temaki", "cha"))
        )
        e = SushiRegras.escolher(e, 0, 0)
        e = SushiRegras.escolher(e, 1, 0) // rolinho × temaki; mãos [niguiri] / [cha]
        assertTrue(!e.rodadaFechada)
        e = SushiRegras.escolher(e, 0, 0)
        e = SushiRegras.escolher(e, 1, 0) // niguiri × chá; esvazia

        assertTrue(e.rodadaFechada)
        // prato 0: rolinho + niguiri = 2 + 2 = 4
        // prato 1: temaki (1 < meta → 0) + chá (1 chá × 2 itens = 2) = 2
        assertEquals(listOf(4, 2), e.pontos)
        assertEquals(2, e.rodadaDetalhes.size)
    }

    @Test
    fun pontosDePratoConferemComConfig() {
        // niguiri: 2 × 2 = 4
        assertEquals(4, SushiRegras.pontosDaRodada(mapOf("niguiri" to 2), config))
        // rolinho + wasabi: combos 2 → 2×2 + 2×(4−2) = 8; wasabi sobra não pontua
        assertEquals(8, SushiRegras.pontosDaRodada(mapOf("rolinho" to 2, "wasabi" to 3), config))
        assertEquals(4, SushiRegras.pontosDaRodada(mapOf("rolinho" to 1, "wasabi" to 2), config))
        assertEquals(0, SushiRegras.pontosDaRodada(mapOf("wasabi" to 2), config))
        // gengibre: 1 × 3
        assertEquals(3, SushiRegras.pontosDaRodada(mapOf("gengibre" to 1), config))
        // temaki: meta 3 → 12; 2 não pontua
        assertEquals(12, SushiRegras.pontosDaRodada(mapOf("temaki" to 3), config))
        assertEquals(0, SushiRegras.pontosDaRodada(mapOf("temaki" to 2), config))
        // mochi: pares → 2: 5; 3: 5; 4: 10
        assertEquals(5, SushiRegras.pontosDaRodada(mapOf("mochi" to 2), config))
        assertEquals(5, SushiRegras.pontosDaRodada(mapOf("mochi" to 3), config))
        assertEquals(10, SushiRegras.pontosDaRodada(mapOf("mochi" to 4), config))
        // chá: cada chá × itens do prato
        assertEquals(12, SushiRegras.pontosDaRodada(mapOf("cha" to 2, "niguiri" to 2), config))
    }

    @Test
    fun proximaRodadaMantemTotaisPudimENovaMesa() {
        var e = novo().copy(
            mao = listOf(emptyList(), emptyList()),
            prato = listOf(mapOf("rolinho" to 2), mapOf("temaki" to 3)),
            pudim = listOf(1, 0),
            pontos = listOf(4, 12),
            rodadaFechada = true,
        )
        e = SushiRegras.proximaRodada(e)
        assertEquals(2, e.rodada)
        assertEquals(8, e.mao[0].size)
        assertEquals(8, e.mao[1].size)
        assertTrue(e.prato[0].isEmpty())
        assertEquals(1, e.pudim[0])
        assertEquals(listOf(4, 12), e.pontos)
        assertTrue(!e.rodadaFechada)
    }

    @Test
    fun bonusDePudimVitoriaEEmpate() {
        // B tem mais pudim: B recebe +6; total 10 × 14 → vencedor 1
        var e = novo().copy(pontos = listOf(10, 8), pudim = listOf(2, 5))
        e = SushiRegras.encerrarPartida(e)
        assertTrue(e.fim)
        assertEquals(1, e.vencedor)
        assertEquals(listOf(10, 14), e.pontos)

        // pudim empatado: +3 cada; vencedor por total (0 vence 13 × 11)
        e = novo().copy(pontos = listOf(10, 8), pudim = listOf(2, 2))
        e = SushiRegras.encerrarPartida(e)
        assertEquals(0, e.vencedor)
        assertEquals(listOf(13, 11), e.pontos)
    }

    @Test
    fun fimNaUltimaRodadaFechaEnvolveBonusNaDetalhes() {
        var e = novo().copy(
            rodada = 3,
            mao = listOf(emptyList(), emptyList()),
            prato = listOf(mapOf("rolinho" to 1), emptyMap()),
            pudim = listOf(1, 2),
            pontos = listOf(4, 0),
        )
        e = SushiRegras.fecharRodada(e)
        assertTrue(e.fim) // rodada 3 = última
        // pontos da rodada: 2 × 0; bônus: B mais pudim (+6) → 6 × 6: empate
        assertEquals(listOf(6, 6), e.pontos)
        assertEquals(null, e.vencedor)
        assertTrue(e.rodadaDetalhes.last().startsWith("Bônus de pudim"))
    }

    @Test
    fun iaSegueAsPrioridadesDoPacote() {
        val ia = IAsushi()

        // falta 1 temaki para a meta → vai de temaki
        var e = novo().copy(rodada = 1, mao = listOf(
            listOf(), listOf("niguiri", "temaki", "rolinho")
        ), prato = listOf(emptyMap(), mapOf("temaki" to 2)))
        assertEquals(1, ia.escolher(e, 1)) // índice do temaki

        // prato com mochi ímpar fecha o par
        e = novo().copy(rodada = 1, mao = listOf(
            listOf(), listOf("niguiri", "mochi", "rolinho")
        ), prato = listOf(emptyMap(), mapOf("mochi" to 1)))
        assertEquals(1, ia.escolher(e, 1))

        // rolinho no prato sem wasabi → wasabi
        e = novo().copy(rodada = 1, mao = listOf(
            listOf(), listOf("wasabi", "rolinho", "niguiri")
        ), prato = listOf(emptyMap(), mapOf("rolinho" to 2)))
        assertEquals(0, ia.escolher(e, 1))

        // nada prioridade: rolinho antes de niguiri/gengibre
        e = novo().copy(rodada = 1, mao = listOf(
            listOf(), listOf("niguiri", "gengibre", "rolinho")
        ), prato = listOf(emptyMap(), emptyMap()))
        assertEquals(2, ia.escolher(e, 1))
    }

    @Test
    fun escolherRefusaLancesInvalidos() {
        var e = novo().copy(mao = listOf(listOf("rolinho"), listOf("temaki")))
        e = SushiRegras.escolher(e, 0, 0)
        assertFailsWith<IllegalArgumentException> { SushiRegras.escolher(e, 0, 0) }
        assertFailsWith<IllegalArgumentException> { SushiRegras.escolher(e, 1, 3) }
    }

    @Test
    fun contraOAppOhumanoEscolheEOAppResponde() {
        var jogo = SushiPartida(config, contraApp = true)
        // mão 8 cartas; grille o primeiro lance
        val antes = jogo.estado.value
        assertEquals(0, antes.vez)
        jogo.escolherHumano(0)
        val depois = jogo.estado.value
        // huma picked + IA picked → par revelado
        assertEquals(listOf(null, null), depois.picks)
        assertTrue(depois.ultimaRevelacao[0] != null && depois.ultimaRevelacao[1] != null)
    }

    @Test
    fun jogadoresLocaisAlternamEmparelha() {
        var jogo = SushiPartida(config, contraApp = false)
        val antes = jogo.estado.value
        assertEquals(0, antes.vez)
        jogo.escolherHumano(0)
        assertEquals(1, jogo.estado.value.vez)
    }
}

package com.playbox.offline.games.sushi

import kotlin.random.Random

/**
 * Motor do Festival de Sushi (2 assentos).
 *
 * Cada rodada: mesa com composição do pacote, dividida em 2 mãos de N cartas.
 * Em cada lance os dois assentos escolhem 1 carta da própria mão; ao
 * completarem o par, as escolhas se revelam, entram no prato (pudim é
 * guardado à parte) e as mãos restantes se trocam. Quando a mão esvazia,
 * a rodada pontua e uma nova mesa é embaralhada, menos no fim da partida,
 * que soma os pontos de pudim (quem mais tem recebe o bônus; empate paga o
 * bônus de empate) e declara o campeão pelo total.
 *
 * Pontuação (todos os parâmetros vêm do config do pacote):
 *  - niguiri e rolinho valem fixos;
 *  - cada conjunto rolinho + wasabi vale wasabiTransformaRolinho
 *    (combos limitados tanto pelos wasabis quanto pelos rolinhos);
 *  - gengibre vale fixo; temaki atinge pontos ao completar a meta;
 *  - mochi pontua por pares completos; chá pontua por tamanho do prato;
 *  - pudim só pontua no fim da partida.
 */
object SushiRegras {

    fun novoJogo(
        config: SushiConfig,
        nomes: List<String>,
        contraApp: Boolean,
        aleatorio: Random = Random.Default,
    ): EstadoSushi {
        require(nomes.size == 2) { "partida sempre de 2 assentos (humano × IA ou 2 humanos)" }
        val mao = distribuir(config, aleatorio)
        return EstadoSushi(
            config = config,
            nomes = nomes,
            contraApp = contraApp,
            rodada = 1,
            mao = mao,
            picks = listOf(null, null),
            prato = listOf(emptyMap(), emptyMap()),
            pudim = listOf(0, 0),
            pontos = listOf(0, 0),
            log = listOf("Rodada 1: mãos distribuídas (${config.cartasPorMao} cartas para cada)."),
        )
    }

    /** Nova mesa embaralhada a partir da composição do pacote. */
    fun distribuir(config: SushiConfig, aleatorio: Random): List<List<String>> {
        val pool = mutableListOf<String>()
        config.composicao.forEach { (id, quantidade) -> repeat(quantidade) { pool.add(id) } }
        pool.shuffle(aleatorio)
        val m = config.cartasPorMao
        return listOf(pool.subList(0, m).toList(), pool.subList(m, 2 * m).toList())
    }

    /**
     * O assento escolhe a carta [indice] da própria mão. Quando o par
     * completa, revela, aplica no prato, troca as mãos e (se a mão esvaziou)
     * fecha a rodada.
     */
    fun escolher(estado: EstadoSushi, assento: Int, indice: Int): EstadoSushi {
        require(!estado.fim) { "a partida já acabou" }
        require(!estado.rodadaFechada) { "rodada fechada: aguardando próxima rodada" }
        require(estado.picks[assento] == null) { "o assento $assento já escolheu neste lance" }
        require(indice in estado.mao[assento].indices) { "índice fora da mão" }

        val e1 = estado.copy(picks = estado.picks.comNo(assento, indice))
        return if (e1.vez == -1) revelar(e1) else e1
    }

    private fun revelar(estado: EstadoSushi): EstadoSushi {
        val config = estado.config
        val i0 = estado.picks[0]!!
        val i1 = estado.picks[1]!!
        val carta0 = estado.mao[0][i0]
        val carta1 = estado.mao[1][i1]

        var pudim = estado.pudim
        var prato0 = estado.prato[0]
        var prato1 = estado.prato[1]
        if (carta0 == Cartas.PUDIM) pudim = pudim.comNo(0, pudim[0] + 1) else prato0 = prato0.adiciona(carta0)
        if (carta1 == Cartas.PUDIM) pudim = pudim.comNo(1, pudim[1] + 1) else prato1 = prato1.adiciona(carta1)

        val restante0 = estado.mao[0].semIndice(i0)
        val restante1 = estado.mao[1].semIndice(i1)

        var e = estado
            .copy(
                mao = listOf(restante1, restante0),
                prato = listOf(prato0, prato1),
                pudim = pudim,
                picks = listOf(null, null),
                ultimaRevelacao = listOf(carta0, carta1),
            )
            .comLog(
                "${estado.nomes[0]} escolheu ${corretorPlural(carta0, 1)}; " +
                    "${estado.nomes[1]} escolheu ${corretorPlural(carta1, 1)}."
            )

        return if (e.mao[0].isEmpty()) fecharRodada(e) else e
    }

    /** Fecha a rodada: pontua os pratos; na última rodada encerra a partida. */
    fun fecharRodada(estado: EstadoSushi): EstadoSushi {
        require(estado.mao[0].isEmpty() && estado.mao[1].isEmpty()) { "mãos ainda têm cartas" }
        val config = estado.config
        val rodada0 = pontosDaRodada(estado.prato[0], config)
        val rodada1 = pontosDaRodada(estado.prato[1], config)
        val detalhes = listOf(
            "${estado.nomes[0]} fecha a rodada ${estado.rodada} com $rodada0 pontos. " +
                resumoDoPrato(estado.prato[0]),
            "${estado.nomes[1]} fecha a rodada ${estado.rodada} com $rodada1 pontos. " +
                resumoDoPrato(estado.prato[1]),
        )
        var e = estado
            .copy(
                pontos = listOf(estado.pontos[0] + rodada0, estado.pontos[1] + rodada1),
                rodadaFechada = true,
                rodadaDetalhes = detalhes,
            )
            .comLog("Fim da rodada ${estado.rodada}: ${estado.nomes[0]} $rodada0 × ${estado.nomes[1]} $rodada1.")

        return if (estado.rodada >= config.rodadas) encerrarPartida(e) else e
    }

    /** Depois do diálogo de rodada fechada: nova mesa (mantém pudins e totais). */
    fun proximaRodada(estado: EstadoSushi, aleatorio: Random = Random.Default): EstadoSushi {
        require(estado.rodadaFechada && !estado.fim) { "sem rodada fechada para avançar" }
        return estado
            .copy(
                rodada = estado.rodada + 1,
                mao = distribuir(estado.config, aleatorio),
                picks = listOf(null, null),
                prato = listOf(emptyMap(), emptyMap()),
                rodadaFechada = false,
                rodadaDetalhes = emptyList(),
                ultimaRevelacao = listOf(null, null),
            )
            .comLog("Rodada ${estado.rodada + 1}: mãos redistribuídas.")
    }

    /** Placar final: bônus de pudim e campeão. */
    fun encerrarPartida(estado: EstadoSushi): EstadoSushi {
        val config = estado.config
        val p0 = estado.pudim[0]
        val p1 = estado.pudim[1]
        val bonus0: Int
        val bonus1: Int
        when {
            p0 > p1 -> { bonus0 = config.pudimFimVitoria; bonus1 = 0 }
            p1 > p0 -> { bonus0 = 0; bonus1 = config.pudimFimVitoria }
            else -> { bonus0 = config.pudimFimEmpate; bonus1 = config.pudimFimEmpate }
        }
        val totais = listOf(estado.pontos[0] + bonus0, estado.pontos[1] + bonus1)
        val vencedor = when {
            totais[0] > totais[1] -> 0
            totais[1] > totais[0] -> 1
            else -> null
        }
        return estado
            .copy(
                pontos = totais,
                fim = true,
                vencedor = vencedor,
                rodadaDetalhes = estado.rodadaDetalhes + listOf(
                    "Bônus de pudim: ${estado.nomes[0]} $p0 cartas (+$bonus0) · " +
                        "${estado.nomes[1]} $p1 cartas (+$bonus1)."
                ),
            )
            .comLog("Fim da partida!")
    }

    /** Pontos de um prato da rodada (todos os parâmetros do config). */
    fun pontosDaRodada(prato: Map<String, Int>, config: SushiConfig): Int {
        val totalItens = prato.values.sum()
        val niguiri = (prato[Cartas.NIGUIRI] ?: 0) * config.pontosNiguiri
        val rolinho = (prato[Cartas.ROLINHO] ?: 0) * config.pontosRolinho
        val combos = minOf(prato[Cartas.ROLINHO] ?: 0, prato[Cartas.WASABI] ?: 0)
        val extraWasabi = combos * (config.wasabiTransformaRolinho - config.pontosRolinho)
        val gengibre = (prato[Cartas.GENGIBRE] ?: 0) * config.pontosGengibre
        val temaki = prato[Cartas.TEMAKI] ?: 0
        val pontosTemaki = if (temaki >= config.temakiMeta) config.temakiPontos else 0
        val mochi = (prato[Cartas.MOCHI] ?: 0) / 2 * config.mochiParPontos
        val cha = (prato[Cartas.CHA] ?: 0) * totalItens * config.chaPorCarta
        return niguiri + rolinho + extraWasabi + gengibre + pontosTemaki + mochi + cha
    }

    /** Resumo legível do prato (para o diálogo de fechamento). */
    fun resumoDoPrato(prato: Map<String, Int>): String =
        if (prato.isEmpty()) {
            "Prato vazio."
        } else {
            "Prato: " + prato.entries
                .sortedBy { -it.value }
                .joinToString(", ") { "${corretorPlural(it.key, it.value)}" } + "."
    }

    /** Nome com plural simples para exibição. */
    fun corretorPlural(id: String, quantidade: Int): String {
        val nome = nomesDeCartas[id] ?: id
        val plural = when (id) {
            Cartas.PUDIM -> "pudins"
            Cartas.CHA -> "chás"
            else -> nome + "s"
        }
        return if (quantidade > 1) "$quantidade $plural" else nome
    }

    val nomesDeCartas = mapOf(
        Cartas.NIGUIRI to "Niguiri",
        Cartas.ROLINHO to "Rolinho",
        Cartas.TEMAKI to "Temaki",
        Cartas.MOCHI to "Mochi",
        Cartas.WASABI to "Wasabi",
        Cartas.GENGIBRE to "Gengibre",
        Cartas.CHA to "Chá",
        Cartas.PUDIM to "Pudim",
    )

    // ---------- helpers ----------

    private fun EstadoSushi.comLog(msg: String): EstadoSushi =
        copy(log = (log + msg).takeLast(60))

    /** Lista imutável com o índice trocado. */
    private fun <T> List<T>.comNo(indice: Int, valor: T): List<T> =
        toMutableList().also { it[indice] = valor }.toList()

    /** Lista imutável sem o elemento do índice. */
    private fun <T> List<T>.semIndice(indice: Int): List<T> =
        toMutableList().also { it.removeAt(indice) }.toList()

    /** Mapa imutável com +1 no id. */
    private fun Map<String, Int>.adiciona(id: String): Map<String, Int> =
        this + (id to ((this[id] ?: 0) + 1))
}

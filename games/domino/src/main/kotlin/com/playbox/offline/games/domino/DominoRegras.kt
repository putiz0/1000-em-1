package com.playbox.offline.games.domino

import kotlin.random.Random

/**
 * Dominó duplo-6 para 2 a 4 jogadores no mesmo aparelho (passa-e-joga).
 *
 * Regras implementadas (todas descritas no regras.md do pacote):
 *  - 28 pedras; 7 na mão de cada jogador; o resto fica para comprar;
 *  - quem tem a maior carroça começa podendo jogar qualquer pedra
 *    (se ninguém tiver carroça, quem tem a pedra de maior soma; empate:
 *    o jogador de menor número);
 *  - só encaixa pedra com número igual à ponta (esquerda ou direita);
 *  - sem pedra que encaixe: compra do monte até poder jogar; com o
 *    monte vazio, passa ("toco");
 *  - bater = ficar sem pedras: o baterista soma os pontos das mãos
 *    dos outros (+ bônus se a última pedra jogada for carroça);
 *  - trancado = todos passaram com o monte vazio: vence quem tem MENOS
 *    pontos na mão e soma os dos outros (empate: ninguém pontua);
 *  - rodadas seguem até alguém atingir a meta de pontos (padrão 100);
 *    o vencedor de uma rodada começa a próxima (empate em tranca:
 *    quem tem a maior pedra da nova distribuição).
 */
object DominoRegras {

    const val MAO_PADRAO = 7
    const val META_PADRAO = 100

    fun baralho(): List<PedraDomino> {
        val pedras = mutableListOf<PedraDomino>()
        for (a in 0..6) for (b in a..6) pedras += PedraDomino(a, b)
        return pedras
    }

    fun novoJogo(
        nomes: List<String>,
        maoInicial: Int = MAO_PADRAO,
        aleatorio: Random = Random.Default,
    ): EstadoDomino {
        require(nomes.size in 2..4) { "de 2 a 4 jogadores" }
        val baralho = baralho().shuffled(aleatorio)
        val (jogadores, resto) = distribuir(baralho, nomes, maoInicial)
        return EstadoDomino(
            jogadores = jogadores,
            monte = resto,
            mesa = emptyList(),
            vez = primeiroJogador(jogadores),
            passesSeguidos = 0,
            ultimaFoiCarroca = false,
            numeroRodada = 1,
        )
    }

    private fun distribuir(
        baralho: List<PedraDomino>,
        nomes: List<String>,
        maoInicial: Int,
    ): Pair<List<JogadorDomino>, List<PedraDomino>> {
        val maos = List(nomes.size) { mutableListOf<PedraDomino>() }
        var restante = baralho
        for (rodada in 0 until maoInicial) {
            for (j in nomes.indices) {
                val proxima = restante.firstOrNull() ?: break
                maos[j] += proxima
                restante = restante.drop(1)
            }
        }
        val jogadores = nomes.mapIndexed { i, nome ->
            JogadorDomino(nome, maos[i].sortedBy { it.soma })
        }
        return jogadores to restante
    }

    /** Quem tem a maior carroça; sem carroças, a pedra de maior soma. */
    fun primeiroJogador(jogadores: List<JogadorDomino>): Int {
        var melhorIndice = 0
        var melhorChave = -1
        jogadores.forEachIndexed { i, jogador ->
            val carroca = jogador.mao.filter { it.isCarroca }.maxByOrNull { it.ladoA }
            val chave = if (carroca != null) 10 + carroca.ladoA else (jogador.mao.maxByOrNull { it.soma }?.soma ?: -1)
            if (chave > melhorChave) {
                melhorChave = chave
                melhorIndice = i
            }
        }
        return melhorIndice
    }

    fun jogadasPossiveis(estado: EstadoDomino, jogador: Int): Map<PedraDomino, List<LadoMesa>> {
        val mao = estado.jogadores[jogador].mao
        if (estado.mesa.isEmpty()) {
            return mao.associateWith { listOf(LadoMesa.DIREITA) }
        }
        val esq = estado.pontaEsquerda!!
        val dir = estado.pontaDireita!!
        val resultado = mutableMapOf<PedraDomino, List<LadoMesa>>()
        for (pedra in mao) {
            val lados = mutableListOf<LadoMesa>()
            if (pedra.ladoA == esq || pedra.ladoB == esq) lados += LadoMesa.ESQUERDA
            if (pedra.ladoA == dir || pedra.ladoB == dir) lados += LadoMesa.DIREITA
            if (lados.isNotEmpty()) resultado[pedra] = lados
        }
        return resultado
    }

    fun podeJogar(estado: EstadoDomino, jogador: Int): Boolean =
        jogadasPossiveis(estado, jogador).isNotEmpty()

    /**
     * Joga uma pedra num lado. Se a mão do jogador esvazia, a rodada
     * fecha com a batida; senão a vez segue para o próximo jogador.
     * [metaPontos] define quando a partida inteira acaba.
     */
    fun jogar(
        estado: EstadoDomino,
        jogador: Int,
        pedra: PedraDomino,
        lado: LadoMesa,
        metaPontos: Int = META_PADRAO,
        bonusCarrocaBatida: Int = 1,
    ): EstadoDomino {
        require(estado.vez == jogador) { "não é a vez do jogador $jogador" }
        val lados = jogadasPossiveis(estado, jogador)[pedra]
            ?: error("essa pedra não encaixa nas pontas")
        require(lado in lados) { "essa pedra não encaixa no lado $lado" }

        val novaMesa = if (estado.mesa.isEmpty()) {
            listOf(pedra)
        } else when (lado) {
            LadoMesa.DIREITA -> {
                val ponta = estado.pontaDireita
                val orientada = if (pedra.ladoA == ponta) pedra else PedraDomino(pedra.ladoB, pedra.ladoA)
                estado.mesa + orientada
            }
            LadoMesa.ESQUERDA -> {
                val ponta = estado.pontaEsquerda
                val orientada = if (pedra.ladoB == ponta) pedra else PedraDomino(pedra.ladoB, pedra.ladoA)
                listOf(orientada) + estado.mesa
            }
        }
        val novaMao = estado.jogadores.mapIndexed { i, j ->
            if (i == jogador) JogadorDomino(j.nome, j.mao - pedra, j.pontos) else j
        }
        val base = estado.copy(
            jogadores = novaMao,
            mesa = novaMesa,
            passesSeguidos = 0,
            ultimaFoiCarroca = pedra.isCarroca,
        )
        if (novaMao[jogador].mao.isEmpty()) {
            return fecharPorBatida(base, metaPontos, bonusCarrocaBatida)
        }
        return base.copy(vez = (base.vez + 1) % base.jogadores.size)
    }

    /**
     * Compra do monte (uma por vez) até poder jogar ou esvaziar o monte.
     * A vez continua com o mesmo jogador: quem compra deve jogar ou,
     * se o monte acabou sem jogada, passar.
     */
    fun comprar(estado: EstadoDomino): EstadoDomino {
        require(estado.monte.isNotEmpty()) { "não há pedras para comprar" }
        var mao = estado.jogadores[estado.vez].mao
        var monte = estado.monte
        while (monte.isNotEmpty()) {
            val pedra = monte.first()
            monte = monte.drop(1)
            mao = (mao + pedra).sortedBy { it.soma }
            val ensaio = estado.copy(
                jogadores = comMao(estado, estado.vez, mao),
                monte = monte,
            )
            if (podeJogar(ensaio, estado.vez)) return ensaio
        }
        return estado.copy(
            jogadores = comMao(estado, estado.vez, mao),
            monte = emptyList(),
        )
    }

    private fun comMao(
        estado: EstadoDomino,
        jogador: Int,
        novaMao: List<PedraDomino>,
    ): List<JogadorDomino> = estado.jogadores.mapIndexed { i, j ->
        if (i == jogador) JogadorDomino(j.nome, novaMao, j.pontos) else j
    }

    /** Toco: passa a vez quando não há jogada possível nem compras. */
    fun passar(estado: EstadoDomino): EstadoDomino {
        require(!podeJogar(estado, estado.vez)) { "existe jogada possível" }
        val proximo = estado.copy(
            vez = (estado.vez + 1) % estado.jogadores.size,
            passesSeguidos = estado.passesSeguidos + 1,
        )
        if (proximo.passesSeguidos >= proximo.jogadores.size) {
            return fecharPorTranca(proximo, META_PADRAO)
        }
        return proximo
    }

    private fun fecharPorBatida(
        estado: EstadoDomino,
        metaPontos: Int,
        bonusCarrocaBatida: Int,
    ): EstadoDomino {
        val baterista = estado.vez
        val somaOutros = estado.jogadores
            .filterIndexed { i, _ -> i != baterista }
            .sumOf { j -> j.mao.sumOf { it.soma } }
        val bonus = if (estado.ultimaFoiCarroca) bonusCarrocaBatida else 0
        val detalhes = buildList {
            add("${estado.jogadores[baterista].nome} bateu." + if (bonus > 0) " Bater com carroça: +$bonus de bônus." else "")
            estado.jogadores.forEachIndexed { i, j ->
                val ganho = if (i == baterista) somaOutros + bonus else 0
                val restante = j.mao.sumOf { it.soma }
                add("${j.nome}: +$ganho (restou $restante na mão)")
            }
        }
        val pontos = mapOf(baterista to (somaOutros + bonus))
        return fechar(estado, baterista, pontos, detalhes, metaPontos)
    }

    private fun fecharPorTranca(estado: EstadoDomino, metaPontos: Int): EstadoDomino {
        val somas = estado.jogadores
            .mapIndexed { i, j -> i to j.mao.sumOf { it.soma } }
        val menor = somas.minOf { it.second }
        val empatados = somas.filter { it.second == menor }
        return if (empatados.size > 1) {
            fechar(
                estado, null, emptyMap(),
                listOf("A rodada ficou trancada e empatada: ninguém pontua.") + detalhesTranca(estado),
                metaPontos,
            )
        } else {
            val vencedor = empatados.first().first
            val outros = somas.filter { it.first != vencedor }.sumOf { it.second }
            fechar(
                estado, vencedor,
                mapOf(vencedor to outros),
                listOf(
                    "A rodada ficou trancada; ${estado.jogadores[vencedor].nome} tem a menor mão e vence."
                ) + detalhesTranca(estado),
                metaPontos,
            )
        }
    }

    private fun detalhesTranca(estado: EstadoDomino): List<String> =
        estado.jogadores.mapIndexed { i, j ->
            "${j.nome}: ${j.mao.sumOf { it.soma }} pontos na mão"
        }

    private fun fechar(
        estado: EstadoDomino,
        ganhador: Int?,
        pontosGanhos: Map<Int, Int>,
        detalhes: List<String>,
        metaPontos: Int,
    ): EstadoDomino {
        val atualizados = estado.jogadores.mapIndexed { i, j ->
            JogadorDomino(j.nome, j.mao, j.pontos + (pontosGanhos[i] ?: 0))
        }
        val campeao = atualizados.indexOfFirst { it.pontos >= metaPontos }.takeIf { it >= 0 }
        return estado.copy(
            jogadores = atualizados,
            rodadaFechada = RodadaDomino(
                texto = "Rodada ${estado.numeroRodada} fechada",
                ganhador = ganhador,
                pontosGanhos = pontosGanhos,
                detalhes = detalhes,
            ),
            campeao = campeao,
        )
    }

    /** Nova rodada: redistribui tudo, mantém pontuação; quem fechou começa. */
    fun iniciarProximaRodada(
        estado: EstadoDomino,
        maoInicial: Int = MAO_PADRAO,
        aleatorio: Random = Random.Default,
    ): EstadoDomino {
        val fechada = estado.rodadaFechada ?: error("a rodada atual não está fechada")
        require(estado.campeao == null) { "o jogo já acabou" }
        val (jogadores, resto) = distribuir(
            baralho().shuffled(aleatorio),
            estado.jogadores.map { it.nome },
            maoInicial,
        )
        val comPontos = jogadores.mapIndexed { i, j ->
            JogadorDomino(j.nome, j.mao, estado.jogadores[i].pontos)
        }
        return EstadoDomino(
            jogadores = comPontos,
            monte = resto,
            mesa = emptyList(),
            vez = fechada.ganhador ?: primeiroJogador(comPontos),
            passesSeguidos = 0,
            ultimaFoiCarroca = false,
            numeroRodada = estado.numeroRodada + 1,
        )
    }
}

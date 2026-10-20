package com.playbox.offline.games.sushi

import com.playbox.offline.core.packs.PackManager
import kotlinx.serialization.Serializable

/**
 * Configuração do Festival de Sushi (config.json do pacote).
 *
 * O motor usa EXATAMENTE estes parâmetros — nada de regra escondida em código:
 * pontos por tipo, campos de composição da mão e metas saem do arquivo.
 */
@Serializable
data class SushiConfig(
    val rodadas: Int = 3,
    val cartasPorMao: Int = 8,
    /** Composição da mesa por rodada (soma = 2 × cartasPorMao para 2 assentos). */
    val composicao: Map<String, Int> = mapOf(
        "niguiri" to 3,
        "rolinho" to 3,
        "temaki" to 2,
        "mochi" to 2,
        "wasabi" to 2,
        "gengibre" to 1,
        "cha" to 2,
        "pudim" to 1,
    ),
    val pontosNiguiri: Int = 2,
    val pontosRolinho: Int = 2,
    /** Valor do conjunto rolinho + wasabi (o rolinho passa a valer isto). */
    val wasabiTransformaRolinho: Int = 4,
    val pontosGengibre: Int = 3,
    /** Cada chá vale 1 ponto por carta do prato da rodada (incluindo o próprio chá). */
    val chaPorCarta: Int = 1,
    val temakiMeta: Int = 3,
    val temakiPontos: Int = 12,
    /** Pontos por par completo de mochis (sobra solta não pontua). */
    val mochiParPontos: Int = 5,
    val pudimFimVitoria: Int = 6,
    val pudimFimEmpate: Int = 3,
) {
    companion object {
        /** Lê e valida o config.json do pacote instalado (null se ausente/inválido). */
        fun doPacote(texto: String?): SushiConfig? {
            if (texto == null) return null
            return runCatching {
                val config = PackManager.json.decodeFromString<SushiConfig>(texto)
                require(config.rodadas >= 1) { "rodadas deve ser >= 1" }
                require(config.cartasPorMao >= 2) { "mão precisa de ao menos 2 cartas" }
                require(
                    config.composicao.values.sum() == 2 * config.cartasPorMao
                ) { "composição da mesa tem que somar 2 × cartasPorMao" }
                config
            }.getOrNull()
        }
    }
}

/**
 * Identificadores das cartas originais do jogo (dados do pacote).
 *
 *  niguiri  — prato simples;
 *  rolinho  — pode ganhar wasabi;
 *  temaki   — meta de iguais na fila;
 *  mochi    — pares completos;
 *  wasabi   — transforma 1 rolinho do prato;
 *  gengibre — prato de limpeza com pontos fixos;
 *  cha      — pauta a pontuação por tamanho do prato da rodada;
 *  pudim    — acumula até o fim da partida (não entra no prato da rodada).
 */
object Cartas {
    const val NIGUIRI = "niguiri"
    const val ROLINHO = "rolinho"
    const val TEMAKI = "temaki"
    const val MOCHI = "mochi"
    const val WASABI = "wasabi"
    const val GENGIBRE = "gengibre"
    const val CHA = "cha"
    const val PUDIM = "pudim"
}

/** Estado completo da partida (2 assentos: humano × IA ou 2 humanos locais). */
data class EstadoSushi(
    val config: SushiConfig,
    val nomes: List<String>,
    /** O assento 1 é IA quando a partida é contra o app. */
    val contraApp: Boolean,
    /** Rodada em curso (1..config.rodadas). */
    val rodada: Int,
    /** Mão real que cada assento tem agora (ids); troca a cada par de escolhas. */
    val mao: List<List<String>>,
    /** Índice escolhido na própria mão, por assento (null = aguardando). */
    val picks: List<Int?>,
    /** Cartas do prato na rodada atual (id → quantidade), sem os pudins. */
    val prato: List<Map<String, Int>>,
    /** Pudins guardados para o placar final. */
    val pudim: List<Int>,
    /** Pontos acumulados das rodadas já fechadas. */
    val pontos: List<Int>,
    /** true entre o fechamento da rodada e a próxima rodada. */
    val rodadaFechada: Boolean = false,
    /** Cartas reveladas no último par de escolhas (ids). */
    val ultimaRevelacao: List<String?> = listOf(null, null),
    /** Textos do fechamento da rodada em exibição. */
    val rodadaDetalhes: List<String> = emptyList(),
    val fim: Boolean = false,
    /** Vencedor por pontos totais; null em empate. */
    val vencedor: Int? = null,
    val log: List<String> = emptyList(),
) {
    /** Assento que ainda deve escolher (ou -1 se o par já está completo). */
    val vez: Int
        get() = when {
            picks[0] == null -> 0
            picks[1] == null -> 1
            else -> -1
        }
}

package com.playbox.offline.games.domino

/**
 * Pedra (a, b). `ladoA` é o lado que toca a peça anterior na mesa —
 * portanto PODE não estar em ordem crescente quando a pedra está na
 * mesa (orientation da cadeia). O baralho e as mãos usam canônicas
 * (a <= b), como gerado por [DominoRegras.baralho].
 */
data class PedraDomino(val ladoA: Int, val ladoB: Int) {
    val soma: Int get() = ladoA + ladoB
    val isCarroca: Boolean get() = ladoA == ladoB
}

data class JogadorDomino(
    val nome: String,
    val mao: List<PedraDomino>,
    val pontos: Int = 0,
)

enum class LadoMesa { ESQUERDA, DIREITA }

/**
 * Mesa: sequência de pedras orientadas (esquerda, direita), em que o
 * `direita` de cada uma casa com o `esquerda` da próxima.
 */
data class EstadoDomino(
    val jogadores: List<JogadorDomino>,
    /** Compras disponíveis (restante não distribuído). */
    val monte: List<PedraDomino>,
    val mesa: List<PedraDomino>,
    val vez: Int,
    /** Passes consecutivos com monte vazio (tocos). */
    val passesSeguidos: Int,
    /** Última pedra posta na mesa foi carroça? (bônus de batida) */
    val ultimaFoiCarroca: Boolean,
    val numeroRodada: Int,
    /** Rodada fechada (alguém bateu ou trancou) à espera do próximo round. */
    val rodadaFechada: RodadaDomino? = null,
    /** Jogo encerrado por meta de pontos. */
    val campeao: Int? = null,
) {
    val pontaEsquerda: Int? get() = mesa.firstOrNull()?.ladoA
    val pontaDireita: Int? get() = mesa.lastOrNull()?.ladoB
}

/** Resumo do fechamento de uma rodada. */
data class RodadaDomino(
    val texto: String,
    /** Quem fechou (índice) — null se empate em tranca. */
    val ganhador: Int?,
    val pontosGanhos: Map<Int, Int>,
    val detalhes: List<String>,
)

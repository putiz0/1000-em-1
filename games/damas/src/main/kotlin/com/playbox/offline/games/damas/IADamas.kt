package com.playbox.offline.games.damas

import kotlin.random.Random

/**
 * IA por negamax com poda alfa-beta.
 *
 * A pontuação de avaliação é sempre do ponto de vista do lado que move
 * (negamax). Dificuldades do jogo:
 *  - fácil:    profundidade 2, com 40% de chance de lance aleatório;
 *  - médio:    profundidade 4;
 *  - difícil:  profundidade 6.
 */
class IADamas(
    private val profundidade: Int,
    private val chanceDeLanceAleatorio: Double = 0.0,
    private val aleatorio: Random = Random.Default,
) {

    fun escolherJogada(estado: EstadoDamas): Jogada {
        val jogadas = DamasRegras.jogadasLegais(estado)
        require(jogadas.isNotEmpty()) { "não há jogadas legais" }
        if (chanceDeLanceAleatorio > 0.0 && aleatorio.nextDouble() < chanceDeLanceAleatorio) {
            return jogadas[aleatorio.nextInt(jogadas.size)]
        }
        var melhor = jogadas.first()
        var melhorNota = Int.MIN_VALUE
        var alfa = Int.MIN_VALUE
        // Capturas primeiro: melhora a poda.
        for (jogada in jogadas.sortedByDescending { it.capturas.size }) {
            val filho = DamasRegras.aplicar(estado, jogada)
            val nota = -negamax(filho, profundidade - 1, Int.MIN_VALUE, -alfa)
            if (nota > melhorNota) {
                melhorNota = nota
                melhor = jogada
            }
            if (nota > alfa) alfa = nota
        }
        return melhor
    }

    private fun negamax(estado: EstadoDamas, profundidade: Int, alfaEntrada: Int, beta: Int): Int {
        var alfa = alfaEntrada
        when (val r = DamasRegras.resultado(estado)) {
            ResultadoDamas.EMPATE -> return 0
            ResultadoDamas.VITORIA_CLARAS ->
                return if (estado.vez == CorPeca.CLARA) -PESO_VITORIA else PESO_VITORIA
            ResultadoDamas.VITORIA_ESCURAS ->
                return if (estado.vez == CorPeca.ESCURA) -PESO_VITORIA else PESO_VITORIA
            null -> Unit
        }
        if (profundidade == 0) return avaliar(estado)
        var melhor = Int.MIN_VALUE
        val jogadas = DamasRegras.jogadasLegais(estado).sortedByDescending { it.capturas.size }
        for (jogada in jogadas) {
            val filho = DamasRegras.aplicar(estado, jogada)
            val nota = -negamax(filho, profundidade - 1, -beta, -alfa)
            if (nota > melhor) melhor = nota
            if (melhor > alfa) alfa = melhor
            if (alfa >= beta) break
        }
        return melhor
    }

    /** Avaliação do ponto de vista de estado.vez. */
    private fun avaliar(estado: EstadoDamas): Int =
        material(estado, estado.vez) - material(estado, estado.vez.oposta())

    private fun material(estado: EstadoDamas, cor: CorPeca): Int {
        var soma = 0
        for (l in 0 until DamasRegras.TAMANHO) {
            for (c in 0 until DamasRegras.TAMANHO) {
                val peca = estado.casas[l][c] ?: continue
                if (peca.cor != cor) continue
                soma += if (peca.dama) {
                    350
                } else {
                    val avanco = if (cor == CorPeca.CLARA) 7 - l else l
                    100 + avanco * 4
                }
            }
        }
        return soma
    }

    companion object {
        private const val PESO_VITORIA = 1_000_000

        fun facil(): IADamas = IADamas(profundidade = 2, chanceDeLanceAleatorio = 0.40)
        fun medio(): IADamas = IADamas(profundidade = 4)
        fun dificil(): IADamas = IADamas(profundidade = 6)
    }
}

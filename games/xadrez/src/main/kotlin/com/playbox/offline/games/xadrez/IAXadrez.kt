package com.playbox.offline.games.xadrez

import kotlin.math.abs
import kotlin.random.Random

/**
 * IA por negamax com poda alfa-beta e ordenação de capturas (MVV-LVA).
 * Avaliação: material + bônus de centralização simples. Sem busca de
 * quiescência — é uma IA recreativa em três dificuldades:
 *  - fácil:   profundidade 1, com 35% de chance de lance aleatório;
 *  - médio:   profundidade 3;
 *  - difícil: profundidade 5.
 */
class IAXadrez(
    private val profundidadeTotal: Int,
    private val chanceDeLanceAleatorio: Double = 0.0,
    private val aleatorio: Random = Random.Default,
) {

    fun escolherLance(estado: EstadoXadrez): LanceXadrez {
        val lances = XadrezRegras.jogadasLegais(estado)
        require(lances.isNotEmpty()) { "não há lances legais" }
        if (chanceDeLanceAleatorio > 0.0 && aleatorio.nextDouble() < chanceDeLanceAleatorio) {
            return lances[aleatorio.nextInt(lances.size)]
        }
        val ordenados = lances.sortedByDescending { pontuacaoOrdenacao(it) }
        var melhor = ordenados.first()
        var melhorNota = -INF
        var alfa = -INF
        for (lance in ordenados) {
            val filho = XadrezRegras.aplicar(estado, lance)
            val nota = -negamax(filho, profundidadeTotal - 1, -INF, -alfa, ply = 1)
            if (nota > melhorNota) {
                melhorNota = nota
                melhor = lance
            }
            if (nota > alfa) alfa = nota
        }
        return melhor
    }

    private fun pontuacaoOrdenacao(lance: LanceXadrez): Int {
        var nota = 0
        lance.capturada?.let { vitima ->
            nota += 10 * valorPeca(vitima.tipo) - valorPeca(TipoPeca.PEAO)
        }
        if (lance.promover == TipoPeca.DAMA) nota += 500
        if (lance.roque != null) nota += 50
        return nota
    }

    private fun negamax(
        estado: EstadoXadrez,
        profundidade: Int,
        alfaEntrada: Int,
        beta: Int,
        ply: Int,
    ): Int {
        var alfa = alfaEntrada
        val lances = XadrezRegras.jogadasLegais(estado)
        if (lances.isEmpty()) {
            return if (XadrezRegras.inCheck(estado, estado.vez)) {
                // Quem move perdeu por mate (mate mais cedo pontua melhor).
                -(PESO_MATE - ply)
            } else {
                0
            }
        }
        if (profundidade == 0) return avaliar(estado)
        var melhor = -INF
        val ordenados = lances.sortedByDescending { pontuacaoOrdenacao(it) }
        for (lance in ordenados) {
            val filho = XadrezRegras.aplicar(estado, lance)
            val nota = -negamax(filho, profundidade - 1, -beta, -alfa, ply + 1)
            if (nota > melhor) melhor = nota
            if (melhor > alfa) alfa = melhor
            if (alfa >= beta) break
        }
        return melhor
    }

    /** Avaliação do ponto de vista de estado.vez. */
    private fun avaliar(estado: EstadoXadrez): Int {
        var soma = 0
        for (l in 0..7) for (c in 0..7) {
            val p = estado.casas[l][c] ?: continue
            val bruta = valorPeca(p.tipo) + bonusPosicao(p, l, c)
            soma += if (p.cor == estado.vez) bruta else -bruta
        }
        return soma
    }

    private fun bonusPosicao(peca: PecaXadrez, linha: Int, coluna: Int): Int {
        val distCentro = maxOf(abs(2 * linha - 7), abs(2 * coluna - 7)) // 0..7
        return when (peca.tipo) {
            TipoPeca.CAVALO, TipoPeca.BISPO -> (7 - distCentro)
            TipoPeca.DAMA -> 2 * (7 - distCentro)
            TipoPeca.PEAO -> when (peca.cor) {
                CorXadrez.BRANCAS -> (6 - linha) * 2 + (4 - distCentro)
                CorXadrez.NEGRAS -> linha * 2 + (4 - distCentro)
            }
            else -> 0
        }
    }

    private fun valorPeca(tipo: TipoPeca): Int = when (tipo) {
        TipoPeca.PEAO -> 100
        TipoPeca.CAVALO -> 320
        TipoPeca.BISPO -> 330
        TipoPeca.TORRE -> 500
        TipoPeca.DAMA -> 900
        TipoPeca.REI -> 0
    }

    companion object {
        /** Maior que qualquer nota real; evita saturação de Int. */
        private const val INF = 10_000_000
        private const val PESO_MATE = 1_000_000

        fun facil(): IAXadrez = IAXadrez(profundidadeTotal = 1, chanceDeLanceAleatorio = 0.35)
        fun medio(): IAXadrez = IAXadrez(profundidadeTotal = 3)
        fun dificil(): IAXadrez = IAXadrez(profundidadeTotal = 5)
    }
}

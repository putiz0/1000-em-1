package com.playbox.offline.games.damas

/**
 * Modelos do jogo de damas (regras brasileiras 8×8).
 *
 * Convenção de coordenadas:
 *  - linha 0 é o topo do tabuleiro, linha 7 é a base;
 *  - peças CLARAS começam nas linhas 5..7 e avançam na direção de linha menor;
 *  - peças ESCURAS começam nas linhas 0..2 e avançam na direção de linha maior;
 *  - casas jogáveis são as escuras, onde (linha + coluna) % 2 == 1.
 */
enum class CorPeca { CLARA, ESCURA }

fun CorPeca.oposta(): CorPeca =
    if (this == CorPeca.CLARA) CorPeca.ESCURA else CorPeca.CLARA

data class Peca(val cor: CorPeca, val dama: Boolean = false)

data class Casa(val linha: Int, val coluna: Int)

/**
 * Jogada completa. Em capturas múltiplas, [destino] é a casa final da cadeia
 * e [capturas] lista as peças saltadas em ordem.
 */
data class Jogada(
    val origem: Casa,
    val destino: Casa,
    val capturas: List<Casa> = emptyList(),
) {
    val isCaptura: Boolean get() = capturas.isNotEmpty()
}

data class EstadoDamas(
    val casas: List<List<Peca?>>,
    val vez: CorPeca,
    /** Meios-lances consecutivos sem captura (empate técnico). */
    val jogadasSemCaptura: Int = 0,
) {
    val pecasClaras: Int get() = casas.sumOf { linha -> linha.count { it?.cor == CorPeca.CLARA } }
    val pecasEscuras: Int get() = casas.sumOf { linha -> linha.count { it?.cor == CorPeca.ESCURA } }
}

enum class ResultadoDamas { VITORIA_CLARAS, VITORIA_ESCURAS, EMPATE }

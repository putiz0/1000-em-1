package com.playbox.offline.games.xadrez

enum class CorXadrez { BRANCAS, NEGRAS }

fun CorXadrez.oposta(): CorXadrez =
    if (this == CorXadrez.BRANCAS) CorXadrez.NEGRAS else CorXadrez.BRANCAS

enum class TipoPeca { PEAO, CAVALO, BISPO, TORRE, DAMA, REI }

data class PecaXadrez(val cor: CorXadrez, val tipo: TipoPeca)

data class Pos(val linha: Int, val coluna: Int)

enum class RoqueDireito { BRANCO_REI, BRANCO_DAMA, PRETO_REI, PRETO_DAMA }

/** Um lance. [capturada] é a peça no destino (ou o peão en passant). */
data class LanceXadrez(
    val origem: Pos,
    val destino: Pos,
    val capturada: PecaXadrez? = null,
    val promover: TipoPeca? = null,
    val enPassant: Boolean = false,
    val roque: RoqueDireito? = null,
)

data class EstadoXadrez(
    val casas: List<List<PecaXadrez?>>,
    val vez: CorXadrez,
    val roques: Set<RoqueDireito>,
    /** Casa atrás do peão que acabou de andar duas (destino do en passant). */
    val alvoEnPassant: Pos?,
    /** Relógio da regra dos 50 lances (meios-lances sem captura/movimento de peão). */
    val relogio: Int,
    val numeroLance: Int,
)

enum class ResultadoXadrez {
    VITORIA_BRANCAS, VITORIA_NEGRAS,
    EMPATE_PAT, EMPATE_50_LANCES, EMPATE_MATERIAL, EMPATE_REPETICAO,
}

val colunas = "abcdefgh"

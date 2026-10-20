package com.playbox.offline.games.imperio

import com.playbox.offline.core.packs.PackManager
import kotlinx.serialization.Serializable

/**
 * Modelos do Império Imobiliário — jogo de propriedades ORIGINAL do
 * PlayBox Offline (32 casas, 20 propriedades em grupos de cor, cartas
 * de Sorte e Imprevisto próprias, prisão, falência e construção).
 *
 * Todo o conteúdo do tabuleiro (nomes, preços, aluguéis, cartas) vem do
 * pacote instalado (board.json); o motor interpreta apenas as ações
 * declaradas em dados.
 */
enum class TipoCasa {
    PROPRIEDADE, SORTE, IMPREVISTO, IMPOSTO, PRISAO, VA_PARA_PRISAO, LIVRE, LARGADA
}

@Serializable
data class CasaBoard(
    val tipo: String,
    val nome: String,
    val grupo: String? = null,
    val preco: Int? = null,
    /** Aluguéis por nível: 0 = sem construção, 1..4 = casas, 5 = hotel. */
    val alugueis: List<Int>? = null,
    val custoCasa: Int? = null,
    val valor: Int? = null, // imposto
) {
    fun tipoCasa(): TipoCasa = when (tipo) {
        "propriedade" -> TipoCasa.PROPRIEDADE
        "sorte" -> TipoCasa.SORTE
        "imprevisto" -> TipoCasa.IMPREVISTO
        "imposto" -> TipoCasa.IMPOSTO
        "prisao" -> TipoCasa.PRISAO
        "va_para_prisao" -> TipoCasa.VA_PARA_PRISAO
        "livre" -> TipoCasa.LIVRE
        else -> TipoCasa.LARGADA
    }
}

/** Ação de carta declarada em dados; o motor só executa o que existe aqui. */
@Serializable
data class CartaBoard(
    val texto: String,
    val acao: String,
    val valor: Int? = null,
    val destino: Int? = null,
    val porCasa: Int? = null,
    val porHotel: Int? = null,
)

@Serializable
data class ImperioBoard(
    val casas: List<CasaBoard>,
    val cartasSorte: List<CartaBoard>,
    val cartasImprevisto: List<CartaBoard>,
    val salarioLargada: Int = 200,
    val fiancaPrisao: Int = 50,
    val caixaInicial: Int = 1500,
) {
    val tamanho: Int get() = casas.size

    /** Índice da casa de prisão (visita); requerida no board. */
    val indicePrisao: Int
        get() = casas.indexOfFirst { it.tipoCasa() == TipoCasa.PRISAO }

    fun propriedadesDoGrupo(grupo: String): List<Int> =
        casas.withIndex().filter { it.value.grupo == grupo }.map { it.index }

    companion object {
        /** Lê e valida o board.json do pacote instalado (null se ausente/inválido). */
        fun doPacote(texto: String?): ImperioBoard? {
            if (texto == null) return null
            return runCatching {
                val board = PackManager.json.decodeFromString<ImperioBoard>(texto)
                require(board.casas.isNotEmpty()) { "board sem casas" }
                require(board.indicePrisao >= 0) { "board precisa de casa de prisão" }
                board
            }.getOrNull()
        }
    }
}

/** Dono de uma propriedade e nível de construção (0..5; 5 = hotel). */
data class Dono(val assento: Int, val nivel: Int)

/** Pendências que aguardam decisão do jogador da vez. */
enum class Pendencia { COMPRAR, NADA }

/** Fase do turno. */
enum class FaseTurno {
    /** Ações pré-lance (rolar; preso: pagar fiança/cartão; construir). */
    ANTES_ROLAR,
    /** Resolução aberta (compra possível ou carta à mostra); encerra no OK. */
    RESOLVENDO,
    /** Partida encerrada. */
    FIM,
}

data class EstadoImperio(
    val board: ImperioBoard,
    val nomes: List<String>,
    /** O assento 1 é IA quando a partida é contra o app. */
    val contraApp: Boolean,
    val pos: List<Int>,
    val caixa: List<Int>,
    /** Turnos presos: -1 = livre; senão 0..3 tentativas de duplo. */
    val prisao: List<Int>,
    /** Cartas de "sair da prisão" guardadas (quantidade por assento). */
    val cartasSair: List<Int>,
    /** casa → dono/nível. */
    val donos: Map<Int, Dono>,
    val ordem: Int,
    val rodada: Int,
    /** Duplos seguidos do assento atual (3º duplo vai para a prisão). */
    val duplosSeguidos: Int = 0,
    /** Após resolver (OK), o MESMO assento rola de novo (lance duplo). */
    val novoLance: Boolean = false,
    val fase: FaseTurno = FaseTurno.ANTES_ROLAR,
    val pendencia: Pendencia = Pendencia.NADA,
    /** Casa da pendência de compra aberta. */
    val pendenciaCasa: Int? = null,
    /** Texto da carta à exibição (fase RESOLVENDO) e seu saco (Sorte/Imprevisto). */
    val cartaExibida: String? = null,
    val cartaTipo: String? = null,
    /** Histórico de eventos, mais recente por último. */
    val log: List<String> = emptyList(),
    val falidos: Set<Int> = emptySet(),
    val vencedor: Int? = null,
) {
    val assentoAtual: Int get() = ordem % nomes.size

    fun podeConstruir(casa: Int): Boolean {
        val casaBoard = board.casas[casa]
        if (casaBoard.tipoCasa() != TipoCasa.PROPRIEDADE) return false
        val dono = donos[casa] ?: return false
        if (dono.assento != assentoAtual) return false
        if (dono.nivel >= 5) return false
        val grupo = casaBoard.grupo ?: return false
        val doGrupo = board.propriedadesDoGrupo(grupo)
        return doGrupo.all { donos[it]?.assento == assentoAtual } &&
            caixa[assentoAtual] >= (casaBoard.custoCasa ?: 0)
    }

    fun assentosAtivos(): List<Int> = nomes.indices.filter { it !in falidos }
}

package com.playbox.offline.games.imperio

/**
 * IA simples do assento "App" (partidas contraApp, assento 1):
 *  - compra propriedade mantendo reserva (menor quando já tem outra do grupo);
 *  - constrói antes de rolar, do mais barato ao mais caro, mantendo reserva;
 *  - na prisão usa carta de saída, depois paga a fiança se o caixa estiver
 *    confortável (caso contrário, tenta sair com duplos);
 *  - fora disso apenas rola os dados e lê a resolução.
 */
class IAImperio {

    fun deveComprar(estado: EstadoImperio, casa: Int): Boolean {
        val assento = estado.assentoAtual
        val info = estado.board.casas[casa]
        val preco = info.preco ?: return false
        val caixa = estado.caixa[assento]
        val jaTemDoGrupo = info.grupo?.let { grupo ->
            estado.board.propriedadesDoGrupo(grupo)
                .any { it != casa && estado.donos[it]?.assento == assento }
        } ?: false
        // Já ter outra propriedade do mesmo grupo encarece menos a decisão:
        // contribui para o dobro do aluguel e para a construção.
        val reserva = if (jaTemDoGrupo) RESERVA_SEGUE_GRUPO else RESERVA_COMPRA
        return caixa >= preco + reserva
    }

    /** Constrói o máximo possível mantendo a reserva; devolve o estado novo. */
    fun construirMaximo(estado: EstadoImperio): EstadoImperio {
        var e = estado
        var progrediu = true
        while (progrediu) {
            progrediu = false
            val candidatas = e.board.casas.indices
                .filter { e.podeConstruir(it) }
                .sortedBy { e.board.casas[it].custoCasa ?: Int.MAX_VALUE }
            for (casa in candidatas) {
                val custo = e.board.casas[casa].custoCasa ?: continue
                if (e.caixa[e.assentoAtual] - custo >= RESERVA_OBRA) {
                    e = ImperioRegras.construir(e, casa)
                    progrediu = true
                }
            }
        }
        return e
    }

    fun querPagarFianca(estado: EstadoImperio): Boolean =
        estado.caixa[estado.assentoAtual] >= estado.board.fiancaPrisao + RESERVA_COMPRA

    companion object {
        const val RESERVA_COMPRA = 200
        const val RESERVA_SEGUE_GRUPO = 100
        const val RESERVA_OBRA = 150
    }
}

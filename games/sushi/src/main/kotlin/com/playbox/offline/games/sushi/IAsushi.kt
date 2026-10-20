package com.playbox.offline.games.sushi

/**
 * IA heurística do assento "App" (partidas contraApp, assento 1).
 *
 * Ordem de prioridade fixa (determinística, sem aleatoriedade):
 *  1. temaki se falta 1 para a meta;
 *  2. mochi se o prato tem quantidade ímpar (fecha o par);
 *  3. wasabi enquanto houver rolinho no prato sem wasabi;
 *  4. pudim (acumula para o bônus final);
 *  5. chá na rodada final (o prato está no auge);
 *  6. rolinho;
 *  7. mochi;
 *  8. temaki;
 *  9. niguiri;
 *  10. gengibre;
 *  11. primeira carta da mão (nada era prioridade).
 */
class IAsushi {

    fun escolher(estado: EstadoSushi, assento: Int): Int {
        val mao = estado.mao[assento]
        val prato = estado.prato[assento]
        val config = estado.config

        fun primeiro(id: String): Int = mao.indexOfFirst { it == id }

        val faltaTemaki = (config.temakiMeta - 1) - (prato[Cartas.TEMAKI] ?: 0)
        if (faltaTemaki <= 0) {
            val i = primeiro(Cartas.TEMAKI)
            if (i >= 0) return i
        }

        if ((prato[Cartas.MOCHI] ?: 0) % 2 == 1) {
            val i = primeiro(Cartas.MOCHI)
            if (i >= 0) return i
        }

        if ((prato[Cartas.WASABI] ?: 0) < (prato[Cartas.ROLINHO] ?: 0)) {
            val i = primeiro(Cartas.WASABI)
            if (i >= 0) return i
        }

        primeiro(Cartas.PUDIM).let { if (it >= 0) return it }

        if (estado.rodada == config.rodadas) {
            primeiro(Cartas.CHA).let { if (it >= 0) return it }
        }

        for (id in listOf(Cartas.ROLINHO, Cartas.MOCHI, Cartas.TEMAKI, Cartas.NIGUIRI, Cartas.GENGIBRE)) {
            val i = primeiro(id)
            if (i >= 0) return i
        }
        return 0
    }
}

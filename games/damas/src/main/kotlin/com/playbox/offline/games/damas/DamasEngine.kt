package com.playbox.offline.games.damas

/**
 * Regras das damas brasileiras em tabuleiro 8×8:
 *
 *  - 12 peças por lado, em casas escuras ((linha + coluna) % 2 == 1);
 *  - peão anda uma casa em diagonal para frente;
 *  - peão captura para frente E para trás, saltando peça inimiga
 *    adjacente e pousando na casa vazia imediatamente depois dela;
 *  - dama anda qualquer distância em diagonal (dama "voadora");
 *  - dama captura qualquer peça inimiga na diagonal, pousando em
 *    qualquer casa vazia depois dela;
 *  - captura é obrigatória (lei da captura) e, havendo mais de uma
 *    opção, vale a lei da maioria: capturar o maior número de peças;
 *  - a cadeia de captura continua enquanto houver captura possível
 *    pela mesma peça;
 *  - peão promove a dama ao terminar a cadeia na última linha;
 *  - vence quem captura ou bloqueia todas as peças adversárias;
 *  - empate após [LIMITE_SEM_CAPTURA] meios-lances consecutivos sem captura.
 */
object DamasRegras {

    const val TAMANHO = 8

    /** Limite padrão de meios-lances sem captura antes do empate. */
    const val LIMITE_SEM_CAPTURA = 60

    private data class Direcao(val linha: Int, val coluna: Int)

    private val DIAGONAIS = listOf(
        Direcao(-1, -1), Direcao(-1, 1), Direcao(1, -1), Direcao(1, 1)
    )

    private fun dentro(linha: Int, coluna: Int): Boolean =
        linha in 0 until TAMANHO && coluna in 0 until TAMANHO

    private fun casaEscura(casa: Casa): Boolean = (casa.linha + casa.coluna) % 2 == 1

    fun inicial(): EstadoDamas {
        val casas: List<MutableList<Peca?>> =
            List(TAMANHO) { MutableList<Peca?>(TAMANHO) { null } }
        for (l in 0 until 3) {
            for (c in 0 until TAMANHO) {
                if ((l + c) % 2 == 1) casas[l][c] = Peca(CorPeca.ESCURA)
            }
        }
        for (l in 5 until TAMANHO) {
            for (c in 0 until TAMANHO) {
                if ((l + c) % 2 == 1) casas[l][c] = Peca(CorPeca.CLARA)
            }
        }
        return EstadoDamas(casas, CorPeca.CLARA)
    }

    /**
     * Jogadas legais do lado da vez. Se existirem capturas, devolve
     * somente as capturas com o maior número de peças (lei da maioria).
     */
    fun jogadasLegais(estado: EstadoDamas): List<Jogada> {
        val capturas = todasCapturas(estado, estado.vez)
        if (capturas.isNotEmpty()) {
            val maximo = capturas.maxOf { it.capturas.size }
            return capturas.filter { it.capturas.size == maximo }
        }
        return movimentosSimples(estado, estado.vez)
    }

    private fun pecasDe(estado: EstadoDamas, cor: CorPeca): List<Pair<Casa, Peca>> {
        val resultado = mutableListOf<Pair<Casa, Peca>>()
        estado.casas.forEachIndexed { l, linha ->
            linha.forEachIndexed { c, peca ->
                if (peca != null && peca.cor == cor) resultado += Casa(l, c) to peca
            }
        }
        return resultado
    }

    private fun todasCapturas(estado: EstadoDamas, cor: CorPeca): List<Jogada> {
        val resultado = mutableListOf<Jogada>()
        for ((casa, peca) in pecasDe(estado, cor)) {
            val semOrigem = estado.casas.mapIndexed { l, linha ->
                linha.mapIndexed { c, p ->
                    if (l == casa.linha && c == casa.coluna) null else p
                }
            }
            resultado += explorarCapturas(semOrigem, cor, casa, peca.dama, emptySet(), casa)
        }
        return resultado
    }

    /**
     * Explora cadeias de captura. A peça móvel já foi removida de [origem]
     * no tabuleiro [casas]; peças já saltadas ([capturadas]) continuam no
     * tabuleiro como obstáculo e não podem ser saltadas de novo.
     */
    private fun explorarCapturas(
        casas: List<List<Peca?>>,
        cor: CorPeca,
        atual: Casa,
        dama: Boolean,
        capturadas: Set<Casa>,
        origem: Casa,
    ): List<Jogada> {
        val resultado = mutableListOf<Jogada>()
        for (d in DIAGONAIS) {
            val alvo = saltoPossivel(casas, cor, atual, d, dama, capturadas) ?: continue
            val novasCapturadas = capturadas + alvo.pecaCapturada
            for (desembarque in alvo.desembarques) {
                val filhos = explorarCapturas(casas, cor, desembarque, dama, novasCapturadas, origem)
                if (filhos.isEmpty()) {
                    resultado += Jogada(origem, desembarque, novasCapturadas.toList())
                } else {
                    resultado += filhos
                }
            }
        }
        return resultado
    }

    private class Salvo(val pecaCapturada: Casa, val desembarques: List<Casa>)

    private fun saltoPossivel(
        casas: List<List<Peca?>>,
        cor: CorPeca,
        atual: Casa,
        d: Direcao,
        dama: Boolean,
        capturadas: Set<Casa>,
    ): Salvo? {
        if (!dama) {
            val vítima = Casa(atual.linha + d.linha, atual.coluna + d.coluna)
            val pouso = Casa(atual.linha + 2 * d.linha, atual.coluna + 2 * d.coluna)
            if (!dentro(vítima.linha, vítima.coluna) || !dentro(pouso.linha, pouso.coluna)) return null
            val peca = casas[vítima.linha][vítima.coluna]
            if (peca == null || peca.cor == cor || Casa(vítima.linha, vítima.coluna) in capturadas) return null
            if (casas[pouso.linha][pouso.coluna] != null) return null
            return Salvo(vítima, listOf(pouso))
        }
        // Dama: percorre a diagonal até achar a primeira peça.
        var l = atual.linha + d.linha
        var c = atual.coluna + d.coluna
        while (dentro(l, c)) {
            val peca = casas[l][c]
            if (peca == null) {
                l += d.linha
                c += d.coluna
                continue
            }
            if (peca.cor == cor || Casa(l, c) in capturadas) return null
            // Primeira peça inimiga na diagonal: pousos são as casas vazias depois dela.
            val desembarques = mutableListOf<Casa>()
            var ll = l + d.linha
            var cc = c + d.coluna
            while (dentro(ll, cc) && casas[ll][cc] == null) {
                desembarques += Casa(ll, cc)
                ll += d.linha
                cc += d.coluna
            }
            if (desembarques.isEmpty()) return null
            return Salvo(Casa(l, c), desembarques)
        }
        return null
    }

    private fun movimentosSimples(estado: EstadoDamas, cor: CorPeca): List<Jogada> {
        val resultado = mutableListOf<Jogada>()
        for ((casa, peca) in pecasDe(estado, cor)) {
            if (peca.dama) {
                for (d in DIAGONAIS) {
                    var l = casa.linha + d.linha
                    var c = casa.coluna + d.coluna
                    while (dentro(l, c) && estado.casas[l][c] == null) {
                        resultado += Jogada(casa, Casa(l, c))
                        l += d.linha
                        c += d.coluna
                    }
                }
            } else {
                val avanco = if (cor == CorPeca.CLARA) -1 else 1
                for (dc in listOf(-1, 1)) {
                    val l = casa.linha + avanco
                    val c = casa.coluna + dc
                    if (dentro(l, c) && estado.casas[l][c] == null) {
                        resultado += Jogada(casa, Casa(l, c))
                    }
                }
            }
        }
        return resultado
    }

    private fun linhaDePromocao(cor: CorPeca): Int = if (cor == CorPeca.CLARA) 0 else TAMANHO - 1

    fun aplicar(estado: EstadoDamas, jogada: Jogada): EstadoDamas {
        require(casaEscura(jogada.origem) && casaEscura(jogada.destino)) {
            "movimento fora das casas escuras"
        }
        val casas: MutableList<MutableList<Peca?>> =
            estado.casas.map { it.toMutableList() }.toMutableList()
        val peca = casas[jogada.origem.linha][jogada.origem.coluna]
            ?: error("não há peça em (${jogada.origem.linha}, ${jogada.origem.coluna})")
        casas[jogada.origem.linha][jogada.origem.coluna] = null
        val promove = !peca.dama && jogada.destino.linha == linhaDePromocao(peca.cor)
        casas[jogada.destino.linha][jogada.destino.coluna] =
            if (promove) Peca(peca.cor, dama = true) else peca
        for (c in jogada.capturas) {
            casas[c.linha][c.coluna] = null
        }
        val semCaptura = if (jogada.isCaptura) 0 else estado.jogadasSemCaptura + 1
        return EstadoDamas(casas, estado.vez.oposta(), semCaptura)
    }

    /**
     * Resultado da partida, ou null se ela continua.
     * [limiteSemCaptura] usa [LIMITE_SEM_CAPTURA] quando omitido.
     */
    fun resultado(estado: EstadoDamas, limiteSemCaptura: Int = LIMITE_SEM_CAPTURA): ResultadoDamas? {
        if (estado.jogadasSemCaptura >= limiteSemCaptura) return ResultadoDamas.EMPATE
        if (jogadasLegais(estado).isEmpty()) {
            return when (estado.vez) {
                CorPeca.CLARA -> ResultadoDamas.VITORIA_ESCURAS
                CorPeca.ESCURA -> ResultadoDamas.VITORIA_CLARAS
            }
        }
        return null
    }
}

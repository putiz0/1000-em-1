package com.playbox.offline.games.xadrez

/**
 * Motor de xadrez com regras completas: todos os movimentos, roques
 * (parcial/grande), en passant, promoção, detecção de xeque, xeque-mate,
 * pat (afogamento), regra dos 50 lances e material insuficiente.
 *
 * O teste de perfeição (perft 3 = 8.902 lances) valida a geração completa
 * contra as tabelas padrão da prática.
 */
object XadrezRegras {

    private val CAVALO_PASSOS = listOf(
        2 to 1, 2 to -1, -2 to 1, -2 to -1,
        1 to 2, 1 to -2, -1 to 2, -1 to -2,
    )
    private val REI_PASSOS = listOf(
        1 to 0, -1 to 0, 0 to 1, 0 to -1,
        1 to 1, 1 to -1, -1 to 1, -1 to -1,
    )
    private val TORRE_DIRS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    private val BISPO_DIRS = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)

    private fun dentro(l: Int, c: Int): Boolean = l in 0..7 && c in 0..7

    fun notacao(pos: Pos): String =
        "${colunas[pos.coluna]}${8 - pos.linha}"

    fun inicial(): EstadoXadrez {
        val casas: List<MutableList<PecaXadrez?>> =
            List(8) { MutableList(8) { null } }
        // Brancas embaixo (linhas 6 e 7); negras em cima (linhas 0 e 1).
        for (c in 0..7) {
            casas[6][c] = PecaXadrez(CorXadrez.BRANCAS, TipoPeca.PEAO)
            casas[1][c] = PecaXadrez(CorXadrez.NEGRAS, TipoPeca.PEAO)
        }
        casas[7][0] = PecaXadrez(CorXadrez.BRANCAS, TipoPeca.TORRE)
        casas[7][1] = PecaXadrez(CorXadrez.BRANCAS, TipoPeca.CAVALO)
        casas[7][2] = PecaXadrez(CorXadrez.BRANCAS, TipoPeca.BISPO)
        casas[7][3] = PecaXadrez(CorXadrez.BRANCAS, TipoPeca.DAMA)
        casas[7][4] = PecaXadrez(CorXadrez.BRANCAS, TipoPeca.REI)
        casas[7][5] = PecaXadrez(CorXadrez.BRANCAS, TipoPeca.BISPO)
        casas[7][6] = PecaXadrez(CorXadrez.BRANCAS, TipoPeca.CAVALO)
        casas[7][7] = PecaXadrez(CorXadrez.BRANCAS, TipoPeca.TORRE)
        casas[0][0] = PecaXadrez(CorXadrez.NEGRAS, TipoPeca.TORRE)
        casas[0][1] = PecaXadrez(CorXadrez.NEGRAS, TipoPeca.CAVALO)
        casas[0][2] = PecaXadrez(CorXadrez.NEGRAS, TipoPeca.BISPO)
        casas[0][3] = PecaXadrez(CorXadrez.NEGRAS, TipoPeca.DAMA)
        casas[0][4] = PecaXadrez(CorXadrez.NEGRAS, TipoPeca.REI)
        casas[0][5] = PecaXadrez(CorXadrez.NEGRAS, TipoPeca.BISPO)
        casas[0][6] = PecaXadrez(CorXadrez.NEGRAS, TipoPeca.CAVALO)
        casas[0][7] = PecaXadrez(CorXadrez.NEGRAS, TipoPeca.TORRE)
        return EstadoXadrez(
            casas = casas,
            vez = CorXadrez.BRANCAS,
            roques = setOf(
                RoqueDireito.BRANCO_REI, RoqueDireito.BRANCO_DAMA,
                RoqueDireito.PRETO_REI, RoqueDireito.PRETO_DAMA,
            ),
            alvoEnPassant = null,
            relogio = 0,
            numeroLance = 1,
        )
    }

    /** O quadrado [alvo] está atacado por alguma peça da cor [por]? */
    fun isAtaque(casas: List<List<PecaXadrez?>>, alvo: Pos, por: CorXadrez): Boolean {
        // Peões: peão branco ataca para cima (linha menor).
        val dirPeao = if (por == CorXadrez.BRANCAS) 1 else -1
        for (dc in listOf(-1, 1)) {
            val l = alvo.linha + dirPeao
            val c = alvo.coluna + dc
            if (dentro(l, c)) {
                val p = casas[l][c]
                if (p != null && p.cor == por && p.tipo == TipoPeca.PEAO) return true
            }
        }
        // Cavalos
        for ((dl, dc) in CAVALO_PASSOS) {
            val l = alvo.linha + dl
            val c = alvo.coluna + dc
            if (dentro(l, c)) {
                val p = casas[l][c]
                if (p != null && p.cor == por && p.tipo == TipoPeca.CAVALO) return true
            }
        }
        // Rei adversário
        for ((dl, dc) in REI_PASSOS) {
            val l = alvo.linha + dl
            val c = alvo.coluna + dc
            if (dentro(l, c)) {
                val p = casas[l][c]
                if (p != null && p.cor == por && p.tipo == TipoPeca.REI) return true
            }
        }
        // Torres e damas (linhas e colunas)
        for ((dl, dc) in TORRE_DIRS) {
            var l = alvo.linha + dl
            var c = alvo.coluna + dc
            while (dentro(l, c)) {
                val p = casas[l][c]
                if (p != null) {
                    if (p.cor == por && (p.tipo == TipoPeca.TORRE || p.tipo == TipoPeca.DAMA)) return true
                    break
                }
                l += dl
                c += dc
            }
        }
        // Bispos e damas (diagonais)
        for ((dl, dc) in BISPO_DIRS) {
            var l = alvo.linha + dl
            var c = alvo.coluna + dc
            while (dentro(l, c)) {
                val p = casas[l][c]
                if (p != null) {
                    if (p.cor == por && (p.tipo == TipoPeca.BISPO || p.tipo == TipoPeca.DAMA)) return true
                    break
                }
                l += dl
                c += dc
            }
        }
        return false
    }

    fun posicaoRei(casas: List<List<PecaXadrez?>>, cor: CorXadrez): Pos? {
        for (l in 0..7) for (c in 0..7) {
            val p = casas[l][c]
            if (p != null && p.cor == cor && p.tipo == TipoPeca.REI) return Pos(l, c)
        }
        return null
    }

    fun inCheck(estado: EstadoXadrez, cor: CorXadrez): Boolean {
        val rei = posicaoRei(estado.casas, cor) ?: return false
        return isAtaque(estado.casas, rei, cor.oposta())
    }

    private fun lanceRoque(estado: EstadoXadrez, direito: RoqueDireito): LanceXadrez? {
        val vez = estado.vez
        val (linhaBase, ladoRei) = when (direito) {
            RoqueDireito.BRANCO_REI, RoqueDireito.PRETO_REI -> (if (vez == CorXadrez.BRANCAS) 7 else 0) to true
            RoqueDireito.BRANCO_DAMA, RoqueDireito.PRETO_DAMA -> (if (vez == CorXadrez.BRANCAS) 7 else 0) to false
        }
        if (!estado.roques.contains(direito)) return null
        val rei = estado.casas[linhaBase][4]
        if (rei == null || rei.tipo != TipoPeca.REI || rei.cor != vez) return null
        val colunaTorre = if (ladoRei) 7 else 0
        val torre = estado.casas[linhaBase][colunaTorre]
        if (torre == null || torre.tipo != TipoPeca.TORRE || torre.cor != vez) return null
        // O rei não pode estar em xeque nem atravessar casa atacada.
        if (isAtaque(estado.casas, Pos(linhaBase, 4), vez.oposta())) return null
        if (ladoRei) {
            for (c in listOf(5, 6)) {
                if (estado.casas[linhaBase][c] != null) return null
                if (isAtaque(estado.casas, Pos(linhaBase, c), vez.oposta())) return null
            }
            return LanceXadrez(
                origem = Pos(linhaBase, 4),
                destino = Pos(linhaBase, 6),
                roque = direito,
            )
        } else {
            for (c in listOf(1, 2, 3)) {
                if (estado.casas[linhaBase][c] != null) return null
            }
            for (c in listOf(3, 2)) {
                if (isAtaque(estado.casas, Pos(linhaBase, c), vez.oposta())) return null
            }
            return LanceXadrez(
                origem = Pos(linhaBase, 4),
                destino = Pos(linhaBase, 2),
                roque = direito,
            )
        }
    }

    private fun pseudoLances(estado: EstadoXadrez): List<LanceXadrez> {
        val vez = estado.vez
        val lances = mutableListOf<LanceXadrez>()
        for (l in 0..7) {
            for (c in 0..7) {
                val peca = estado.casas[l][c] ?: continue
                if (peca.cor != vez) continue
                when (peca.tipo) {
                    TipoPeca.PEAO -> {
                        val dir = if (vez == CorXadrez.BRANCAS) -1 else 1
                        val linhaInicial = if (vez == CorXadrez.BRANCAS) 6 else 1
                        val linhaFinal = if (vez == CorXadrez.BRANCAS) 0 else 7

                        val frente = l + dir
                        if (dentro(frente, c) && estado.casas[frente][c] == null) {
                            lances += lancesDeAvanco(Pos(l, c), Pos(frente, c), null, linhaFinal, vez)
                            if (l == linhaInicial) {
                                val duplo = l + 2 * dir
                                if (estado.casas[duplo][c] == null) {
                                    lances += LanceXadrez(Pos(l, c), Pos(duplo, c))
                                }
                            }
                        }
                        for (dc in listOf(-1, 1)) {
                            val cc = c + dc
                            if (!dentro(frente, cc)) continue
                            val alvo = estado.casas[frente][cc]
                            if (alvo != null && alvo.cor != vez) {
                                lances += lancesDeAvanco(Pos(l, c), Pos(frente, cc), alvo, linhaFinal, vez)
                            } else if (alvo == null && estado.alvoEnPassant == Pos(frente, cc)) {
                                lances += LanceXadrez(
                                    origem = Pos(l, c),
                                    destino = Pos(frente, cc),
                                    capturada = PecaXadrez(vez.oposta(), TipoPeca.PEAO),
                                    enPassant = true,
                                )
                            }
                        }
                    }
                    TipoPeca.CAVALO -> {
                        for ((dl, dc) in CAVALO_PASSOS) {
                            lanceSimples(estado, vez, Pos(l, c), l + dl, c + dc, lances)
                        }
                    }
                    TipoPeca.REI -> {
                        for ((dl, dc) in REI_PASSOS) {
                            lanceSimples(estado, vez, Pos(l, c), l + dl, c + dc, lances)
                        }
                    }
                    TipoPeca.BISPO -> {
                        lances += lancesDoDeslize(estado, vez, Pos(l, c), BISPO_DIRS)
                    }
                    TipoPeca.TORRE -> {
                        lances += lancesDoDeslize(estado, vez, Pos(l, c), TORRE_DIRS)
                    }
                    TipoPeca.DAMA -> {
                        lances += lancesDoDeslize(estado, vez, Pos(l, c), TORRE_DIRS + BISPO_DIRS)
                    }
                }
            }
        }
        // Roques
        RoqueDireito.entries
            .filter { direitoPossivel(vez, it) }
            .forEach { lanceRoque(estado, it)?.let { r -> lances += r } }
        return lances
    }

    private fun direitoPossivel(vez: CorXadrez, direito: RoqueDireito): Boolean = when (vez) {
        CorXadrez.BRANCAS -> direito == RoqueDireito.BRANCO_REI || direito == RoqueDireito.BRANCO_DAMA
        CorXadrez.NEGRAS -> direito == RoqueDireito.PRETO_REI || direito == RoqueDireito.PRETO_DAMA
    }

    private fun lancesDeAvanco(
        origem: Pos, destino: Pos, capturada: PecaXadrez?, linhaFinal: Int, vez: CorXadrez,
    ): List<LanceXadrez> =
        if (destino.linha == linhaFinal) {
            listOf(
                TipoPeca.DAMA, TipoPeca.TORRE, TipoPeca.BISPO, TipoPeca.CAVALO
            ).map { tipo ->
                LanceXadrez(origem, destino, capturada, promover = tipo)
            }
        } else {
            listOf(LanceXadrez(origem, destino, capturada))
        }

    private fun lanceSimples(
        estado: EstadoXadrez,
        vez: CorXadrez,
        origem: Pos,
        l: Int,
        c: Int,
        lances: MutableList<LanceXadrez>,
    ) {
        if (!dentro(l, c)) return
        val alvo = estado.casas[l][c]
        if (alvo == null) {
            lances += LanceXadrez(origem, Pos(l, c))
        } else if (alvo.cor != vez) {
            lances += LanceXadrez(origem, Pos(l, c), alvo)
        }
    }

    private fun lancesDoDeslize(
        estado: EstadoXadrez,
        vez: CorXadrez,
        origem: Pos,
        dirs: List<Pair<Int, Int>>,
    ): List<LanceXadrez> {
        val lances = mutableListOf<LanceXadrez>()
        for ((dl, dc) in dirs) {
            var l = origem.linha + dl
            var c = origem.coluna + dc
            while (dentro(l, c)) {
                val alvo = estado.casas[l][c]
                if (alvo == null) {
                    lances += LanceXadrez(origem, Pos(l, c))
                } else {
                    if (alvo.cor != vez) lances += LanceXadrez(origem, Pos(l, c), alvo)
                    break
                }
                l += dl
                c += dc
            }
        }
        return lances
    }

    fun jogadasLegais(estado: EstadoXadrez): List<LanceXadrez> {
        val legais = mutableListOf<LanceXadrez>()
        for (lance in pseudoLances(estado)) {
            val filho = aplicar(estado, lance)
            if (!inCheck(filho, estado.vez)) legais += lance
        }
        return legais
    }

    private val cantos = mapOf(
        Pos(7, 7) to RoqueDireito.BRANCO_REI,
        Pos(7, 0) to RoqueDireito.BRANCO_DAMA,
        Pos(0, 7) to RoqueDireito.PRETO_REI,
        Pos(0, 0) to RoqueDireito.PRETO_DAMA,
    )

    fun aplicar(estado: EstadoXadrez, lance: LanceXadrez): EstadoXadrez {
        val casas: MutableList<MutableList<PecaXadrez?>> =
            estado.casas.map { it.toMutableList() }.toMutableList()
        val peca = casas[lance.origem.linha][lance.origem.coluna]
            ?: error("não há peça em ${lance.origem}")

        casas[lance.origem.linha][lance.origem.coluna] = null
        if (lance.enPassant) {
            val linhaVitima = if (peca.cor == CorXadrez.BRANCAS) lance.destino.linha + 1 else lance.destino.linha - 1
            casas[linhaVitima][lance.destino.coluna] = null
        }
        casas[lance.destino.linha][lance.destino.coluna] =
            if (lance.promover != null) PecaXadrez(peca.cor, lance.promover) else peca

        when (lance.roque) {
            RoqueDireito.BRANCO_REI -> {
                casas[7][5] = casas[7][7]
                casas[7][7] = null
            }
            RoqueDireito.BRANCO_DAMA -> {
                casas[7][3] = casas[7][0]
                casas[7][0] = null
            }
            RoqueDireito.PRETO_REI -> {
                casas[0][5] = casas[0][7]
                casas[0][7] = null
            }
            RoqueDireito.PRETO_DAMA -> {
                casas[0][3] = casas[0][0]
                casas[0][0] = null
            }
            null -> Unit
        }

        val roques = estado.roques.toMutableSet()
        if (peca.tipo == TipoPeca.REI) {
            roques -= when (peca.cor) {
                CorXadrez.BRANCAS -> setOf(RoqueDireito.BRANCO_REI, RoqueDireito.BRANCO_DAMA)
                CorXadrez.NEGRAS -> setOf(RoqueDireito.PRETO_REI, RoqueDireito.PRETO_DAMA)
            }
        }
        if (peca.tipo == TipoPeca.TORRE) {
            roques -= setOf(cantos[lance.origem]).filterNotNull().toSet()
        }
        // Torre capturada num canto perde o direito do dono dela.
        if (lance.capturada != null && lance.capturada.tipo == TipoPeca.TORRE) {
            roques -= setOf(cantos[lance.destino]).filterNotNull().toSet()
        }

        val novoAlvo = if (peca.tipo == TipoPeca.PEAO &&
            kotlin.math.abs(lance.destino.linha - lance.origem.linha) == 2
        ) {
            Pos((lance.origem.linha + lance.destino.linha) / 2, lance.origem.coluna)
        } else {
            null
        }

        val relogio = if (peca.tipo == TipoPeca.PEAO || lance.capturada != null) 0 else estado.relogio + 1
        val numeroLance = if (estado.vez == CorXadrez.NEGRAS) estado.numeroLance + 1 else estado.numeroLance
        return EstadoXadrez(
            casas = casas,
            vez = estado.vez.oposta(),
            roques = roques,
            alvoEnPassant = novoAlvo,
            relogio = relogio,
            numeroLance = numeroLance,
        )
    }

    fun materialInsuficiente(estado: EstadoXadrez): Boolean {
        val sobras = mutableListOf<TipoPeca>()
        for (l in 0..7) for (c in 0..7) {
            val p = estado.casas[l][c] ?: continue
            if (p.tipo != TipoPeca.REI) sobras += p.tipo
        }
        if (sobras.isEmpty()) return true
        return sobras.size == 1 && (sobras[0] == TipoPeca.CAVALO || sobras[0] == TipoPeca.BISPO)
    }

    /** Resultado puro (sem repetição de posição, avaliada pela partida). */
    fun resultado(estado: EstadoXadrez): ResultadoXadrez? {
        val lances = jogadasLegais(estado)
        if (lances.isEmpty()) {
            return if (inCheck(estado, estado.vez)) {
                if (estado.vez == CorXadrez.BRANCAS) ResultadoXadrez.VITORIA_NEGRAS
                else ResultadoXadrez.VITORIA_BRANCAS
            } else {
                ResultadoXadrez.EMPATE_PAT
            }
        }
        if (estado.relogio >= 100) return ResultadoXadrez.EMPATE_50_LANCES
        if (materialInsuficiente(estado)) return ResultadoXadrez.EMPATE_MATERIAL
        return null
    }

    /** Texto do lance para o histórico ("e2→e4", "b2×c3", "O-O", "e7→e8 (promotion: dama)"). */
    fun textoLance(lance: LanceXadrez): String = when (lance.roque) {
        RoqueDireito.BRANCO_REI, RoqueDireito.PRETO_REI -> "Roque pequeno"
        RoqueDireito.BRANCO_DAMA, RoqueDireito.PRETO_DAMA -> "Roque grande"
        null -> buildString {
            append(notacao(lance.origem))
            if (lance.capturada != null) append('×') else append("→")
            append(notacao(lance.destino))
            when {
                lance.enPassant -> append(" (en passant)")
                lance.promover != null -> append(" (promoção: ${nomeTipo(lance.promover)})")
            }
        }
    }

    fun nomeTipo(tipo: TipoPeca): String = when (tipo) {
        TipoPeca.PEAO -> "peão"
        TipoPeca.CAVALO -> "cavalo"
        TipoPeca.BISPO -> "bispo"
        TipoPeca.TORRE -> "torre"
        TipoPeca.DAMA -> "dama"
        TipoPeca.REI -> "rei"
    }
}

package com.playbox.offline.games.imperio

import kotlin.random.Random

/**
 * Motor do Império Imobiliário.
 *
 * Fluxo de turno:
 *  ANTES_ROLAR → rolar() → RESOLVENDO → encerrarResolucao() → próximo assento.
 *  Com duplo no dado o MESMO assento volta a ANTES_ROLAR; no 3º duplo
 *  seguido vai direto para a prisão. Preso: tenta duplo por até 3 turnos,
 *  paga a fiança ou usa carta de saída (quando a rolagem é resolvida por
 *  fiança ou cartão, o movimento é normal e não vale novo lance por duplo).
 *
 * Cartas "vá até" avançam pela mesa (recebem salário ao cruzar a Largada)
 * e resolvem apenas a casa de destino, sem puxar outra carta.
 *
 * Falência: quem não cobre uma dívida liquida construções (reembolso =
 * metade do custo da casa) e, se ainda faltar, fica falido: o credor
 * recebe as propriedades (níveis zerados) e todo o caixa; dívida com o
 * banco devolve as propriedades ao tabuleiro. Restando um único assento,
 * ele vence.
 */
object ImperioRegras {

    const val NIVEL_HOTEL = 5
    const val MAX_TENTATIVAS_PRISAO = 3

    fun novoJogo(
        board: ImperioBoard,
        nomes: List<String>,
        contraApp: Boolean,
    ): EstadoImperio {
        require(nomes.size == 2) { "partida sempre de 2 assentos (humano × IA ou 2 humanos)" }
        require(board.indicePrisao >= 0) { "board precisa de casa de prisão" }
        return EstadoImperio(
            board = board,
            nomes = nomes,
            contraApp = contraApp,
            pos = nomes.map { 0 },
            caixa = nomes.map { board.caixaInicial },
            prisao = nomes.map { -1 },
            cartasSair = nomes.map { 0 },
            donos = emptyMap(),
            ordem = 0,
            rodada = 1,
            log = listOf("Partida começou: ${nomes[0]} × ${nomes[1]}. Caixa inicial ${board.caixaInicial}."),
        )
    }

    // ---------- rolagem ----------

    fun rolar(estado: EstadoImperio, aleatorio: Random = Random.Default): EstadoImperio {
        require(estado.fase == FaseTurno.ANTES_ROLAR) { "fora da fase de rolagem" }
        require(estado.vencedor == null) { "a partida já acabou" }
        val assento = estado.assentoAtual
        val d1 = aleatorio.nextInt(1, 7)
        val d2 = aleatorio.nextInt(1, 7)
        var e = estado.comLog("dados: $d1 + $d2")

        val turnoNaPrisao = e.prisao[assento]
        if (turnoNaPrisao >= 0) {
            if (d1 == d2) {
                e = e
                    .copy(prisao = e.prisao.comNo(assento, -1))
                    .comLog("duplo! ${estado.nomes[assento]} sai da prisão e anda.")
                return moverEResolver(e, d1 + d2, novoLancePorDuplo = false, aleatorio = aleatorio)
            }
            val tentativa = turnoNaPrisao + 1
            if (tentativa >= MAX_TENTATIVAS_PRISAO) {
                e = e.comLog("3 tentativas falharam: paga a fiança e anda.")
                    .copy(prisao = e.prisao.comNo(assento, -1))
                e = debitar(e, assento, estado.board.fiancaPrisao, credor = null)
                if (assento in e.falidos) {
                    // falência pela fiança: FIM do debitar tem prioridade
                    if (e.fase == FaseTurno.FIM) return e
                    return e.copy(fase = FaseTurno.RESOLVENDO, pendencia = Pendencia.NADA)
                }
                return moverEResolver(e, d1 + d2, novoLancePorDuplo = false, aleatorio = aleatorio)
            }
            return e
                .copy(prisao = e.prisao.comNo(assento, tentativa))
                .comLog("sem duplo na prisão (tentativa $tentativa). Fase finalizada no botão.")
                .copy(fase = FaseTurno.RESOLVENDO)
        }

        return moverEResolver(e, d1 + d2, novoLancePorDuplo = d1 == d2, aleatorio = aleatorio)
    }

    private fun moverEResolver(
        estado: EstadoImperio,
        passos: Int,
        novoLancePorDuplo: Boolean,
        aleatorio: Random,
    ): EstadoImperio {
        var e = estado.moverCore(passos)
        if (novoLancePorDuplo) {
            val seguidos = e.duplosSeguidos + 1
            e = e.copy(duplosSeguidos = seguidos)
            if (seguidos >= 3) {
                e = prender(e, "três duplos seguidos: direto para a prisão!")
                return e.copy(fase = FaseTurno.RESOLVENDO, pendencia = Pendencia.NADA)
            }
        } else {
            e = e.copy(duplosSeguidos = 0)
        }
        val resolvida = resolverCasa(e, novoLancePorDuplo = novoLancePorDuplo, puxarCarta = true, aleatorio = aleatorio)
        return if (novoLancePorDuplo && resolvida.fase != FaseTurno.FIM &&
            resolvida.prisao[resolvida.assentoAtual] < 0
        ) {
            // Duplo: depois de resolver (compra, carta, aluguel...), o MESMO
            // assento ainda rola de novo — marcado em novoLance, efetivado
            // pelo OK do jogador (evita perder compra pendente).
            resolvida.copy(novoLance = true)
        } else {
            resolvida
        }
    }

    /** Move e credita salário ao cruzar/parar na Largada; não resolve casa. */
    private fun EstadoImperio.moverCore(passos: Int): EstadoImperio {
        val assento = assentoAtual
        val antiga = pos[assento]
        val nova = antiga + passos
        var e = this
        if (nova >= board.tamanho) {
            e = e.comCaixa(assento, e.caixa[assento] + board.salarioLargada)
                .comLog("cruzou a Largada: + ${board.salarioLargada}.")
        }
        return e.copy(pos = e.pos.comNo(assento, nova % board.tamanho))
    }

    private fun prender(estado: EstadoImperio, motivo: String): EstadoImperio {
        val assento = estado.assentoAtual
        return estado
            .copy(
                pos = estado.pos.comNo(assento, estado.board.indicePrisao),
                prisao = estado.prisao.comNo(assento, 0),
                duplosSeguidos = 0,
            )
            .comLog("Prisão! $motivo")
    }

    // ---------- resolução de casa ----------

    private fun resolverCasa(
        estado: EstadoImperio,
        novoLancePorDuplo: Boolean,
        puxarCarta: Boolean,
        aleatorio: Random,
    ): EstadoImperio {
        val assento = estado.assentoAtual
        val indice = estado.pos[assento]
        val casa = estado.board.casas[indice]
        return when (casa.tipoCasa()) {
            TipoCasa.LARGADA -> estado.faseResolvida("parou na Largada da mesa.")
            TipoCasa.LIVRE -> estado.faseResolvida("descansa na casa livre.")
            TipoCasa.PRISAO -> estado.faseResolvida("de visita à prisão (não está preso).")
            TipoCasa.IMPOSTO -> {
                val valor = casa.valor ?: 0
                var e = estado.faseResolvida("Imposto \"${casa.nome}\": \u2212 $valor.")
                e = debitar(e, assento, valor, credor = null)
                // a falência pode encerrar a partida — não sobrescreve o FIM
                if (e.fase != FaseTurno.FIM) e = e.copy(fase = FaseTurno.RESOLVENDO)
                e
            }
            TipoCasa.VA_PARA_PRISAO -> prender(estado, "a casa \"${casa.nome}\" mandou você.").copy(fase = FaseTurno.RESOLVENDO)
            TipoCasa.PROPRIEDADE -> resolverPropriedade(estado, indice, casa, novoLancePorDuplo)
            TipoCasa.SORTE, TipoCasa.IMPREVISTO -> {
                if (puxarCarta) {
                    aplicarCarta(estado, casa.tipoCasa() == TipoCasa.SORTE, aleatorio)
                } else {
                    estado.faseResolvida("a carta trouxe até aqui; nada mais acontece nesta casa.")
                }
            }
        }
    }

    private fun resolverPropriedade(
        estado: EstadoImperio,
        indice: Int,
        casa: CasaBoard,
        novoLancePorDuplo: Boolean,
    ): EstadoImperio {
        val assento = estado.assentoAtual
        val dono = estado.donos[indice]
        return when {
            dono == null -> {
                val preco = casa.preco ?: 0
                if (estado.caixa[assento] >= preco) {
                    estado
                        .faseResolvida("${casa.nome} sem dono (preço $preco) — há opção de comprar.")
                        .copy(
                            fase = FaseTurno.RESOLVENDO,
                            pendencia = Pendencia.COMPRAR,
                            pendenciaCasa = indice,
                        )
                } else {
                    estado.faseResolvida("caixa insuficiente para comprar ${casa.nome}.")
                }
            }
            dono.assento == assento -> estado.faseResolvida("${casa.nome} é sua.")
            else -> {
                val donoNome = estado.nomes[dono.assento]
                val aluguel = aluguelDe(estado, indice)
                var e = estado.comLog("parou em ${casa.nome} de $donoNome: aluguel $aluguel.")
                e = debitar(e, assento, aluguel, credor = dono.assento)
                // a falência do pagador pode encerrar a partida — não sobrescreve o FIM
                if (e.fase != FaseTurno.FIM) e = e.copy(fase = FaseTurno.RESOLVENDO)
                e
            }
        }
    }

    /** Aluguel: tabela própria; grupo completo e sem construção dobram. */
    fun aluguelDe(estado: EstadoImperio, indice: Int): Int {
        val casa = estado.board.casas[indice]
        val dono = estado.donos[indice] ?: return 0
        val tabela = casa.alugueis.orEmpty()
        if (tabela.isEmpty()) return 0
        val nivel = dono.nivel.coerceIn(0, tabela.size - 1)
        val grupoCompleto = casa.grupo != null && estado.board.propriedadesDoGrupo(casa.grupo)
            .all { estado.donos[it]?.assento == dono.assento }
        val base = tabela[nivel]
        return if (nivel == 0 && grupoCompleto) base * 2 else base
    }

    // ---------- cartas ----------

    private fun aplicarCarta(estado: EstadoImperio, sorte: Boolean, aleatorio: Random): EstadoImperio {
        val board = estado.board
        val saco = if (sorte) board.cartasSorte else board.cartasImprevisto
        if (saco.isEmpty()) return estado.faseResolvida("cartas deste tipo ainda não entraram nesta mesa.")
        val carta = saco[aleatorio.nextInt(saco.size)]
        val tipo = if (sorte) "Sorte" else "Imprevisto"
        val assento = estado.assentoAtual
        var e = estado.comLog("$tipo: ${carta.texto}")
            .copy(fase = FaseTurno.RESOLVENDO, cartaTipo = tipo, cartaExibida = carta.texto)

        when (carta.acao) {
            "receber" -> e = e.comCaixa(assento, e.caixa[assento] + (carta.valor ?: 0))
            "pagar" -> e = debitar(e, assento, carta.valor ?: 0, credor = null)
            "pagar_por_construcao" -> {
                var casas = 0
                var hoteis = 0
                e.donos.values.filter { it.assento == assento }.forEach { dono ->
                    when {
                        dono.nivel >= NIVEL_HOTEL -> hoteis++
                        dono.nivel > 0 -> casas += dono.nivel
                    }
                }
                val total = casas * (carta.porCasa ?: 0) + hoteis * (carta.porHotel ?: 0)
                e = e.comLog("conta da carta: $casas casas e $hoteis hotéis = $total.")
                if (total > 0) e = debitar(e, assento, total, credor = null)
            }
            "mover" -> {
                val destino = (carta.destino ?: 0).coerceIn(0, board.tamanho - 1)
                val passos = (destino - e.pos[assento] + board.tamanho) % board.tamanho
                e = e.moverCore(passos)
                    .copy(duplosSeguidos = 0)
                    .comLog("avançou até ${board.casas[destino].nome}.")
                e = resolverCasa(e, novoLancePorDuplo = false, puxarCarta = false, aleatorio = aleatorio)
            }
            "prisao" -> e = prender(e, "carta $tipo.").copy(fase = FaseTurno.RESOLVENDO)
            "sair_da_prisao" -> e = e.copy(
                cartasSair = e.cartasSair.comNo(assento, e.cartasSair[assento] + 1)
            )
        }
        return e
    }

    // ---------- decisões ----------

    /** decide a compra aberta ([aceitar]) — mantém o turno em RESOLVENDO. */
    fun decidirCompra(estado: EstadoImperio, aceitar: Boolean): EstadoImperio {
        require(estado.fase == FaseTurno.RESOLVENDO && estado.pendencia == Pendencia.COMPRAR)
        val casa = estado.pendenciaCasa ?: error("compra sem casa")
        val assento = estado.assentoAtual
        val board = estado.board.casas[casa]
        val preco = board.preco ?: 0
        var e = estado
        if (aceitar) {
            require(estado.caixa[assento] >= preco)
            e = e
                .comCaixa(assento, estado.caixa[assento] - preco)
                .copy(donos = estado.donos + (casa to Dono(assento, 0)))
                .comLog("comprou ${board.nome} por $preco.")
        } else {
            e = e.comLog("não comprou ${board.nome}.")
        }
        return e.copy(pendencia = Pendencia.NADA, pendenciaCasa = null, fase = FaseTurno.RESOLVENDO)
    }

    fun construir(estado: EstadoImperio, casa: Int): EstadoImperio {
        require(estado.podeConstruir(casa)) { "construção inválida na casa $casa" }
        val assento = estado.assentoAtual
        val info = estado.board.casas[casa]
        val custo = info.custoCasa ?: 0
        val dono = estado.donos[casa]!!
        val texto = if (dono.nivel + 1 == NIVEL_HOTEL) "um hotel em ${info.nome}" else
            "uma casa em ${info.nome}"
        return estado
            .comCaixa(assento, estado.caixa[assento] - custo)
            .copy(donos = estado.donos + (casa to Dono(assento, dono.nivel + 1)))
            .comLog("construiu $texto (custo $custo).")
    }

    fun pagarFianca(estado: EstadoImperio): EstadoImperio {
        val assento = estado.assentoAtual
        require(estado.prisao[assento] >= 0) { "o assento não está preso" }
        require(estado.fase == FaseTurno.ANTES_ROLAR) { "fora da fase de rolagem" }
        var e = debitar(estado, assento, estado.board.fiancaPrisao, credor = null)
            .comLog("pagou a fiança.")
        if (assento in e.falidos) {
            // falência pela fiança: FIM do debitar tem prioridade
            if (e.fase == FaseTurno.FIM) return e
            return e.copy(fase = FaseTurno.RESOLVENDO)
        }
        return e.copy(prisao = e.prisao.comNo(assento, -1))
    }

    fun usarCartaSair(estado: EstadoImperio): EstadoImperio {
        val assento = estado.assentoAtual
        require(estado.prisao[assento] >= 0) { "o assento não está preso" }
        require(estado.cartasSair[assento] > 0) { "sem cartas de saída" }
        require(estado.fase == FaseTurno.ANTES_ROLAR) { "fora da fase de rolagem" }
        return estado
            .copy(
                cartasSair = estado.cartasSair.comNo(assento, estado.cartasSair[assento] - 1),
                prisao = estado.prisao.comNo(assento, -1),
            )
            .comLog("usou uma carta de saída da prisão.")
    }

    /** Fechamento: aplica o lance duplo pendente (mesmo assento rola de
     *  novo) ou passa ao próximo assento ativo, na fase ANTES_ROLAR. */
    fun encerrarResolucao(estado: EstadoImperio): EstadoImperio {
        require(estado.fase != FaseTurno.FIM) { "a partida acabou" }
        if (estado.novoLance && estado.assentoAtual !in estado.falidos) {
            return estado.copy(
                fase = FaseTurno.ANTES_ROLAR,
                novoLance = false,
                pendencia = Pendencia.NADA,
                pendenciaCasa = null,
                cartaExibida = null,
                cartaTipo = null,
            )
        }
        var n = estado.ordem
        do { n++ } while (n % estado.nomes.size in estado.falidos)
        return estado.copy(
            ordem = n,
            rodada = n / estado.nomes.size + 1,
            fase = FaseTurno.ANTES_ROLAR,
            novoLance = false,
            pendencia = Pendencia.NADA,
            pendenciaCasa = null,
            cartaExibida = null,
            cartaTipo = null,
            duplosSeguidos = 0,
        )
    }

    // ---------- falência / pagamento ----------

    private fun debitar(estado: EstadoImperio, de: Int, valor: Int, credor: Int?): EstadoImperio {
        if (valor <= 0) return estado
        var e = estado
        if (e.caixa[de] >= valor) {
            e = e.comCaixa(de, e.caixa[de] - valor)
            if (credor != null) e = e.comCaixa(credor, e.caixa[credor] + valor)
            return e
        }
        // Liquida construções (as mais baratas primeiro; reembolso = metade do custo).
        val doAssento = e.donos.filterValues { it.assento == de && it.nivel > 0 }
            .toList()
            .sortedBy { (casa, _) -> e.board.casas[casa].custoCasa ?: Int.MAX_VALUE }
        for ((casa, dono) in doAssento) {
            if (e.caixa[de] >= valor) break
            val custo = e.board.casas[casa].custoCasa ?: 0
            val reembolso = custo / 2
            e = e
                .copy(donos = e.donos + (casa to Dono(de, dono.nivel - 1)))
                .comCaixa(de, e.caixa[de] + reembolso)
                .comLog("liquida construção em ${e.board.casas[casa].nome} (+$reembolso).")
        }
        if (e.caixa[de] >= valor) {
            e = e.comCaixa(de, e.caixa[de] - valor)
            if (credor != null) e = e.comCaixa(credor, e.caixa[credor] + valor)
            return e
        }
        // Falência.
        val restante = e.caixa[de]
        var donos = e.donos
        val novos = mutableMapOf<Int, Dono>()
        donos.forEach { (casa, dono) ->
            when {
                dono.assento != de -> novos[casa] = dono
                credor != null -> novos[casa] = Dono(credor, 0)
                // dívida com o banco: a propriedade volta ao tabuleiro (sem dono)
            }
        }
        e = e.copy(donos = novos, falidos = e.falidos + de)
        e = e.comCaixa(de, 0)
        if (credor != null) {
            e = e.comCaixa(credor, e.caixa[credor] + restante)
        }
        e = e.comLog(
            if (credor == null) "falência! Propriedades ficam livres." else "falência! O credor recebe as propriedades."
        )
        val ativos = e.assentosAtivos()
        if (ativos.size <= 1) {
            e = e.copy(vencedor = ativos.firstOrNull(), fase = FaseTurno.FIM)
        }
        return e
    }

    // ---------- helpers ----------

    private fun EstadoImperio.comLog(msg: String): EstadoImperio =
        copy(log = (log + msg).takeLast(60))

    private fun EstadoImperio.comCaixa(assento: Int, valor: Int): EstadoImperio =
        copy(caixa = caixa.comNo(assento, valor))

    private fun EstadoImperio.faseResolvida(msg: String): EstadoImperio =
        comLog(msg).copy(fase = FaseTurno.RESOLVENDO, pendencia = Pendencia.NADA, pendenciaCasa = null)

    /** Lista imutável com o índice trocado. */
    private fun <T> List<T>.comNo(indice: Int, valor: T): List<T> =
        toMutableList().also { it[indice] = valor }.toList()
}

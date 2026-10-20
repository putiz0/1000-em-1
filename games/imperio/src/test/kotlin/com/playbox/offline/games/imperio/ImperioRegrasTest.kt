package com.playbox.offline.games.imperio

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testes do motor do Império Imobiliário com um board-fixture de 12 casas
 * e dados determinísticos (sem dependência de sorte).
 */
class ImperioRegrasTest {

    /** Random que devolve os valores informados nos dados (1..6) e respeita
     *  os limites nos outros sorteios do motor (sacos de cartas etc.). */
    private class DadosFixos(vararg valores: Int) : Random() {
        private val fila = valores.toList()
        private var i = 0
        override fun nextInt(from: Int, until: Int): Int {
            return if (from == 1 && until == 7) {
                val valor = fila[i % fila.size]
                i++
                valor
            } else {
                from // sorteio de carta: devolve o primeiro elemento do saco
            }
        }
        override fun nextBits(bitCount: Int): Int = 0
    }

    private val board = ImperioBoard(
        casas = listOf(
            CasaBoard(tipo = "largada", nome = "Largada"),
            CasaBoard(tipo = "propriedade", nome = "T1", grupo = "a", preco = 60,
                alugueis = listOf(2, 10, 30, 90, 160, 250), custoCasa = 50),
            CasaBoard(tipo = "propriedade", nome = "T2", grupo = "a", preco = 60,
                alugueis = listOf(4, 20, 60, 180, 320, 450), custoCasa = 50),
            CasaBoard(tipo = "sorte", nome = "Sorte"),
            CasaBoard(tipo = "imposto", nome = "Imposto Teste", valor = 250),
            CasaBoard(tipo = "propriedade", nome = "U", grupo = "b", preco = 100,
                alugueis = listOf(200, 800, 2200, 6000, 7500, 9000), custoCasa = 50),
            CasaBoard(tipo = "prisao", nome = "Prisão"),
            CasaBoard(tipo = "imprevisto", nome = "Imprevisto"),
            CasaBoard(tipo = "sorte", nome = "Sorte"),
            CasaBoard(tipo = "propriedade", nome = "V", grupo = "b", preco = 100,
                alugueis = listOf(200, 900, 2500, 6200, 7800, 9300), custoCasa = 50),
            CasaBoard(tipo = "va_para_prisao", nome = "Vá para a Prisão"),
            CasaBoard(tipo = "propriedade", nome = "W", grupo = "c", preco = 300,
                alugueis = listOf(30, 120, 340, 870, 1120, 1520), custoCasa = 100),
        ),
        cartasSorte = listOf(CartaBoard(texto = "Chuva de bênçãos: receba 25.", acao = "receber", valor = 25)),
        cartasImprevisto = listOf(CartaBoard(texto = "Convite surpresa: vá até a casa 3.", acao = "mover", destino = 3)),
        salarioLargada = 200,
        fiancaPrisao = 50,
        caixaInicial = 1500,
    )

    private fun novo(): EstadoImperio = ImperioRegras.novoJogo(board, listOf("A", "B"), contraApp = false)

    private fun rolando(estado: EstadoImperio, vararg osDados: Int): EstadoImperio =
        ImperioRegras.rolar(estado, DadosFixos(*osDados))

    @Test
    fun partidaComecaNaLargadaComCaixaCheiaEFaseDeRolagem() {
        val e = novo()
        assertEquals(0, e.pos[0])
        assertEquals(1500, e.caixa[0])
        assertEquals(FaseTurno.ANTES_ROLAR, e.fase)
        assertEquals(0, e.ordem)
        assertEquals(6, board.indicePrisao)
    }

    @Test
    fun aluguelSimplesSemGrupoCompleto() {
        // casa 2 (T2) é do assento 0; o grupo "a" está incompleto (falta a casa 1)
        val base = novo().copy(donos = mapOf(2 to Dono(assento = 0, nivel = 0)), ordem = 1)
        val e = rolando(base, 1, 1) // cai na casa 2 (T2)
        assertEquals(1496, e.caixa[1])
        assertEquals(1504, e.caixa[0])
        assertEquals(FaseTurno.RESOLVENDO, e.fase)
        assertTrue(e.novoLance) // duplo: lance extra após o OK
    }

    @Test
    fun aluguelDobradoComGrupoCompleto() {
        val base = novo().copy(
            donos = mapOf(1 to Dono(0, 0), 2 to Dono(0, 0)),
            ordem = 1,
        )
        val e = rolando(base, 1, 1)
        // aluguel tabela[0] de T2 = 4, dobrado = 8
        assertEquals(1492, e.caixa[1])
        assertEquals(1508, e.caixa[0])
    }

    @Test
    fun compraDePropriedadeSemDono() {
        var e = rolando(novo(), 2, 3) // cai na casa 5 (U)
        assertEquals(Pendencia.COMPRAR, e.pendencia)
        assertEquals(5, e.pendenciaCasa)
        assertEquals(FaseTurno.RESOLVENDO, e.fase)

        e = ImperioRegras.decidirCompra(e, aceitar = true)
        assertEquals(1400, e.caixa[0])
        assertEquals(0, e.donos[5]?.assento)
        assertEquals(Pendencia.NADA, e.pendencia)

        e = ImperioRegras.encerrarResolucao(e)
        assertEquals(1, e.ordem)
        assertEquals(FaseTurno.ANTES_ROLAR, e.fase)
    }

    @Test
    fun construcaoExigeGrupoCompletoEaumentaAluguel() {
        val incompleto = novo().copy(donos = mapOf(1 to Dono(0, 0)))
        assertFalse(incompleto.podeConstruir(1), "grupo incompleto não permite construção")

        val completo = novo().copy(donos = mapOf(1 to Dono(0, 0), 2 to Dono(0, 0)))
        assertTrue(completo.podeConstruir(1))

        val construido = ImperioRegras.construir(completo, casa = 1)
        assertEquals(1450, construido.caixa[0])
        assertEquals(1, construido.donos[1]?.nivel)
        // aluguel usa a tabela (coluna 1) — sem dobro quando há construção
        assertEquals(10, ImperioRegras.aluguelDe(construido, 1))

        // caixa insuficiente trava a obra
        val semCaixa = construido.copy(caixa = construido.caixa.comNo(0, 10))
        assertFalse(semCaixa.podeConstruir(1))
    }

    @Test
    fun hotelUsaUltimaColunaDaTabela() {
        val e = novo().copy(donos = mapOf(5 to Dono(0, 5)))
        assertEquals(9000, ImperioRegras.aluguelDe(e, 5))
    }

    @Test
    fun vaParaPrisaoPrendeEPresosTentamDuplos() {
        var e = rolando(novo(), 3, 7) // cai na casa 10 (Vá para a Prisão)
        assertEquals(6, e.pos[0])
        assertEquals(0, e.prisao[0])
        assertEquals(FaseTurno.RESOLVENDO, e.fase)
        assertFalse(e.novoLance) // preso via casa não ganha lance extra do duplo (aqui não foi duplo)

        // preso tenta sem duplo: conta tentativa e não anda (continua na cela)
        val preso = novo().copy(prisao = listOf(0, -1), pos = listOf(6, 0))
        e = rolando(preso, 1, 2)
        assertEquals(1, e.prisao[0])
        assertEquals(6, e.pos[0])
        assertEquals(FaseTurno.RESOLVENDO, e.fase)
    }

    @Test
    fun presoSaiComDuploEPagaFiancaQuandoQuiser() {
        // duplo na prisão: sai e anda (o preso está na cela, pos 6); 6+6 cruza a Largada
        var e = rolando(novo().copy(prisao = listOf(0, -1), pos = listOf(6, 0)), 3, 3)
        assertEquals(-1, e.prisao[0])
        assertEquals(0, e.pos[0])
        assertEquals(1700, e.caixa[0])
        assertFalse(e.novoLance)

        // fiança voluntária: caixa 1500 - 50, e continua na fase de rolagem
        val base = novo().copy(prisao = listOf(0, -1))
        e = ImperioRegras.pagarFianca(base)
        assertEquals(1450, e.caixa[0])
        assertEquals(-1, e.prisao[0])
        assertEquals(FaseTurno.ANTES_ROLAR, e.fase)
    }

    @Test
    fun cartaDeSorteCreditaEMostraTexto() {
        val e = rolando(novo(), 1, 2) // cai na casa 3 (Sorte)
        assertEquals(1525, e.caixa[0])
        assertEquals("Sorte", e.cartaTipo)
        assertEquals("Chuva de bênçãos: receba 25.", e.cartaExibida)
        assertEquals(FaseTurno.RESOLVENDO, e.fase)
    }

    @Test
    fun cartaDeMoverResolveDestinoSemReencadearCarta() {
        val e = rolando(novo(), 1, 6) // cai na casa 7 (Imprevisto)
        // a carta manda até a casa 3 (Sorte), que NÃO puxa outra carta
        // (7 → 3: avança pela mesa e cruza a Largada: salário + 200)
        assertEquals(3, e.pos[0])
        assertEquals(1700, e.caixa[0])
        assertEquals("Imprevisto", e.cartaTipo)
        assertEquals("Convite surpresa: vá até a casa 3.", e.cartaExibida)
        assertTrue(e.log.none { it.startsWith("Sorte: ") })
    }

    @Test
    fun impostoMaiorQueCaixaFazFalenciaEoBancoFicaComTudo() {
        val base = novo().copy(caixa = listOf(100, 1500))
        val e = rolando(base, 1, 3) // casa 4 (Imposto 250), caixa 100 < 250
        assertTrue(0 in e.falidos)
        assertEquals(1, e.vencedor)
        assertEquals(FaseTurno.FIM, e.fase)
        assertTrue(e.donos.isEmpty(), "sem credor: propriedades voltam livres")
    }

    @Test
    fun falenciaComCredorTransferePropriedadesECaixa() {
        val base = novo().copy(
            ordem = 1,
            caixa = listOf(1500, 100),
            pos = listOf(0, 7),
            donos = mapOf(5 to Dono(1, 0), 9 to Dono(0, 0)),
        )
        val e = rolando(base, 1, 1) // cae na casa 9 (V), aluguel 200 > caixa 100
        assertTrue(1 in e.falidos)
        assertEquals(0, e.vencedor)
        assertEquals(FaseTurno.FIM, e.fase)
        assertEquals(1600, e.caixa[0]) // credor recebe o caixa restante do falido
        assertEquals(0, e.donos[5]?.assento) // propriedade do falido vai ao credor
        assertEquals(0, e.donos[5]?.nivel)
    }

    @Test
    fun duploMantemMesmoAssentoAteTerminarOExtra() {
        var e = rolando(novo(), 2, 2) // casa 4 (imposto 250), duplo
        assertTrue(e.novoLance)
        e = ImperioRegras.encerrarResolucao(e)
        assertEquals(0, e.ordem) // ainda é a vez do mesmo assento
        assertEquals(FaseTurno.ANTES_ROLAR, e.fase)

        // rolagem extra SEM duplo encerra normal para o próximo assento
        e = rolando(e, 1, 2) // casa 3 (Sorte: +25)
        assertFalse(e.novoLance)
        e = ImperioRegras.encerrarResolucao(e)
        assertEquals(1, e.ordem)
        assertEquals(1, e.rodada)
    }

    @Test
    fun encerramentoSaltaAssentoFalido() {
        val e = ImperioRegras.encerrarResolucao(novo().copy(falidos = setOf(1)))
        assertEquals(2, e.ordem)
        assertEquals(2, e.rodada)
    }

    @Test
    fun iaCompraComReservaEConstruiMantendoReserva() {
        // compra: 500 >= 100 + 200 → sim; 250 < 300 → não
        val rico = novo().copy(ordem = 1, caixa = listOf(1500, 500))
        assertTrue(IAImperio().deveComprar(rico, casa = 5))
        val pobre = rico.copy(caixa = listOf(1500, 250))
        assertFalse(IAImperio().deveComprar(pobre, casa = 5))

        // já tem outra do mesmo grupo: reserva menor vale a compra
        val doGrupo = rico.copy(donos = mapOf(9 to Dono(1, 0)))
        assertTrue(IAImperio().deveComprar(doGrupo, casa = 5))

        // construção: grupo a completo do assento 1, caixa 500 → builds mantendo caixa >= 150:
        // 7 construções (T1 sobe a 4, T2 sobe a 3)
        val base = novo().copy(
            ordem = 1,
            caixa = listOf(1500, 500),
            donos = mapOf(1 to Dono(1, 0), 2 to Dono(1, 0)),
        )
        val e = IAImperio().construirMaximo(base)
        assertEquals(4, e.donos[1]?.nivel)
        assertEquals(3, e.donos[2]?.nivel)
        assertEquals(150, e.caixa[1])
    }

    /** helper igual ao do motor (teste usa cópia local para estados de teste). */
    private fun <T> List<T>.comNo(indice: Int, valor: T): List<T> =
        toMutableList().also { it[indice] = valor }.toList()
}

package com.playbox.offline.games.imperio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.playbox.offline.core.designsystem.PlayBoxIcon
import com.playbox.offline.core.designsystem.PlayBoxIconPainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Porta do motor para a tela (padrão dos outros jogos: holder simples). */
class ImperioPartida(val board: ImperioBoard, val contraApp: Boolean) {

    private val nomes = if (contraApp) listOf("Você", "App") else listOf("Jogador 1", "Jogador 2")
    private val _estado = MutableStateFlow(ImperioRegras.novoJogo(board, nomes, contraApp))
    val estado: StateFlow<EstadoImperio> = _estado.asStateFlow()

    /** Incrementa a cada mudança real de estado (chave dos efeitos da IA). */
    private val _versao = MutableStateFlow(0)
    val versao: StateFlow<Int> = _versao.asStateFlow()

    private fun mudar(acao: (EstadoImperio) -> EstadoImperio) {
        val atual = _estado.value
        val novo = acao(atual)
        if (novo != atual) {
            _estado.value = novo
            _versao.value = _versao.value + 1
        }
    }

    fun rolar() = mudar { ImperioRegras.rolar(it) }
    fun comprar() = mudar { ImperioRegras.decidirCompra(it, true) }
    fun naoComprar() = mudar { ImperioRegras.decidirCompra(it, false) }
    fun construir(casa: Int) = mudar { ImperioRegras.construir(it, casa) }
    fun pagarFianca() = mudar { ImperioRegras.pagarFianca(it) }
    fun usarCartaSair() = mudar { ImperioRegras.usarCartaSair(it) }
    fun encerrar() = mudar { ImperioRegras.encerrarResolucao(it) }

    /** Turno do App: constrói o máximo possível antes de rolar. */
    fun construirIA(ia: IAImperio) = mudar { ia.construirMaximo(it) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImperioScreen(board: ImperioBoard, onSair: () -> Unit) {
    var contraApp by remember { mutableStateOf<Boolean?>(null) }
    if (contraApp == null) {
        AlertDialog(
            onDismissRequest = onSair,
            title = { Text("Império Imobiliário") },
            text = { Text("Partida de 2 assentos: você contra o App (que age sozinho) ou 2 humanos no mesmo aparelho.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { contraApp = true }) { Text("Contra o App") }
                    TextButton(onClick = { contraApp = false }) { Text("2 jogadores") }
                }
            },
        )
        return
    }

    var tokenReinicio by remember { mutableIntStateOf(0) }
    val ia = remember(contraApp) { IAImperio() }
    val partida = remember(contraApp, tokenReinicio) { ImperioPartida(board, contraApp == true) }
    val estado by partida.estado.collectAsState()
    val toc by partida.versao.collectAsState()
    val vezHumana = !estado.contraApp || estado.assentoAtual == 0

    var mostrarObras by remember { mutableStateOf(false) }
    var casaSelecionada by remember { mutableIntStateOf(-1) }

    // Turno automático do App.
    LaunchedEffect(toc, estado.ordem, estado.fase, estado.vencedor) {
        if (!estado.contraApp || estado.assentoAtual != 1 || estado.vencedor != null) return@LaunchedEffect
        delay(600)
        when (estado.fase) {
            FaseTurno.ANTES_ROLAR -> {
                if (estado.prisao[1] >= 0) {
                    when {
                        estado.cartasSair[1] > 0 -> partida.usarCartaSair()
                        ia.querPagarFianca(estado) -> partida.pagarFianca()
                        else -> partida.rolar()
                    }
                } else {
                    partida.construirIA(ia)
                    delay(500)
                    partida.rolar()
                }
            }
            FaseTurno.RESOLVENDO -> {
                val casaCompra = estado.pendenciaCasa
                if (estado.pendencia == Pendencia.COMPRAR && casaCompra != null) {
                    if (ia.deveComprar(estado, casaCompra)) partida.comprar() else partida.naoComprar()
                    delay(300)
                    partida.encerrar()
                } else {
                    delay(1100)
                    partida.encerrar()
                }
            }
            FaseTurno.FIM -> Unit
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Império Imobiliário") },
            navigationIcon = {
                IconButton(onClick = onSair) {
                    Icon(
                        painter = PlayBoxIconPainter(PlayBoxIcon.BACK),
                        contentDescription = "Sair da partida"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Rodada ${estado.rodada} — vez de: ${estado.nomes[estado.assentoAtual]}" +
                    if (!vezHumana) " (turno automático)" else "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(8.dp))

            // ---- caixas dos jogadores ----
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                estado.nomes.indices.forEach { i -> FichaJogador(estado, i, Modifier.weight(1f)) }
            }

            Spacer(Modifier.height(8.dp))

            // ---- tabuleiro 4×8 ----
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                (0 until 4).forEach { linha ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        (0 until 8).forEach { coluna ->
                            val casa = linha * 8 + coluna
                            CasaTile(
                                estado = estado,
                                casa = casa,
                                selecionada = casaSelecionada == casa,
                                aoClicar = { casaSelecionada = casa }
                            )
                        }
                    }
                }
            }
            Text(
                text = "Toque numa casa para ver os detalhes; bordas coloridas mostram donos, bolinhas mostram onde cada jogador está.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(10.dp))

            // ---- painel da casa ----
            val casaPainel =
                if (casaSelecionada in 0 until board.tamanho) casaSelecionada else estado.pos[estado.assentoAtual]
            PainelCasa(estado, casaPainel)

            Spacer(Modifier.height(10.dp))

            // ---- ações ----
            if (estado.vencedor == null) {
                if (estado.fase == FaseTurno.ANTES_ROLAR && vezHumana) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { partida.rolar() }) {
                            Text(if (estado.prisao[estado.assentoAtual] >= 0) "Rolar (tentar duplo)" else "Rolar dados")
                        }
                        OutlinedButton(onClick = { mostrarObras = true }) { Text("Construir") }
                    }
                    if (estado.prisao[estado.assentoAtual] >= 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            OutlinedButton(onClick = { partida.pagarFianca() }) {
                                Text("Pagar fiança (${estado.board.fiancaPrisao})")
                            }
                            OutlinedButton(
                                onClick = { partida.usarCartaSair() },
                                enabled = estado.cartasSair[estado.assentoAtual] > 0
                            ) {
                                Text("Usar carta (${estado.cartasSair[estado.assentoAtual]})")
                            }
                        }
                    }
                } else if (estado.fase == FaseTurno.RESOLVENDO && vezHumana) {
                    if (estado.pendencia == Pendencia.COMPRAR) {
                        val casaCompra = estado.pendenciaCasa!!
                        val info = estado.board.casas[casaCompra]
                        Text(
                            "${info.nome} está à venda por ${info.preco}.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            Button(onClick = { partida.comprar(); partida.encerrar() }) {
                                Text("Comprar (${info.preco})")
                            }
                            OutlinedButton(onClick = { partida.naoComprar(); partida.encerrar() }) {
                                Text("Não comprar")
                            }
                        }
                    } else {
                        Button(onClick = { partida.encerrar() }) {
                            Text(if (estado.novoLance) "Avançar (rola de novo: duplo)" else "Avançar")
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---- log ----
            Text(
                text = "O que aconteceu",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            estado.log.takeLast(8).forEach { msg ->
                Text(
                    text = "• $msg",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { tokenReinicio++ }) { Text("Reiniciar") }
            Spacer(Modifier.height(16.dp))
        }
    }

    // ---- diálogo de obras ----
    if (mostrarObras) {
        AlertDialog(
            onDismissRequest = { mostrarObras = false },
            title = { Text("Construir") },
            text = {
                val obras = estado.board.casas.indices.filter { estado.podeConstruir(it) }
                if (obras.isEmpty()) {
                    Text("Nenhuma obra possível agora: exige o grupo completo, caixa suficiente e nível abaixo do hotel.")
                } else {
                    Column {
                        obras.forEach { casa ->
                            val info = estado.board.casas[casa]
                            val proximo = estado.donos[casa]!!.nivel + 1
                            TextButton(onClick = { partida.construir(casa) }) {
                                Text(
                                    if (proximo == ImperioRegras.NIVEL_HOTEL)
                                        "Hotel em ${info.nome} — ${info.custoCasa}"
                                    else
                                        "Casa ${proximo}ª em ${info.nome} — ${info.custoCasa}"
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { mostrarObras = false }) { Text("Fechar") }
            },
        )
    }

    // ---- fim da partida ----
    if (estado.vencedor != null) {
        val vencedor = estado.vencedor!!
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Fim da partida") },
            text = {
                Column {
                    Text(
                        "🏆 ${estado.nomes[vencedor]} venceu (último jogador não falido!).",
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    estado.assentosAtivos().plus(estado.falidos).sorted().forEach { i ->
                        val props = estado.donos.filterValues { it.assento == i }.size
                        Text(
                            "${estado.nomes[i]}: caixa ${estado.caixa[i]} · $props propriedades" +
                                if (i in estado.falidos) " (falido)" else "",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = onSair) { Text("Sair") } },
        )
    }
}

@Composable
private fun FichaJogador(estado: EstadoImperio, assento: Int, modifier: Modifier = Modifier) {
    val preso = estado.prisao[assento] >= 0
    Card(modifier = modifier) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(corDoAssento(assento), CircleShape)
                )
                Text(
                    " ${estado.nomes[assento]}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            val casaAtual = estado.board.casas[estado.pos[assento]].nome
            Text(
                "Caixa ${estado.caixa[assento]} · está em $casaAtual",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                listOf(
                    if (preso) "🔒 preso (tentativa ${estado.prisao[assento] + 1})" else null,
                    if (estado.cartasSair[assento] > 0) "🎟 ${estado.cartasSair[assento]} carta(s) de saída" else null,
                    "🏠 ${estado.donos.values.count { it.assento == assento }} propriedade(s)"
                ).filterNotNull().joinToString(" · ").ifEmpty { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = if (preso) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CasaTile(estado: EstadoImperio, casa: Int, selecionada: Boolean, aoClicar: () -> Unit) {
    val info = estado.board.casas[casa]
    val tipo = info.tipoCasa()
    val dono = estado.donos[casa]
    val bordaEspessura: androidx.compose.ui.unit.Dp
    val bordaCor: Color
    when {
        selecionada -> { bordaEspessura = 3.dp; bordaCor = MaterialTheme.colorScheme.primary }
        dono != null -> { bordaEspessura = 2.dp; bordaCor = corDoAssento(dono.assento) }
        else -> { bordaEspessura = 0.5.dp; bordaCor = Color(0x66000000) }
    }
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(corDaCasa(tipo, info.grupo))
            .border(bordaEspessura, bordaCor, RoundedCornerShape(5.dp))
            .clickable { aoClicar() }
    ) {
        if (tipo == TipoCasa.PROPRIEDADE) {
            val nivel = dono?.nivel ?: 0
            if (nivel >= ImperioRegras.NIVEL_HOTEL) {
                Text(
                    "H",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            } else if (nivel > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 3.dp)
                ) {
                    repeat(nivel) {
                        Box(
                            Modifier
                                .size(5.dp)
                                .background(Color(0xFF2E2E2E), CircleShape)
                        )
                    }
                }
            }
        } else {
            Text(
                glifoDaCasa(tipo),
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp)
        ) {
            estado.pos.forEachIndexed { i, p ->
                if (p == casa && i !in estado.falidos) {
                    Box(
                        Modifier
                            .padding(horizontal = 1.dp)
                            .size(9.dp)
                            .background(corDoAssento(i), CircleShape)
                            .border(0.5.dp, Color.White, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun PainelCasa(estado: EstadoImperio, casa: Int) {
    val info = estado.board.casas[casa]
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text(info.nome, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            when (info.tipoCasa()) {
                TipoCasa.PROPRIEDADE -> {
                    Text(
                        "Grupo ${info.grupo?.uppercase()} · preço ${info.preco} · custo de casa ${info.custoCasa}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val dono = estado.donos[casa]
                    if (dono == null) {
                        Text("Sem dono.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(
                            "Dono: ${estado.nomes[dono.assento]} · " +
                                if (dono.nivel == ImperioRegras.NIVEL_HOTEL) "hotel construído" else
                                    "${dono.nivel} casa(s)",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Aluguel atual: ${ImperioRegras.aluguelDe(estado, casa)}" +
                                " (grupo completo sem construção dobra)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        "Tabela: ${info.alugueis?.joinToString(" · ") ?: "—"} (sem casa · 1 a 4 casas · hotel)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TipoCasa.IMPOSTO -> Text(
                    "Cobrança de ${info.valor} ao parar aqui.",
                    style = MaterialTheme.typography.bodySmall
                )
                TipoCasa.PRISAO -> Text(
                    "Prisão: quem manda aqui paga visita; presos tentam duplos para sair.",
                    style = MaterialTheme.typography.bodySmall
                )
                TipoCasa.VA_PARA_PRISAO -> Text(
                    "Parar aqui manda direto para a prisão (sem salário).",
                    style = MaterialTheme.typography.bodySmall
                )
                TipoCasa.SORTE -> Text(
                    "Puxa uma carta de Sorte (${estado.board.cartasSorte.size} no saco).",
                    style = MaterialTheme.typography.bodySmall
                )
                TipoCasa.IMPREVISTO -> Text(
                    "Puxa uma carta de Imprevisto (${estado.board.cartasImprevisto.size} no saco).",
                    style = MaterialTheme.typography.bodySmall
                )
                TipoCasa.LARGADA -> Text(
                    "Largada: salário de ${estado.board.salarioLargada} ao cruzar ou parar aqui.",
                    style = MaterialTheme.typography.bodySmall
                )
                TipoCasa.LIVRE -> Text(
                    "Casa livre: nada acontece.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (estado.fase == FaseTurno.RESOLVENDO && estado.cartaExibida != null) {
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Column {
                        Text(
                            estado.cartaTipo ?: "Carta",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(estado.cartaExibida ?: "", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun glifoDaCasa(tipo: TipoCasa): String = when (tipo) {
    TipoCasa.LARGADA -> "🚦"
    TipoCasa.PRISAO -> "🔒"
    TipoCasa.VA_PARA_PRISAO -> "🚓"
    TipoCasa.SORTE -> "✨"
    TipoCasa.IMPREVISTO -> "⚡"
    TipoCasa.IMPOSTO -> "💸"
    TipoCasa.LIVRE -> "🌳"
    TipoCasa.PROPRIEDADE -> "🏠"
}

private fun corDaCasa(tipo: TipoCasa, grupo: String?): Color = when (tipo) {
    TipoCasa.PROPRIEDADE -> CORES_GRUPO[grupo] ?: Color(0xFFFFF59D)
    TipoCasa.SORTE -> Color(0xFFF9E8A5)
    TipoCasa.IMPREVISTO -> Color(0xFFE8DECF)
    TipoCasa.IMPOSTO -> Color(0xFFB0BEC5)
    TipoCasa.PRISAO -> Color(0xFFBDBDBD)
    TipoCasa.VA_PARA_PRISAO -> Color(0xFFCE93D8)
    TipoCasa.LIVRE -> Color(0xFFA5D6A7)
    TipoCasa.LARGADA -> Color(0xFF9E9E9E)
}

private val CORES_GRUPO = mapOf(
    "a" to Color(0xFFF6B26B),
    "b" to Color(0xFFEA9999),
    "c" to Color(0xFF9FC5E8),
    "d" to Color(0xFFB6D7A8),
    "e" to Color(0xFFD5A6BD),
    "f" to Color(0xFFFFE599),
    "g" to Color(0xFF76D7C4),
    "h" to Color(0xFFA2C4C9),
)

private fun corDoAssento(assento: Int): Color = if (assento == 0) Color(0xFFD84315) else Color(0xFF1565C0)

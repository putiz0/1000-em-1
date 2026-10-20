package com.playbox.offline.games.xadrez

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.playbox.offline.core.designsystem.PlayBoxIcon
import com.playbox.offline.core.designsystem.PlayBoxIconPainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Partida de xadrez em memória, com detecção de repetição de posição. */
class XadrezPartida(val contraApp: Boolean, val profundidadeIA: Int) {
    private val _estado = MutableStateFlow(XadrezRegras.inicial())
    val estado: StateFlow<EstadoXadrez> = _estado.asStateFlow()
    private val _historico = MutableStateFlow<List<String>>(emptyList())
    val historico: StateFlow<List<String>> = _historico.asStateFlow()
    private val repeticoes = HashMap<String, Int>()

    init {
        contarRepeticao(_estado.value)
    }

    private fun letraPeca(peca: PecaXadrez): Char {
        val base = when (peca.tipo) {
            TipoPeca.PEAO -> 'P'
            TipoPeca.CAVALO -> 'C'
            TipoPeca.BISPO -> 'B'
            TipoPeca.TORRE -> 'T'
            TipoPeca.DAMA -> 'D'
            TipoPeca.REI -> 'K'
        }
        return if (peca.cor == CorXadrez.BRANCAS) base else base.lowercaseChar()
    }

    private fun chave(estado: EstadoXadrez): String = buildString {
        for (l in 0..7) for (c in 0..7) {
            val p = estado.casas[l][c]
            append(if (p == null) '.' else letraPeca(p))
        }
        append('|')
        append(if (estado.vez == CorXadrez.BRANCAS) 'b' else 'n')
        append('|')
        append(estado.roques.sortedBy { it.ordinal }.joinToString("") { it.name })
        append('|')
        estado.alvoEnPassant?.let { append(XadrezRegras.notacao(it)) } ?: append('-')
    }

    private fun contarRepeticao(estado: EstadoXadrez) {
        val k = chave(estado)
        repeticoes[k] = (repeticoes[k] ?: 0) + 1
    }

    /** Resultado completo da partida, incluindo repetição. */
    fun resultadoPartida(estado: EstadoXadrez): ResultadoXadrez? {
        XadrezRegras.resultado(estado)?.let { return it }
        return if ((repeticoes[chave(estado)] ?: 0) >= 3) {
            ResultadoXadrez.EMPATE_REPETICAO
        } else {
            null
        }
    }

    fun jogar(lance: LanceXadrez) {
        val antes = _estado.value
        val depois = XadrezRegras.aplicar(antes, lance)
        _estado.value = depois
        val lado = if (antes.vez == CorXadrez.BRANCAS) "B" else "N"
        _historico.value = _historico.value + "$lado: ${XadrezRegras.textoLance(lance)}"
        contarRepeticao(depois)
    }

    fun reiniciar() {
        _estado.value = XadrezRegras.inicial()
        _historico.value = emptyList()
        repeticoes.clear()
        contarRepeticao(_estado.value)
    }
}

private val DIFICULDADES = listOf("Fácil", "Médio", "Difícil")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XadrezScreen(config: XadrezConfig, onSair: () -> Unit) {
    var contraApp by remember { mutableStateOf(true) }
    var indiceDificuldade by remember { mutableIntStateOf(1) }
    var tokenDeReinicio by remember { mutableIntStateOf(0) }

    val partida = remember(contraApp, indiceDificuldade, tokenDeReinicio) {
        XadrezPartida(contraApp = contraApp, profundidadeIA = config.profundidade(indiceDificuldade))
    }
    val ia = remember(partida) {
        when (indiceDificuldade) {
            0 -> IAXadrez(config.profundidadeFacil, config.chanceLanceAleatorioFacil)
            1 -> IAXadrez(config.profundidadeMedia)
            else -> IAXadrez(config.profundidadeDificil)
        }
    }
    val estado by partida.estado.collectAsState()
    val historico by partida.historico.collectAsState()
    var pensando by remember { mutableStateOf(false) }

    val resultado = partida.resultadoPartida(estado)
    val lances = remember(estado) { XadrezRegras.jogadasLegais(estado) }
    var selecionada by remember(estado) { mutableStateOf<Pos?>(null) }
    var promocaoPendente by remember(estado) { mutableStateOf<Pair<Pos, List<LanceXadrez>>?>(null) }

    LaunchedEffect(estado, partida) {
        if (partida.contraApp && estado.vez == CorXadrez.NEGRAS &&
            partida.resultadoPartida(estado) == null
        ) {
            pensando = true
            delay(250)
            val lance = try {
                withContext(Dispatchers.Default) { ia.escolherLance(estado) }
            } catch (e: Exception) {
                Log.e("Xadrez", "erro inesperado na IA", e)
                null
            }
            pensando = false
            lance?.let { partida.jogar(it) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Xadrez") },
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = contraApp,
                    onClick = { contraApp = true },
                    label = { Text("Contra o app") }
                )
                FilterChip(
                    selected = !contraApp,
                    onClick = { contraApp = false },
                    label = { Text("2 jogadores") }
                )
            }
            if (contraApp) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    DIFICULDADES.forEachIndexed { indice, nome ->
                        FilterChip(
                            selected = indiceDificuldade == indice,
                            onClick = { indiceDificuldade = indice },
                            label = { Text(nome) }
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = quando(estado.vez, contraApp, pensando),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { tokenDeReinicio++ }) { Text("Reiniciar") }
            }

            Spacer(Modifier.height(8.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .padding(2.dp)
            ) {
                Column(Modifier.fillMaxSize()) {
                    repeat(8) { linha ->
                        Row(Modifier.weight(1f)) {
                            repeat(8) { coluna ->
                                CasaXadrez(
                                    pos = Pos(linha, coluna),
                                    estado = estado,
                                    lances = lances,
                                    selecionada = selecionada,
                                    jogavel = !pensando && resultado == null,
                                    aoTocar = { tocada ->
                                        val peca = estado.casas[tocada.linha][tocada.coluna]
                                        val origem = selecionada
                                        val lancesDaOrigem = lances.filter { it.origem == origem && it.destino == tocada }
                                        when {
                                            lancesDaOrigem.isNotEmpty() -> {
                                                if (lancesDaOrigem.first().promover != null) {
                                                    promocaoPendente = tocada to lancesDaOrigem
                                                } else {
                                                    partida.jogar(lancesDaOrigem.first())
                                                    selecionada = null
                                                }
                                            }
                                            peca != null && peca.cor == estado.vez ->
                                                selecionada = tocada
                                            else -> selecionada = null
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = historico.takeLast(6).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Regras completas: roque, en passant, promoção, pat, 50 lances e " +
                    "empate por repetição. O texto “Como jogar” vem no pacote do jogo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
        }
    }

    promocaoPendente?.let { (_, opcoes) ->
        AlertDialog(
            onDismissRequest = { promocaoPendente = null },
            title = { Text("Escolha a peça da promoção") },
            text = {
                Column {
                    listOf(TipoPeca.DAMA, TipoPeca.TORRE, TipoPeca.BISPO, TipoPeca.CAVALO).forEach { tipo ->
                        TextButton(onClick = {
                            val escolhido = opcoes.first { it.promover == tipo }
                            promocaoPendente = null
                            partida.jogar(escolhido)
                            selecionada = null
                        }) {
                            Text(XadrezRegras.nomeTipo(tipo).replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }

    if (resultado != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Fim de partida") },
            text = { Text(textoResultado(resultado, contraApp)) },
            confirmButton = {
                TextButton(onClick = { tokenDeReinicio++ }) { Text("Jogar de novo") }
            },
            dismissButton = {
                TextButton(onClick = onSair) { Text("Sair") }
            }
        )
    }
}

@Composable
private fun RowScope.CasaXadrez(
    pos: Pos,
    estado: EstadoXadrez,
    lances: List<LanceXadrez>,
    selecionada: Pos?,
    jogavel: Boolean,
    aoTocar: (Pos) -> Unit,
) {
    val clara = (pos.linha + pos.coluna) % 2 == 0
    val corCasa = if (clara) Color(0xFFF0D9B5) else Color(0xFFB58863)
    val peca = estado.casas[pos.linha][pos.coluna]
    val isDestino = selecionada != null && lances.any { it.origem == selecionada && it.destino == pos }
    val isCaptura = selecionada != null &&
        lances.any { it.origem == selecionada && it.destino == pos && it.capturada != null }
    val reiEmXeque = peca != null && peca.tipo == TipoPeca.REI && peca.cor == estado.vez &&
        XadrezRegras.inCheck(estado, estado.vez)

    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .background(corCasa)
            .let { base ->
                if (jogavel) base.clickable { aoTocar(pos) } else base
            }
            .then(
                if (reiEmXeque) Modifier.border(2.dp, Color(0xFFD64C3B)) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (peca != null) {
            val fundo = if (peca.cor == CorXadrez.BRANCAS) Color(0xFFF7F2E4) else Color(0xFF242424)
            val glifo = when (peca.tipo) {
                TipoPeca.PEAO -> "♟"
                TipoPeca.CAVALO -> "♞"
                TipoPeca.BISPO -> "♝"
                TipoPeca.TORRE -> "♜"
                TipoPeca.DAMA -> "♛"
                TipoPeca.REI -> "♚"
            }
            Box(
                Modifier
                    .padding(3.dp)
                    .fillMaxSize()
                    .border(
                        1.dp,
                        if (peca.cor == CorXadrez.BRANCAS) Color(0xFF8A7A5C) else Color(0xFF6E6E6E),
                        CircleShape
                    )
                    .background(fundo, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = glifo,
                    fontSize = 17.sp,
                    color = if (peca.cor == CorXadrez.BRANCAS) Color(0xFF3A3223) else Color(0xFFE8E2D4),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (isDestino) {
            Box(
                Modifier
                    .padding(3.dp)
                    .fillMaxSize()
                    .border(
                        3.dp,
                        if (isCaptura) Color(0xFFCC3B3B) else Color(0xFF2E7D32),
                        RoundedCornerShape(6.dp)
                    )
            )
        }
    }
}

private fun quando(vez: CorXadrez, contraApp: Boolean, pensando: Boolean): String = when {
    pensando -> "O app está pensando…"
    contraApp && vez == CorXadrez.BRANCAS -> "Sua vez (brancas)"
    contraApp && vez == CorXadrez.NEGRAS -> "Vez das negras"
    else -> if (vez == CorXadrez.BRANCAS) "Vez das brancas" else "Vez das negras"
}

private fun textoResultado(resultado: ResultadoXadrez, contraApp: Boolean): String = when (resultado) {
    ResultadoXadrez.VITORIA_BRANCAS ->
        if (contraApp) "Você venceu: xeque-mate com as brancas!" else "Xeque-mate das brancas!"
    ResultadoXadrez.VITORIA_NEGRAS ->
        if (contraApp) "O app venceu: xeque-mate com as negras." else "Xeque-mate das negras!"
    ResultadoXadrez.EMPATE_PAT -> "Empate: pat (quem move não está em xeque, mas não tem lance legal)."
    ResultadoXadrez.EMPATE_50_LANCES -> "Empate: regra dos 50 lances."
    ResultadoXadrez.EMPATE_MATERIAL -> "Empate: material insuficiente."
    ResultadoXadrez.EMPATE_REPETICAO -> "Empate: a mesma posição se repetiu três vezes."
}

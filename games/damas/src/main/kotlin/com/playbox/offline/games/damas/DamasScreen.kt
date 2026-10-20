package com.playbox.offline.games.damas

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
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

/**
 * Partida de damas em memória (uma sessão da tela).
 * Não persiste entre reinícios do app — jogo por sessão.
 */
class DamasPartida(val contraApp: Boolean, val profundidadeIA: Int) {
    private val _estado = MutableStateFlow(DamasRegras.inicial())
    val estado: StateFlow<EstadoDamas> = _estado.asStateFlow()

    fun jogar(jogada: Jogada) {
        _estado.value = DamasRegras.aplicar(_estado.value, jogada)
    }

    fun reiniciar() {
        _estado.value = DamasRegras.inicial()
    }
}

private val DIFICULDADES = listOf("Fácil" to 2, "Médio" to 4, "Difícil" to 6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DamasScreen(config: DamasConfig, onSair: () -> Unit) {
    var contraApp by remember { mutableStateOf(true) }
    var indiceDificuldade by remember { mutableIntStateOf(1) }
    var tokenDeReinicio by remember { mutableIntStateOf(0) }

    val partida = remember(contraApp, indiceDificuldade, tokenDeReinicio) {
        DamasPartida(
            contraApp = contraApp,
            profundidadeIA = DIFICULDADES[indiceDificuldade].second,
        )
    }
    val ia = remember(partida) {
        when (indiceDificuldade) {
            0 -> IADamas.facil()
            else -> IADamas(profundidade = DIFICULDADES[indiceDificuldade].second)
        }
    }
    val estado by partida.estado.collectAsState()
    var pensando by remember { mutableStateOf(false) }

    val limite = config.jogadasSemCapturaParaEmpate.coerceAtLeast(1)
    val resultado = DamasRegras.resultado(estado, limite)
    val jogadas = remember(estado) { DamasRegras.jogadasLegais(estado) }
    var selecionada by remember(estado) { mutableStateOf<Casa?>(null) }

    // Lance do app (escuras), quando o modo é contra o app.
    LaunchedEffect(estado, partida) {
        if (partida.contraApp && estado.vez == CorPeca.ESCURA &&
            DamasRegras.resultado(estado, limite) == null
        ) {
            pensando = true
            delay(300)
            val lance = withContext(Dispatchers.Default) { ia.escolherJogada(estado) }
            pensando = false
            partida.jogar(lance)
        }
    }

    val corCasaClara = corDe(config.casaClara, Color(0xFFEED9B6))
    val corCasaEscura = corDe(config.casaEscura, Color(0xFF8B5A2B))
    val corPecaClara = corDe(config.pecaClara, Color(0xFFF7F3E8))
    val corPecaEscura = corDe(config.pecaEscura, Color(0xFF232323))

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Damas") },
            navigationIcon = {
                IconButton(onClick = onSair) {
                    Icon(
                        painter = PlayBoxIconPainter(PlayBoxIcon.BACK),
                        contentDescription = "Sair da partida"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Modo
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    DIFICULDADES.forEachIndexed { indice, (nome, _) ->
                        FilterChip(
                            selected = indiceDificuldade == indice,
                            onClick = { indiceDificuldade = indice },
                            label = { Text(nome) }
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = quando(estado.vez, contraApp, pensando),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Claras ${estado.pecasClaras} · Escuras ${estado.pecasEscuras}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { tokenDeReinicio++ }) {
                    Text("Reiniciar")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Tabuleiro
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .padding(2.dp)
            ) {
                Column(Modifier.fillMaxSize()) {
                    repeat(DamasRegras.TAMANHO) { linha ->
                        Row(Modifier.weight(1f)) {
                            repeat(DamasRegras.TAMANHO) { coluna ->
                                CasaTabuleiro(
                                    casa = Casa(linha, coluna),
                                    estado = estado,
                                    jogadas = jogadas,
                                    selecionada = selecionada,
                                    corCasaClara = corCasaClara,
                                    corCasaEscura = corCasaEscura,
                                    corPecaClara = corPecaClara,
                                    corPecaEscura = corPecaEscura,
                                    jogavel = !pensando && resultado == null,
                                    aoTocar = { casaTocada ->
                                        val peca = estado.casas[casaTocada.linha][casaTocada.coluna]
                                        val destino = jogadas.firstOrNull {
                                            it.origem == selecionada && it.destino == casaTocada
                                        }
                                        when {
                                            destino != null -> {
                                                partida.jogar(destino)
                                                selecionada = null
                                            }
                                            peca != null && peca.cor == estado.vez &&
                                                jogadas.any { it.origem == casaTocada } ->
                                                selecionada = casaTocada
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
                text = "Você joga com as peças claras. Tocar numa peça mostra os " +
                    "destinos possíveis; a captura é obrigatória e vale capturar o " +
                    "maior número de peças.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
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
private fun RowScope.CasaTabuleiro(
    casa: Casa,
    estado: EstadoDamas,
    jogadas: List<Jogada>,
    selecionada: Casa?,
    corCasaClara: Color,
    corCasaEscura: Color,
    corPecaClara: Color,
    corPecaEscura: Color,
    jogavel: Boolean,
    aoTocar: (Casa) -> Unit,
) {
    val escura = (casa.linha + casa.coluna) % 2 == 1
    val peca = estado.casas[casa.linha][casa.coluna]
    val isDestino = selecionada != null &&
        jogadas.any { it.origem == selecionada && it.destino == casa }
    val isCaptura = selecionada != null &&
        jogadas.any { it.origem == selecionada && it.destino == casa && it.isCaptura }
    val isOrigem = selecionada == casa

    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .background(if (escura) corCasaEscura else corCasaClara)
            .let { base ->
                if (escura && jogavel) {
                    base.clickable { aoTocar(casa) }
                } else {
                    base
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (peca != null) {
            val corFundo = if (peca.cor == CorPeca.CLARA) corPecaClara else corPecaEscura
            val corBorda = if (peca.cor == CorPeca.CLARA) Color(0xFF9C8B6E) else Color(0xFF6E6E6E)
            val bordaSelecao = if (isOrigem) 3.dp else 1.dp
            Box(
                Modifier
                    .padding(3.dp)
                    .fillMaxSize()
                    .border(bordaSelecao, corBorda, CircleShape)
                    .background(corFundo, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (peca.dama) {
                    Text(
                        text = "♛",
                        color = if (peca.cor == CorPeca.CLARA) Color(0xFF5B4A21) else Color(0xFFE4C441),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        if (isDestino) {
            Box(
                Modifier
                    .size(18.dp)
                    .background(
                        if (isCaptura) Color(0xFFCC3B3B) else Color(0xFFF2F2F2),
                        CircleShape
                    )
            )
        }
    }
}

private fun quando(vez: CorPeca, contraApp: Boolean, pensando: Boolean): String = when {
    pensando -> "O app está pensando…"
    contraApp && vez == CorPeca.CLARA -> "Sua vez (claras)"
    contraApp && vez == CorPeca.ESCURA -> "Vez das escuras"
    else -> if (vez == CorPeca.CLARA) "Vez das claras" else "Vez das escuras"
}

private fun textoResultado(resultado: ResultadoDamas, contraApp: Boolean): String = when (resultado) {
    ResultadoDamas.VITORIA_CLARAS ->
        if (contraApp) "Você venceu com as claras!" else "As peças claras venceram!"
    ResultadoDamas.VITORIA_ESCURAS ->
        if (contraApp) "O app venceu com as escuras." else "As peças escuras venceram!"
    ResultadoDamas.EMPATE -> "Empate: 30 lances de cada lado sem captura."
}

private fun corDe(hex: String, padrao: Color): Color =
    runCatching {
        Color(android.graphics.Color.parseColor(hex.trim()))
    }.getOrDefault(padrao)

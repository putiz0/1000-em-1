package com.playbox.offline.games.domino

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.playbox.offline.core.designsystem.PlayBoxIcon
import com.playbox.offline.core.designsystem.PlayBoxIconPainter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DominoPartida(val config: DominoConfig, val numJogadores: Int) {
    private val nomes = (1..numJogadores).map { "Jogador $it" }
    private val _estado = MutableStateFlow(
        DominoRegras.novoJogo(nomes, maoInicial = config.maoInicial)
    )
    val estado: StateFlow<EstadoDomino> = _estado.asStateFlow()

    fun jogar(pedra: PedraDomino, lado: LadoMesa) {
        val atual = _estado.value
        _estado.value = DominoRegras.jogar(
            estado = atual,
            jogador = atual.vez,
            pedra = pedra,
            lado = lado,
            metaPontos = config.metaPontos,
            bonusCarrocaBatida = config.bonusCarrocaBatida,
        )
    }

    fun comprar() {
        _estado.value = DominoRegras.comprar(_estado.value)
    }

    fun passar() {
        _estado.value = DominoRegras.passar(_estado.value)
    }

    fun proximaRodada() {
        _estado.value =
            DominoRegras.iniciarProximaRodada(_estado.value, maoInicial = config.maoInicial)
    }

    fun reiniciar() {
        _estado.value = DominoRegras.novoJogo(nomes, maoInicial = config.maoInicial)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DominoScreen(config: DominoConfig, onSair: () -> Unit) {
    var numJogadores by remember { mutableIntStateOf(0) }
    if (numJogadores !in (config.jogadoresMin..config.jogadoresMax)) {
        AlertDialog(
            onDismissRequest = onSair,
            title = { Text("Quantos jogadores?") },
            text = { Text("Dominó para 2 a 4 pessoas no mesmo aparelho. A partida é toda local.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (config.jogadoresMin..config.jogadoresMax).forEach { n ->
                        TextButton(onClick = { numJogadores = n }) {
                            Text("$n")
                        }
                    }
                }
            },
        )
        return
    }

    var tokenReinicio by remember { mutableIntStateOf(0) }
    val partida = remember(numJogadores, tokenReinicio) { DominoPartida(config, numJogadores) }
    val estado by partida.estado.collectAsState()

    var pedraEscolhida by remember { mutableStateOf<PedraDomino?>(null) }
    val vez = estado.vez
    val possiveis = remember(estado) { DominoRegras.jogadasPossiveis(estado, vez) }
    val podeJogar = possiveis.isNotEmpty()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Dominó") },
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
                text = "Rodada ${estado.numeroRodada} — vez de: ${estado.jogadores[vez].nome}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Pontos: " + estado.jogadores.joinToString(" · ") { "${it.nome} ${it.pontos}" } +
                    "  (meta ${config.metaPontos})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Mesa",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .padding(6.dp)
            ) {
                if (estado.mesa.isEmpty()) {
                    Text(
                        text = "Mesa vazia: ${estado.jogadores[vez].nome} abre com qualquer pedra.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(estado.mesa.size) { index ->
                            PedraNaMesa(
                                pedra = estado.mesa[index],
                                isPonta = index == 0 || index == estado.mesa.size - 1
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Mão de ${estado.jogadores[vez].nome}" +
                    "  (outras: " + estado.jogadores
                    .filterIndexed { i, _ -> i != vez }
                    .joinToString(" · ") { "${it.nome}: ${it.mao.size} pedras" } + ")",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                estado.jogadores[vez].mao.forEach { pedra ->
                    val lados = possiveis[pedra]
                    PedraNaMao(
                        pedra = pedra,
                        jogavel = lados != null,
                        aoTocar = {
                            if (lados != null) {
                                if (lados.size > 1) {
                                    pedraEscolhida = pedra
                                } else {
                                    partida.jogar(pedra, lados.first())
                                }
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (estado.rodadaFechada == null) {
                    if (!podeJogar && estado.monte.isNotEmpty()) {
                        Button(onClick = { partida.comprar() }) {
                            Text("Comprar (${estado.monte.size})")
                        }
                    } else if (!podeJogar) {
                        Button(onClick = { partida.passar() }) {
                            Text("Passar (toco)")
                        }
                    }
                    OutlinedButton(onClick = { tokenReinicio++ }) { Text("Reiniciar") }
                }
            }
            Text(
                text = "Tocar numa pedra destacada joga na mesa; se ela caber dos dois lados, o app pergunta em qual ponta.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
        }
    }

    pedraEscolhida?.let { pedra ->
        AlertDialog(
            onDismissRequest = { pedraEscolhida = null },
            title = { Text("Em que ponta?") },
            text = { Text("A pedra encaixa dos dois lados da mesa.") },
            confirmButton = {
                TextButton(onClick = { pedraEscolhida = null; partida.jogar(pedra, LadoMesa.ESQUERDA) }) {
                    Text("Esquerda (${estado.pontaEsquerda})")
                }
            },
            dismissButton = {
                TextButton(onClick = { pedraEscolhida = null; partida.jogar(pedra, LadoMesa.DIREITA) }) {
                    Text("Direita (${estado.pontaDireita})")
                }
            }
        )
    }

    estado.rodadaFechada?.let { rodada ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(if (estado.campeao != null) "Fim de partida" else rodada.texto) },
            text = {
                Column {
                    if (estado.campeao != null) {
                        Text(
                            "🏆 ${estado.jogadores[estado.campeao!!].nome} chegou a " +
                                "${estado.jogadores[estado.campeao!!].pontos} pontos!",
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    rodada.detalhes.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Placar: " + estado.jogadores.sortedByDescending { it.pontos }
                            .joinToString(" · ") { "${it.nome} ${it.pontos}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                if (estado.campeao != null) {
                    TextButton(onClick = onSair) { Text("Sair") }
                } else {
                    TextButton(onClick = { partida.proximaRodada() }) { Text("Próxima rodada") }
                }
            }
        )
    }
}

@Composable
private fun PedraNaMesa(pedra: PedraDomino, isPonta: Boolean) {
    Column(
        modifier = Modifier
            .then(if (isPonta) Modifier.border(2.dp, Color(0xFF2E7D32), RoundedCornerShape(6.dp)) else Modifier)
            .padding(2.dp)
    ) {
        MeiaPedra(pedra.ladoA)
        Box(Modifier.height(2.dp).background(Color(0xFF4E4E4E)))
        MeiaPedra(pedra.ladoB)
    }
}

@Composable
private fun PedraNaMao(
    pedra: PedraDomino,
    jogavel: Boolean,
    aoTocar: () -> Unit,
) {
    Column(
        modifier = Modifier
            .then(
                if (jogavel) {
                    Modifier.border(2.dp, Color(0xFFCC3B3B), RoundedCornerShape(6.dp))
                        .clickable { aoTocar() }
                } else {
                    Modifier.background(Color(0x33999999), RoundedCornerShape(6.dp))
                }
            )
            .padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MeiaPedra(pedra.ladoA)
        Box(Modifier.width(2.dp).height(12.dp).background(Color(0xFF4E4E4E)))
        MeiaPedra(pedra.ladoB)
    }
}

/** Posição dos pontos numa grade 3×3 (índice 0..8, da esquerda p topo). */
private fun posicoesDe(valor: Int): List<Int> = when (valor) {
    0 -> emptyList()
    1 -> listOf(4)
    2 -> listOf(0, 8)
    3 -> listOf(0, 4, 8)
    4 -> listOf(0, 2, 6, 8)
    5 -> listOf(0, 2, 4, 6, 8)
    else -> listOf(0, 2, 3, 5, 6, 8)
}

@Composable
private fun MeiaPedra(valor: Int) {
    Box(
        Modifier
            .size(width = 34.dp, height = 42.dp)
            .background(Color(0xFFF3EFE2), RoundedCornerShape(4.dp))
            .padding(2.dp)
    ) {
        val posicoes = posicoesDe(valor)
        for (linha in 0 until 3) {
            for (coluna in 0 until 3) {
                val indice = linha * 3 + coluna
                val ponto = indice in posicoes
                Box(
                    Modifier
                        .align(alignmentFor(linha, coluna))
                        .size(6.dp)
                        .then(if (ponto) Modifier.background(Color(0xFF333333), androidx.compose.foundation.shape.CircleShape) else Modifier)
                        .padding(1.dp)
                )
            }
        }
    }
}

private fun alignmentFor(linha: Int, coluna: Int): Alignment {
    val horizontal = when (coluna) {
        0 -> Alignment.Start
        1 -> Alignment.CenterHorizontally
        else -> Alignment.End
    }
    val vertical = when (linha) {
        0 -> Alignment.Top
        1 -> Alignment.CenterVertically
        else -> Alignment.Bottom
    }
    return when {
        horizontal == Alignment.Start && vertical == Alignment.Top -> Alignment.TopStart
        horizontal == Alignment.CenterHorizontally && vertical == Alignment.Top -> Alignment.TopCenter
        horizontal == Alignment.End && vertical == Alignment.Top -> Alignment.TopEnd
        horizontal == Alignment.Start && vertical == Alignment.CenterVertically -> Alignment.CenterStart
        horizontal == Alignment.CenterHorizontally && vertical == Alignment.CenterVertically -> Alignment.Center
        horizontal == Alignment.End && vertical == Alignment.CenterVertically -> Alignment.CenterEnd
        horizontal == Alignment.Start && vertical == Alignment.Bottom -> Alignment.BottomStart
        horizontal == Alignment.CenterHorizontally && vertical == Alignment.Bottom -> Alignment.BottomCenter
        else -> Alignment.BottomEnd
    }
}

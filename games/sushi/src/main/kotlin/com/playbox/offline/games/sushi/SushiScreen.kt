package com.playbox.offline.games.sushi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Porta do motor para a tela (padrão dos outros jogos: holder simples). */
class SushiPartida(val config: SushiConfig, val contraApp: Boolean) {

    private val ia = IAsushi()
    private val nomes = if (contraApp) listOf("Você", "App") else listOf("Jogador 1", "Jogador 2")
    private val _estado = MutableStateFlow(SushiRegras.novoJogo(config, nomes, contraApp))
    val estado: StateFlow<EstadoSushi> = _estado.asStateFlow()

    /** Incrementa a cada mudança real de estado. */
    private val _versao = MutableStateFlow(0)
    val versao: StateFlow<Int> = _versao.asStateFlow()

    private fun mudar(acao: (EstadoSushi) -> EstadoSushi) {
        val atual = _estado.value
        val novo = acao(atual)
        if (novo != atual) {
            _estado.value = novo
            _versao.value = _versao.value + 1
        }
    }

    /**
     * Lance do humano: grava o pick do assento da vez; contra o app, o App
     * responde na hora e o par se revela (mãos trocam, rodada fecha se
     * preciso). Em 2 jogadores locais, o assento da vez alterna normalmente.
     */
    fun escolherHumano(indice: Int) = mudar { atual ->
        if (atual.vez < 0 || atual.rodadaFechada || atual.fim) return@mudar atual
        var novo = SushiRegras.escolher(atual, atual.vez, indice)
        if (novo.contraApp && novo.vez == 1 && !novo.rodadaFechada && !novo.fim) {
            novo = SushiRegras.escolher(novo, 1, ia.escolher(novo, 1))
        }
        novo
    }

    fun proximaRodada() = mudar { SushiRegras.proximaRodada(it) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SushiScreen(config: SushiConfig, onSair: () -> Unit) {
    var contraApp by remember { mutableStateOf<Boolean?>(null) }
    if (contraApp == null) {
        AlertDialog(
            onDismissRequest = onSair,
            title = { Text("Festival de Sushi") },
            text = {
                Text(
                    "Draft de cartas em ${config.rodadas} rodadas para 2 assentos: " +
                        "você contra o App ou 2 humanos no mesmo aparelho."
                )
            },
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
    val partida = remember(contraApp, tokenReinicio) { SushiPartida(config, contraApp == true) }
    val estado by partida.estado.collectAsState()

    // Assento cuja mão aparece: contra o app é sempre o humano (0);
    // no modo local, a mão do assento da vez.
    val maoVisivel = if (estado.contraApp) 0 else if (estado.vez >= 0) estado.vez else 0
    // Contra o app só o humano (assento 0) toca nas cartas; no modo local ambos são humanos.
    val humanoPodeEscolher = !estado.contraApp || estado.vez == 0

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Festival de Sushi") },
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
                text = "Rodada ${estado.rodada} de ${config.rodadas} — " +
                    estado.nomes.mapIndexed { i, n -> "$n ${estado.pontos[i]} pontos" }
                        .joinToString(" · "),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Pudins guardados: " + estado.nomes
                    .mapIndexed { i, n -> "$n ${estado.pudim[i]}" }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    estado.fim -> "Partida encerrada"
                    estado.rodadaFechada -> "Rodada fechada"
                    estado.vez >= 0 -> "Mão de ${estado.nomes[maoVisivel]} — vez de ${estado.nomes[estado.vez]}"
                    else -> "Revelando..."
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 6.dp, bottom = 2.dp)
            ) {
                estado.mao[maoVisivel].forEachIndexed { indice, id ->
                    CartaView(
                        id = id,
                        aoTocar = {
                            if (humanoPodeEscolher && estado.vez == maoVisivel) {
                                partida.escolherHumano(indice)
                            }
                        },
                    )
                }
            }
            if (!humanoPodeEscolher && !estado.rodadaFechada && !estado.fim) {
                Text(
                    text = "Você já escolheu; o App está escolhendo...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(6.dp))
            UltimaRevelacao(estado)

            Spacer(Modifier.height(8.dp))
            PratoView(estado, 0)
            Spacer(Modifier.height(4.dp))
            PratoView(estado, 1)

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (estado.rodadaFechada && !estado.fim) {
                    Button(onClick = { partida.proximaRodada() }) { Text("Próxima rodada") }
                }
                OutlinedButton(onClick = { tokenReinicio++ }) { Text("Reiniciar") }
            }

            Text(
                text = "Escolha 1 carta por lance: quando os dois assentados escolhem, as cartas " +
                    "se revelam, entram no prato (pudim fica guardado) e as mãos restantes trocam de lado.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(10.dp))
            Text(
                text = "Histórico",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            estado.log.takeLast(6).forEach { msg ->
                Text(
                    text = "• $msg",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // ---- rodada fechada ----
    if (estado.rodadaFechada && !estado.fim) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Fim da rodada ${estado.rodada}") },
            text = {
                Column {
                    estado.rodadaDetalhes.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Placar: " + estado.nomes
                            .mapIndexed { i, n -> "$n ${estado.pontos[i]}" }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { partida.proximaRodada() }) { Text("Próxima rodada") }
            },
        )
    }

    // ---- fim da partida ----
    if (estado.fim) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Fim da partida") },
            text = {
                Column {
                    val v = estado.vencedor
                    if (v == null) {
                        Text("Empate! Ninguém levou o festival.", fontWeight = FontWeight.SemiBold)
                    } else {
                        Text(
                            "🏆 ${estado.nomes[v]} venceu o festival!",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    estado.rodadaDetalhes.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Placar final: " + estado.nomes
                            .mapIndexed { i, n -> "$n ${estado.pontos[i]}" }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = onSair) { Text("Sair") } },
        )
    }
}

@Composable
private fun UltimaRevelacao(estado: EstadoSushi) {
    val r0 = estado.ultimaRevelacao[0]
    val r1 = estado.ultimaRevelacao[1]
    if (r0 != null && r1 != null) {
        Text(
            text = "Revelado: ${estado.nomes[0]} → ${SushiRegras.corretorPlural(r0, 1)} · " +
                "${estado.nomes[1]} → ${SushiRegras.corretorPlural(r1, 1)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun PratoView(estado: EstadoSushi, assento: Int) {
    val prato = estado.prato[assento]
    Text(
        text = "Prato de ${estado.nomes[assento]}: " +
            (if (prato.isEmpty()) "vazio"
            else prato.entries.joinToString(", ") { SushiRegras.corretorPlural(it.key, it.value) }),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun CartaView(id: String, aoTocar: () -> Unit) {
    Box(
        Modifier
            .background(corDacarta(id), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0x66000000), RoundedCornerShape(8.dp))
            .size(width = 60.dp, height = 74.dp)
            .clickable { aoTocar() }
            .padding(4.dp)
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(glifoDaCarta(id), fontSize = 18.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                SushiRegras.nomesDeCartas[id] ?: id,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xCC000000),
                fontSize = 10.sp
            )
        }
    }
}

private fun glifoDaCarta(id: String): String = when (id) {
    Cartas.NIGUIRI -> "🍚"
    Cartas.ROLINHO -> "🍥"
    Cartas.TEMAKI -> "🌯"
    Cartas.MOCHI -> "🍡"
    Cartas.WASABI -> "🌿"
    Cartas.GENGIBRE -> "🍤"
    Cartas.CHA -> "🍵"
    Cartas.PUDIM -> "🍮"
    else -> "🎴"
}

private fun corDacarta(id: String): Color = when (id) {
    Cartas.NIGUIRI -> Color(0xFFFFCA28)
    Cartas.ROLINHO -> Color(0xFF66BB6A)
    Cartas.TEMAKI -> Color(0xFF26A69A)
    Cartas.MOCHI -> Color(0xFFFFAB91)
    Cartas.WASABI -> Color(0xFF7CB342)
    Cartas.GENGIBRE -> Color(0xFFF06292)
    Cartas.CHA -> Color(0xFFBCAAA4)
    Cartas.PUDIM -> Color(0xFFBA68C8)
    else -> Color(0xFFE0E0E0)
}

package com.playbox.offline.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.playbox.offline.R
import com.playbox.offline.core.designsystem.EmptyStateView
import com.playbox.offline.core.packs.PackManager
import com.playbox.offline.games.damas.DamasConfig
import com.playbox.offline.games.damas.DamasScreen
import com.playbox.offline.games.domino.DominoConfig
import com.playbox.offline.games.domino.DominoScreen
import com.playbox.offline.games.imperio.ImperioBoard
import com.playbox.offline.games.imperio.ImperioScreen
import com.playbox.offline.games.sushi.SushiConfig
import com.playbox.offline.games.sushi.SushiScreen
import com.playbox.offline.games.xadrez.XadrezConfig
import com.playbox.offline.games.xadrez.XadrezScreen

/**
 * Despachante da rota play/{gameId}. O motor de cada jogo (código) já está
 * compilado no APK; a config e as regras vêm do pacote instalado (dados).
 * Sem pacote, a partida não abre — "Jogar" exige o pack baixado.
 */
@Composable
fun GamePlayScreen(
    gameId: String?,
    packManager: PackManager,
    onSair: () -> Unit
) {
    when (gameId) {
        "damas" -> {
            val config = remember(gameId) {
                DamasConfig.doPacote(packManager.readTextFromLatestPack("damas", "config.json"))
            }
            if (config == null) {
                PacoteNecessarioView(onSair)
            } else {
                DamasScreen(config = config, onSair = onSair)
            }
        }
        "xadrez" -> {
            val config = remember(gameId) {
                XadrezConfig.doPacote(packManager.readTextFromLatestPack("xadrez", "config.json"))
            }
            if (config == null) {
                PacoteNecessarioView(onSair)
            } else {
                XadrezScreen(config = config, onSair = onSair)
            }
        }
        "domino" -> {
            val config = remember(gameId) {
                DominoConfig.doPacote(packManager.readTextFromLatestPack("domino", "config.json"))
            }
            if (config == null) {
                PacoteNecessarioView(onSair)
            } else {
                DominoScreen(config = config, onSair = onSair)
            }
        }
        "imperio-imobiliario" -> {
            val board = remember(gameId) {
                ImperioBoard.doPacote(packManager.readTextFromLatestPack("imperio-imobiliario", "board.json"))
            }
            if (board == null) {
                PacoteNecessarioView(onSair)
            } else {
                ImperioScreen(board = board, onSair = onSair)
            }
        }
        "festival-de-sushi" -> {
            val config = remember(gameId) {
                SushiConfig.doPacote(packManager.readTextFromLatestPack("festival-de-sushi", "config.json"))
            }
            if (config == null) {
                PacoteNecessarioView(onSair)
            } else {
                SushiScreen(config = config, onSair = onSair)
            }
        }
        else -> ModuloNaoDisponivelView(onSair)
    }
}

@Composable
private fun PacoteNecessarioView(onSair: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EmptyStateView(
            glyph = "📦",
            title = stringResource(R.string.play_need_pack_title),
            subtitle = stringResource(R.string.play_need_pack_subtitle)
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSair) {
            Text(stringResource(R.string.play_close))
        }
    }
}

@Composable
private fun ModuloNaoDisponivelView(onSair: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EmptyStateView(
            glyph = "🚧",
            title = stringResource(R.string.play_module_unavailable_title),
            subtitle = stringResource(R.string.play_module_unavailable_subtitle)
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSair) {
            Text(stringResource(R.string.play_close))
        }
    }
}

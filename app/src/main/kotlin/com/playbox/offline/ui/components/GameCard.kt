package com.playbox.offline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.playbox.offline.R
import com.playbox.offline.core.catalog.GameWithStatus
import com.playbox.offline.core.database.InstallStatus
import com.playbox.offline.core.designsystem.CoverBox
import com.playbox.offline.core.designsystem.StatusChip
import com.playbox.offline.core.designsystem.formatBytes

/** Rótulo em português do estado atual do jogo. */
@Composable
fun installStatusLabel(game: GameWithStatus, suffix: String = ""): String = when (game.status) {
    null -> if (game.available) stringResource(R.string.status_null_available)
    else stringResource(R.string.status_upcoming)
    InstallStatus.QUEUED -> stringResource(R.string.status_queued) + suffix
    InstallStatus.DOWNLOADING -> stringResource(R.string.status_downloading) + suffix
    InstallStatus.PAUSED -> stringResource(R.string.status_paused) + suffix
    InstallStatus.COMPLETED -> stringResource(R.string.status_completed) + suffix
    InstallStatus.UPDATE_AVAILABLE -> stringResource(R.string.status_update_available) + suffix
    InstallStatus.FAILED -> stringResource(R.string.status_failed) + suffix
    InstallStatus.CORRUPTED -> stringResource(R.string.status_corrupted) + suffix
    InstallStatus.REMOVED -> stringResource(R.string.status_removed) + suffix
}

private fun statusColors(game: GameWithStatus): Pair<Color, Color> {
    val container: Color
    val content: Color
    when (game.status) {
        null -> if (game.available) {
            container = Color(0xFFDCFCE7); content = Color(0xFF14532D)
        } else {
            container = Color(0xFFF3F4F6); content = Color(0xFF4B5563)
        }
        InstallStatus.QUEUED -> { container = Color(0xFFFFF3C4); content = Color(0xFF713F12) }
        InstallStatus.DOWNLOADING -> { container = Color(0xFFDDF3FF); content = Color(0xFF0C4A6E) }
        InstallStatus.PAUSED -> { container = Color(0xFFFEF3C7); content = Color(0xFF92400E) }
        InstallStatus.COMPLETED -> { container = Color(0xFFD7F5DF); content = Color(0xFF14532D) }
        InstallStatus.UPDATE_AVAILABLE -> { container = Color(0xFFEDE9FE); content = Color(0xFF4C1D95) }
        InstallStatus.FAILED, InstallStatus.CORRUPTED -> { container = Color(0xFFFDE8E8); content = Color(0xFF7F1D1D) }
        InstallStatus.REMOVED -> { container = Color(0xFFE5E7EB); content = Color(0xFF374151) }
    }
    return container to content
}

@Composable
fun GameStatusChip(game: GameWithStatus, modifier: Modifier = Modifier) {
    val percent = if ((game.status == InstallStatus.DOWNLOADING || game.status == InstallStatus.PAUSED) &&
        game.downloadedBytes > 0 && game.progressTotalBytes > 0
    ) {
        val p = ((game.downloadedBytes * 100) / game.progressTotalBytes).toInt().coerceAtMost(100)
        " · $p%"
    } else ""

    val (container, content) = statusColors(game)
    StatusChip(
        label = installStatusLabel(game, percent),
        containerColor = container,
        contentColor = content,
        modifier = modifier
    )
}

/** Cartão de jogo (capas de catálogo, listas horizontais e grades). */
@Composable
fun GameCard(
    game: GameWithStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
    ) {
        CoverBox(
            glyph = game.entry.coverGlyph,
            coverColors = game.entry.coverColors,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = game.entry.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Row {
            GameStatusChip(game)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = formatBytes(game.entry.estimatedBytes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Linha compacta para resultados de busca e listas da biblioteca. */
@Composable
fun GameSearchRow(
    game: GameWithStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverBox(
            glyph = game.entry.coverGlyph,
            coverColors = game.entry.coverColors,
            cornerRadius = 14.dp,
            modifier = Modifier
                .width(56.dp)
                .height(56.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = game.entry.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(2.dp))
            Row {
                GameStatusChip(game)
            }
        }
        Text(
            text = formatBytes(game.entry.estimatedBytes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

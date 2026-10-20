package com.playbox.offline.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.playbox.offline.LocalAppContainer
import com.playbox.offline.R
import com.playbox.offline.core.catalog.GameWithStatus
import com.playbox.offline.core.database.InstallStatus
import com.playbox.offline.core.designsystem.CoverBox
import com.playbox.offline.core.designsystem.EmptyStateView
import com.playbox.offline.core.designsystem.formatBytes
import com.playbox.offline.ui.components.GameStatusChip
import kotlinx.coroutines.launch

@Composable
fun DownloadsScreen(
    onOpenGame: (String) -> Unit
) {
    val container = LocalAppContainer.current
    val snapshot by container.catalogRepository.snapshot.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val active = snapshot.downloadsActive()

    Column(Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.downloads_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )

        if (active.isEmpty()) {
            EmptyStateView(
                glyph = "⬇️",
                title = stringResource(R.string.downloads_empty_title),
                subtitle = stringResource(R.string.downloads_empty_subtitle)
            )
        } else {
            LazyColumn {
                items(active, key = { it.entry.id }) { game ->
                    DownloadRow(
                        game = game,
                        onOpenGame = onOpenGame,
                        onAction = { action ->
                            scope.launch {
                                when (action) {
                                    DownloadAction.PAUSE -> container.downloadCoordinator.pauseDownload(game.entry.id)
                                    DownloadAction.RESUME -> container.downloadCoordinator.startOrResumeDownload(game.entry.id)
                                    DownloadAction.CANCEL_REMOVE -> container.downloadCoordinator.cancelDownload(game.entry.id)
                                    DownloadAction.RETRY -> container.downloadCoordinator.startOrResumeDownload(game.entry.id)
                                    DownloadAction.REDOWNLOAD -> container.downloadCoordinator.startOrResumeDownload(game.entry.id)
                                    DownloadAction.DELETE_INSTALLED -> container.downloadCoordinator.deleteInstalledPack(game.entry.id)
                                }
                            }
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.downloads_storage_total,
                    formatBytes(snapshot.totalOccupiedBytes())
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

enum class DownloadAction { PAUSE, RESUME, CANCEL_REMOVE, RETRY, REDOWNLOAD, DELETE_INSTALLED }

@Composable
private fun DownloadRow(
    game: GameWithStatus,
    onOpenGame: (String) -> Unit,
    onAction: (DownloadAction) -> Unit
) {
    val entry = game.entry
    val total = game.progressTotalBytes
    val percent = if (total > 0 && game.downloadedBytes > 0) {
        ((game.downloadedBytes * 100) / total).toInt().coerceAtMost(100)
    } else 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onOpenGame(entry.id) })
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        /** Linha: capa, nome, chip e tamanho */
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverBox(
                    glyph = entry.coverGlyph,
                    coverColors = entry.coverColors,
                    cornerRadius = 14.dp,
                    modifier = Modifier
                        .width(56.dp)
                        .height(56.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(2.dp))
                    Row {
                        GameStatusChip(game)
                    }
                }
                if (total > 0 && percent > 0) {
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (game.status == InstallStatus.DOWNLOADING || game.status == InstallStatus.PAUSED) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.detail_bytes_progress,
                        formatBytes(game.downloadedBytes),
                        formatBytes(total)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when (game.status) {
                InstallStatus.QUEUED -> {
                    TextButton(onClick = { onAction(DownloadAction.CANCEL_REMOVE) }) {
                        Text(stringResource(R.string.downloads_action_cancel))
                    }
                }
                InstallStatus.DOWNLOADING -> {
                    Row {
                        TextButton(onClick = { onAction(DownloadAction.PAUSE) }) {
                            Text(stringResource(R.string.downloads_action_pause))
                        }
                        TextButton(onClick = { onAction(DownloadAction.CANCEL_REMOVE) }) {
                            Text(stringResource(R.string.downloads_action_cancel))
                        }
                    }
                }
                InstallStatus.PAUSED -> {
                    Row {
                        TextButton(onClick = { onAction(DownloadAction.RESUME) }) {
                            Text(stringResource(R.string.downloads_action_resume))
                        }
                        TextButton(onClick = { onAction(DownloadAction.CANCEL_REMOVE) }) {
                            Text(stringResource(R.string.downloads_action_cancel))
                        }
                    }
                }
                InstallStatus.FAILED -> {
                    Row {
                        TextButton(onClick = { onAction(DownloadAction.RETRY) }) {
                            Text(stringResource(R.string.downloads_action_retry))
                        }
                        TextButton(onClick = { onAction(DownloadAction.CANCEL_REMOVE) }) {
                            Text(stringResource(R.string.downloads_action_remove))
                        }
                    }
                }
                InstallStatus.CORRUPTED -> {
                    Row {
                        TextButton(onClick = { onAction(DownloadAction.REDOWNLOAD) }) {
                            Text(stringResource(R.string.downloads_action_redownload))
                        }
                        TextButton(onClick = { onAction(DownloadAction.CANCEL_REMOVE) }) {
                            Text(stringResource(R.string.downloads_action_remove))
                        }
                    }
                }
                else -> {}
            }
        }
    }


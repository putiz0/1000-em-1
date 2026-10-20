package com.playbox.offline.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.playbox.offline.core.designsystem.PlayBoxIcon
import com.playbox.offline.core.designsystem.PlayBoxIconPainter
import com.playbox.offline.core.designsystem.formatBytes
import com.playbox.offline.ui.components.GameStatusChip
import com.playbox.offline.ui.components.installStatusLabel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    gameId: String?,
    onBack: () -> Unit,
    onPlay: (String) -> Unit = {}
) {
    val container = LocalAppContainer.current
    val snapshot by container.catalogRepository.snapshot.collectAsStateWithLifecycle()
    val game = gameId?.let { snapshot.game(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(game?.entry?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = PlayBoxIconPainter(PlayBoxIcon.BACK),
                            contentDescription = stringResource(R.string.detail_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
        }
    ) { padding ->
        if (game == null) {
            EmptyStateView(
                glyph = "❓",
                title = stringResource(R.string.detail_not_found_title),
                subtitle = stringResource(R.string.detail_not_found_subtitle),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            Detail(
                game = game,
                categoryName = snapshot.category(game.entry.categoryId)?.name ?: "",
                onPlay = onPlay,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    }
}

@Composable
private fun Detail(
    game: GameWithStatus,
    categoryName: String,
    onPlay: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val container = LocalAppContainer.current
    val entry = game.entry
    var mostrarRegras by remember(entry.id, game.status) { mutableStateOf(false) }
    val regras = remember(entry.id, game.status) {
        container.packManager.readTextFromLatestPack(entry.id, "regras.md")
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            CoverBox(
                glyph = entry.coverGlyph,
                coverColors = entry.coverColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }

        Column(Modifier.padding(16.dp)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                GameStatusChip(game)
                if (categoryName.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = categoryName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.detail_size_label, formatBytes(entry.estimatedBytes)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )

            // Ações conforme o estado do jogo
            Spacer(Modifier.height(12.dp))
            DetailActions(game, onPlay)
            Spacer(Modifier.height(12.dp))

            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Text(
                text = entry.shortDescription,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(12.dp))
            entry.longDescription.split("\n\n").forEach { paragraph ->
                if (paragraph.isNotBlank()) {
                    Text(
                        text = paragraph,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    enabled = regras != null,
                    onClick = { mostrarRegras = true }
                ) {
                    Text(stringResource(R.string.detail_action_how_to))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.detail_note_rules),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (mostrarRegras && regras != null) {
                AlertDialog(
                    onDismissRequest = { mostrarRegras = false },
                    title = { Text(stringResource(R.string.play_rules_title)) },
                    text = {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = regras,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { mostrarRegras = false }) {
                            Text(stringResource(R.string.play_close))
                        }
                    }
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailActions(
    game: GameWithStatus,
    onPlay: (String) -> Unit
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var confirmRemoval by remember { mutableStateOf<Boolean>(false) }

    val entry = game.entry

    if (!entry.available) {
        Button(enabled = false, onClick = {}) {
            Text(stringResource(R.string.detail_action_download))
        }
        NoteText(stringResource(R.string.detail_note_not_ready))
        return
    }

    fun enqueue() = scope.launch { container.downloadCoordinator.startOrResumeDownload(entry.id) }
    fun pause() = scope.launch { container.downloadCoordinator.pauseDownload(entry.id) }
    fun cancel() = scope.launch { container.downloadCoordinator.cancelDownload(entry.id) }
    fun remove() = scope.launch { container.downloadCoordinator.deleteInstalledPack(entry.id) }

    when (game.status) {
        InstallStatus.QUEUED,
        InstallStatus.DOWNLOADING,
        InstallStatus.PAUSED -> {
            val total = game.progressTotalBytes
            val percent = if (total > 0) {
                ((game.downloadedBytes * 100) / total).toInt().coerceAtMost(100)
            } else 0

            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.detail_bytes_progress,
                    formatBytes(game.downloadedBytes),
                    formatBytes(total)
                ) + " — " + installStatusLabel(game),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (game.status) {
                    InstallStatus.DOWNLOADING -> {
                        OutlinedButton(onClick = ::pause) { Text(stringResource(R.string.detail_action_pause)) }
                        TextButton(onClick = ::cancel) { Text(stringResource(R.string.detail_action_cancel)) }
                    }
                    InstallStatus.PAUSED -> {
                        Button(onClick = ::enqueue) { Text(stringResource(R.string.detail_action_resume)) }
                        TextButton(onClick = ::cancel) { Text(stringResource(R.string.detail_action_cancel)) }
                    }
                    else -> {
                        TextButton(onClick = ::cancel) { Text(stringResource(R.string.detail_action_cancel)) }
                    }
                }
            }
        }

        InstallStatus.COMPLETED -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onPlay(entry.id) }) {
                    Text(stringResource(R.string.detail_action_play))
                }
                OutlinedButton(onClick = { confirmRemoval = true }) {
                    Text(stringResource(R.string.detail_action_remove))
                }
            }
            if (confirmRemoval) {
                RemoveConfirmationDialog(
                    onConfirm = { confirmRemoval = false; remove() },
                    onDismiss = { confirmRemoval = false }
                )
            }
        }

        InstallStatus.UPDATE_AVAILABLE -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onPlay(entry.id) }) {
                    Text(stringResource(R.string.detail_action_play))
                }
                Button(onClick = ::enqueue) {
                    Text(stringResource(R.string.detail_action_update))
                }
                OutlinedButton(onClick = { confirmRemoval = true }) {
                    Text(stringResource(R.string.detail_action_remove))
                }
            }
            NoteText(stringResource(R.string.detail_note_update_available))
            if (confirmRemoval) {
                RemoveConfirmationDialog(
                    onConfirm = { confirmRemoval = false; remove() },
                    onDismiss = { confirmRemoval = false }
                )
            }
        }

        InstallStatus.FAILED, InstallStatus.CORRUPTED -> {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = installStatusLabel(game),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    game.lastError?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (game.status == InstallStatus.FAILED) {
                    Button(onClick = ::enqueue) { Text(stringResource(R.string.detail_action_retry)) }
                } else {
                    Button(onClick = ::enqueue) { Text(stringResource(R.string.detail_action_redownload)) }
                }
                TextButton(onClick = ::cancel) { Text(stringResource(R.string.detail_action_cancel)) }
            }
        }

        else -> {
            // Disponível para baixar (ou remoção recém-concluída)
            val size = entry.estimatedBytes
            Button(onClick = ::enqueue) {
                Text(
                    if (size > 0) stringResource(R.string.detail_action_download_size, formatBytes(size))
                    else stringResource(R.string.detail_action_download)
                )
            }
        }
    }
}

@Composable
private fun RemoveConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_confirm_remove_title)) },
        text = { Text(stringResource(R.string.detail_confirm_remove_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.detail_confirm_remove_cta)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.detail_confirm_keep)) }
        }
    )
}

@Composable
private fun NoteText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
}

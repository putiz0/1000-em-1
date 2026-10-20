package com.playbox.offline.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.playbox.offline.LocalAppContainer
import com.playbox.offline.R
import com.playbox.offline.core.designsystem.EmptyStateView
import com.playbox.offline.core.designsystem.formatBytes
import com.playbox.offline.ui.components.GameSearchRow

@Composable
fun LibraryScreen(
    onOpenCatalog: () -> Unit,
    onOpenGame: (String) -> Unit
) {
    val container = LocalAppContainer.current
    val snapshot by container.catalogRepository.snapshot.collectAsStateWithLifecycle()
    val installed = snapshot.installed()
    val occupiedBytes = snapshot.totalOccupiedBytes()

    Column(Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.library_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )

        if (installed.isEmpty()) {
            EmptyStateView(
                glyph = "📚",
                title = stringResource(R.string.library_empty_title),
                subtitle = stringResource(R.string.library_empty_subtitle)
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onOpenCatalog,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(stringResource(R.string.library_open_catalog))
            }
        } else {
            LazyColumn {
                items(installed, key = { it.entry.id }) { game ->
                    GameSearchRow(
                        game = game,
                        onClick = { onOpenGame(game.entry.id) }
                    )
                }
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.library_storage_used,
                                formatBytes(occupiedBytes)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

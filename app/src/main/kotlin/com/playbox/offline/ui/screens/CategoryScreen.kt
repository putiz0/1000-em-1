package com.playbox.offline.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.MaterialTheme
import com.playbox.offline.LocalAppContainer
import com.playbox.offline.R
import com.playbox.offline.core.designsystem.EmptyStateView
import com.playbox.offline.core.designsystem.PlayBoxIcon
import com.playbox.offline.core.designsystem.PlayBoxIconPainter
import com.playbox.offline.ui.components.GameCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    categoryId: String?,
    onBack: () -> Unit,
    onOpenGame: (String) -> Unit
) {
    val container = LocalAppContainer.current
    val snapshot by container.catalogRepository.snapshot.collectAsStateWithLifecycle()

    val category = categoryId?.let { snapshot.category(it) }
    val games = categoryId?.let { snapshot.byCategory(it) }.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category?.name.orEmpty()) },
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
        if (games.isEmpty()) {
            EmptyStateView(
                glyph = category?.glyph?.ifEmpty { "🧩" } ?: "🧩",
                title = stringResource(R.string.category_empty_title),
                subtitle = stringResource(R.string.category_empty_subtitle),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (games.none { it.available }) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.category_all_upcoming),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(games, key = { it.entry.id }) { game ->
                        GameCard(
                            game = game,
                            onClick = { onOpenGame(game.entry.id) }
                        )
                    }
                }
            }
        }
    }
}

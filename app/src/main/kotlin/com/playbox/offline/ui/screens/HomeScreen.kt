package com.playbox.offline.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playbox.offline.LocalAppContainer
import com.playbox.offline.R
import com.playbox.offline.core.catalog.CatalogRepository
import com.playbox.offline.core.catalog.CatalogSearch
import com.playbox.offline.core.catalog.CategoryWithCounting
import com.playbox.offline.core.catalog.GameWithStatus
import com.playbox.offline.core.designsystem.PlayBoxIcon
import com.playbox.offline.core.designsystem.PlayBoxIconPainter
import com.playbox.offline.core.designsystem.SectionHeader
import com.playbox.offline.core.designsystem.EmptyStateView
import com.playbox.offline.core.designsystem.CategoryCard
import com.playbox.offline.ui.components.GameCard
import com.playbox.offline.ui.components.GameSearchRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeSections(
    val query: String = "",
    val scope: CatalogSearch.Scope = CatalogSearch.Scope.ALL,
    val results: List<GameWithStatus>? = null,
    val categories: List<CategoryWithCounting> = emptyList(),
    val popular: List<GameWithStatus> = emptyList(),
    val installed: List<GameWithStatus> = emptyList(),
    val recents: List<GameWithStatus> = emptyList(),
    val downloadsActive: List<GameWithStatus> = emptyList()
)

class HomeViewModel(private val repository: CatalogRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _scope = MutableStateFlow(CatalogSearch.Scope.ALL)
    val scope: StateFlow<CatalogSearch.Scope> = _scope.asStateFlow()

    fun onQueryChange(value: String) { _query.value = value }
    fun onScopeChange(value: CatalogSearch.Scope) { _scope.value = value }

    val sections: StateFlow<HomeSections> =
        combine(repository.snapshot, _query, _scope) { snap, query, scope ->
            val searching = if (query.isNotBlank()) {
                CatalogSearch.filter(snap.games, query, scope)
            } else null

            HomeSections(
                query = query,
                scope = scope,
                results = searching,
                categories = snap.categoriesWithCounts(),
                popular = snap.popular(limit = 6),
                installed = snap.installed(),
                recents = snap.recentlyAdded(limit = 3),
                downloadsActive = snap.downloadsActive()
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeSections()
        )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenCategory: (String) -> Unit,
    onOpenGame: (String) -> Unit,
    onOpenLibrary: () -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel { HomeViewModel(container.catalogRepository) }
    val sections by viewModel.sections.collectAsStateWithLifecycle()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Cabeçalho: logo + nome do app
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "🕹️",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .size(48.dp)
                            .padding(8.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.home_no_network_needed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Campo de pesquisa
        item {
            OutlinedTextField(
                value = sections.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = {
                    Icon(
                        painter = PlayBoxIconPainter(PlayBoxIcon.SEARCH),
                        contentDescription = stringResource(R.string.search_placeholder)
                    )
                },
                trailingIcon = {
                    if (sections.query.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.onQueryChange("") },
                            modifier = Modifier.semantics {
                                contentDescription = "Limpar busca"
                            }
                        ) {
                            Text("✕")
                        }
                    }
                }
            )
            Spacer(Modifier.height(16.dp))
        }

        if (sections.results != null) {
            // Busca ativa: chips de filtro + resultados
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val chips = listOfNotNull(
                        CatalogSearch.Scope.ALL to stringResource(R.string.search_hint_chip_all),
                        CatalogSearch.Scope.AVAILABLE to stringResource(R.string.search_hint_chip_available),
                        CatalogSearch.Scope.INSTALLED to stringResource(R.string.search_hint_chip_installed)
                    )
                    chips.forEach { (scope, label) ->
                        FilterChip(
                            selected = sections.scope == scope,
                            onClick = { viewModel.onScopeChange(scope) },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            val results = sections.results.orEmpty()
            if (results.isEmpty()) {
                item {
                    EmptyStateView(
                        glyph = "🔍",
                        title = stringResource(R.string.search_empty_title),
                        subtitle = stringResource(R.string.search_empty_subtitle)
                    )
                }
            } else {
                items(results) { game ->
                    GameSearchRow(game = game, onClick = { onOpenGame(game.entry.id) })
                }
            }
        } else {
            // Categorias
            item {
                SectionHeader(title = stringResource(R.string.section_categories))
                Spacer(Modifier.height(8.dp))
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
                    items(sections.categories) { categoryView ->
                        val hasGames = categoryView.availableCount > 0
                        CategoryCard(
                            name = categoryView.category.name,
                            glyph = categoryView.category.glyph,
                            countLabel = if (hasGames)
                                stringResource(R.string.home_categories_count, categoryView.availableCount)
                            else
                                stringResource(R.string.home_categories_upcoming),
                            isAvailable = hasGames,
                            onClick = { onOpenCategory(categoryView.category.id) },
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .width(140.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Populares
            item {
                SectionHeader(title = stringResource(R.string.section_popular))
                Spacer(Modifier.height(8.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                    items(sections.popular) { game ->
                        GameCard(
                            game = game,
                            onClick = { onOpenGame(game.entry.id) },
                            modifier = Modifier.padding(end = 12.dp).width(170.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Instalados
            if (sections.installed.isEmpty()) {
                item {
                    EmptyStateView(
                        glyph = "📦",
                        title = stringResource(R.string.library_empty_title),
                        subtitle = stringResource(R.string.library_empty_subtitle)
                    )
                    Spacer(Modifier.height(16.dp))
                }
            } else {
                item {
                    SectionHeader(title = stringResource(R.string.section_installed))
                    Spacer(Modifier.height(8.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                        items(sections.installed) { game ->
                            GameCard(
                                game = game,
                                onClick = { onOpenGame(game.entry.id) },
                                modifier = Modifier.padding(end = 12.dp).width(170.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // Recentes
            item {
                SectionHeader(title = stringResource(R.string.section_recent))
                Spacer(Modifier.height(8.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                    items(sections.recents) { game ->
                        GameCard(
                            game = game,
                            onClick = { onOpenGame(game.entry.id) },
                            modifier = Modifier.padding(end = 12.dp).width(170.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Downloads em andamento (Fase 3: fica funcional)
            item {
                SectionHeader(title = stringResource(R.string.section_downloads_in_progress))
                Spacer(Modifier.height(8.dp))
                if (sections.downloadsActive.isEmpty()) {
                    EmptyStateView(
                        glyph = "⬇️",
                        title = stringResource(R.string.downloads_empty_title),
                        subtitle = stringResource(R.string.downloads_empty_subtitle)
                    )
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                        items(sections.downloadsActive) { game ->
                            GameCard(
                                game = game,
                                onClick = { onOpenGame(game.entry.id) },
                                modifier = Modifier.padding(end = 12.dp).width(170.dp)
                            )
                        }
                    }
                }
            }

            // Atalho: Biblioteca offline
            item {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable(onClick = onOpenLibrary),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📚",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.home_shortcut_library_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = stringResource(R.string.home_shortcut_library_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

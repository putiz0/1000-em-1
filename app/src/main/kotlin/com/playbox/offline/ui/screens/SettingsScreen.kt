package com.playbox.offline.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playbox.offline.AppContainer
import com.playbox.offline.BuildConfig
import com.playbox.offline.LocalAppContainer
import com.playbox.offline.R
import com.playbox.offline.core.catalog.CatalogRepository
import com.playbox.offline.data.SettingsRepository
import com.playbox.offline.data.ThemePreference
import com.playbox.offline.core.designsystem.SectionHeader
import com.playbox.offline.core.designsystem.formatBytes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val packBaseUrl: String? = null,
    val occupiedBytes: Long = 0,
    val schemaVersion: Int = 0,
    val categoriesCount: Int = 0,
    val gamesCount: Int = 0
)

class SettingsViewModel(
    private val catalogRepository: CatalogRepository,
    private val settingsRepository: SettingsRepository,
    private val container: AppContainer
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        catalogRepository.snapshot,
        settingsRepository.themeFlow,
        container.packBaseUrlOverride
    ) { snap, theme, packBaseUrl ->
        SettingsUiState(
            theme = theme,
            packBaseUrl = packBaseUrl,
            occupiedBytes = snap.totalOccupiedBytes(),
            schemaVersion = snap.schemaVersion,
            categoriesCount = snap.categories.size,
            gamesCount = snap.games.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun setTheme(value: ThemePreference) {
        viewModelScope.launch { settingsRepository.setTheme(value) }
    }

    /** Salva o endereço do servidor de packs; null volta ao padrão embutido. */
    fun setPackServerBase(url: String?) {
        container.setPackServerBase(url)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val container = LocalAppContainer.current
    val viewModel: SettingsViewModel = viewModel {
        SettingsViewModel(container.catalogRepository, container.settingsRepository, container)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )

        // Tema
        SectionHeader(title = stringResource(R.string.settings_theme_section))
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.theme == ThemePreference.SYSTEM,
                onClick = { viewModel.setTheme(ThemePreference.SYSTEM) },
                label = { Text(stringResource(R.string.settings_theme_system)) }
            )
            FilterChip(
                selected = state.theme == ThemePreference.LIGHT,
                onClick = { viewModel.setTheme(ThemePreference.LIGHT) },
                label = { Text(stringResource(R.string.settings_theme_light)) }
            )
            FilterChip(
                selected = state.theme == ThemePreference.DARK,
                onClick = { viewModel.setTheme(ThemePreference.DARK) },
                label = { Text(stringResource(R.string.settings_theme_dark)) }
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))

        // Servidor de packs
        SectionHeader(
            title = stringResource(R.string.settings_server_section),
            modifier = Modifier.padding(top = 8.dp)
        )
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_server_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            val savedUrl = state.packBaseUrl ?: BuildConfig.PACK_BASE_URL
            var draft by remember { mutableStateOf<String?>(null) }
            val fieldValue = draft ?: savedUrl
            OutlinedTextField(
                value = fieldValue,
                onValueChange = { draft = it },
                label = { Text(stringResource(R.string.settings_server_field_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(
                    R.string.settings_server_current,
                    state.packBaseUrl ?: stringResource(R.string.settings_server_builtin)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    enabled = fieldValue.trim() != savedUrl,
                    onClick = {
                        viewModel.setPackServerBase(fieldValue.trim())
                        draft = null
                    }
                ) {
                    Text(stringResource(R.string.settings_server_save), maxLines = 1)
                }
                OutlinedButton(
                    enabled = state.packBaseUrl != null,
                    onClick = {
                        viewModel.setPackServerBase(null)
                        draft = null
                    }
                ) {
                    Text(stringResource(R.string.settings_server_default), maxLines = 1)
                }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))

        // Armazenamento
        SectionHeader(
            title = stringResource(R.string.settings_storage_section),
            modifier = Modifier.padding(top = 8.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    text = stringResource(
                        R.string.settings_storage_total,
                        com.playbox.offline.core.designsystem.formatBytes(state.occupiedBytes)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                var cleanupFreedBytes by remember { mutableStateOf<Long?>(null) }
                val scope = rememberCoroutineScope()
                OutlinedButton(
                    enabled = true,
                    onClick = {
                        scope.launch {
                            cleanupFreedBytes = container.downloadCoordinator.clearTemporaryFiles()
                        }
                    },
                    modifier = Modifier.width(280.dp)
                ) {
                    Text(stringResource(R.string.settings_storage_cleanup), maxLines = 1)
                }
                Text(
                    text = stringResource(R.string.settings_storage_cleanup_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                cleanupFreedBytes?.let { freed ->
                    Text(
                        text = stringResource(R.string.settings_storage_cleanup_done, formatBytes(freed)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))

        // Privacidade
        SectionHeader(
            title = stringResource(R.string.settings_privacy_section),
            modifier = Modifier.padding(top = 8.dp)
        )
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_privacy_text_1),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_privacy_text_2),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))

        // Sobre
        SectionHeader(
            title = stringResource(R.string.settings_about_section),
            modifier = Modifier.padding(top = 8.dp)
        )
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(
                    R.string.settings_about_version,
                    BuildConfig.VERSION_NAME
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.settings_about_catalog,
                    state.schemaVersion,
                    state.categoriesCount,
                    state.gamesCount
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

package com.playbox.offline.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemePreference { SYSTEM, LIGHT, DARK }

private val Context.playboxDataStore by preferencesDataStore(name = "playbox.settings")

/**
 * Preferências do usuário (tema etc.) — locais, sem sincronização.
 * Persistem de forma estável entre reinícios do aplicativo.
 */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.playboxDataStore

    private val themeKey = stringPreferencesKey("theme")
    private val packBaseUrlKey = stringPreferencesKey("pack_base_url")

    /**
     * Endereço do servidor de packs escolhido pelo usuário (Fase 5: GitHub
     * Releases). null ou vazio = usar o endereço embutido no aplicativo.
     */
    val packBaseUrlFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[packBaseUrlKey]?.trim()?.takeIf { it.isNotEmpty() }
    }

    suspend fun setPackBaseUrl(value: String?) {
        dataStore.edit { prefs ->
            val cleaned = value?.trim()
            if (cleaned.isNullOrEmpty()) {
                prefs.remove(packBaseUrlKey)
            } else {
                prefs[packBaseUrlKey] = cleaned
            }
        }
    }

    val themeFlow: Flow<ThemePreference> = dataStore.data.map { prefs ->
        prefs[themeKey]?.let { value ->
            runCatching { ThemePreference.valueOf(value) }.getOrNull()
        } ?: ThemePreference.SYSTEM
    }

    suspend fun setTheme(value: ThemePreference) {
        dataStore.edit { prefs -> prefs[themeKey] = value.name }
    }
}

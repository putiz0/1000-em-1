package com.playbox.offline.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.playbox.offline.core.designsystem.audio.AudioPrefs
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
    private val soundOnKey = stringPreferencesKey("sound_on")
    private val musicOnKey = stringPreferencesKey("music_on")

    /**
     * Endereço do servidor de packs escolhido pelo usuário (Fase 5: GitHub
     * Releases). null ou vazio = usar o endereço embutido no aplicativo.
     */
    val packBaseUrlFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[packBaseUrlKey]?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Preferências de áudio globais (Ajustes). O padrão é tudo ligado. */
    val audioFlow: Flow<AudioPrefs> = dataStore.data.map { prefs ->
        AudioPrefs(
            soundOn = prefs[soundOnKey]?.toBooleanStrictOrNull() ?: true,
            musicOn = prefs[musicOnKey]?.toBooleanStrictOrNull() ?: true
        )
    }

    suspend fun setSoundOn(value: Boolean) {
        dataStore.edit { prefs -> prefs[soundOnKey] = value.toString() }
    }

    suspend fun setMusicOn(value: Boolean) {
        dataStore.edit { prefs -> prefs[musicOnKey] = value.toString() }
    }

    /**
     * Opções da última partida por jogo (Fase dos menus pré-jogo). Cada jogo
     * persiste/gere o próprio JSON (ex.: modo, quem começa) aqui.
     */
    fun matchOptionsFlow(gameId: String): Flow<String?> =
        dataStore.data.map { prefs -> prefs[stringPreferencesKey("match_options_$gameId")] }

    suspend fun setMatchOptions(gameId: String, json: String?) {
        dataStore.edit { prefs ->
            val key = stringPreferencesKey("match_options_$gameId")
            if (json.isNullOrEmpty()) prefs.remove(key) else prefs[key] = json
        }
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

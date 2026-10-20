package com.playbox.offline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Surface
import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.playbox.offline.core.designsystem.PlayBoxTheme
import com.playbox.offline.data.ThemePreference
import com.playbox.offline.ui.navigation.AppRoot

val LocalAppContainer = compositionLocalOf<AppContainer> {
    error("AppContainer não fornecido")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as PlayBoxApplication).container

        setContent {
            val themePref by container.settingsRepository.themeFlow
                .collectAsStateWithLifecycle(initialValue = ThemePreference.SYSTEM)

            PlayBoxTheme(
                darkTheme = when (themePref) {
                    ThemePreference.SYSTEM -> isSystemInDarkTheme()
                    ThemePreference.LIGHT -> false
                    ThemePreference.DARK -> true
                }
            ) {
                Surface {
                    CompositionLocalProvider(LocalAppContainer provides container) {
                        AppRoot()
                    }
                }
            }
        }
    }
}

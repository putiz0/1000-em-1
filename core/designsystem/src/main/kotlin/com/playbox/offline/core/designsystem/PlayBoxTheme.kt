package com.playbox.offline.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF5B45E7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE7E1FF),
    onPrimaryContainer = Color(0xFF1B1147),
    secondary = Color(0xFF0D9488),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF042F2E),
    tertiary = Color(0xFFEA8C00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE9CC),
    onTertiaryContainer = Color(0xFF3F2500),
    background = Color(0xFFFCF8FF),
    onBackground = Color(0xFF1C1B20),
    surface = Color(0xFFFCF8FF),
    onSurface = Color(0xFF1C1B20),
    surfaceVariant = Color(0xFFE7E0FA),
    onSurfaceVariant = Color(0xFF48454F),
    outline = Color(0xFF78757F),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFC9BCFF),
    onPrimary = Color(0xFF2E1A7D),
    primaryContainer = Color(0xFF4532A9),
    onPrimaryContainer = Color(0xFFE7E1FF),
    secondary = Color(0xFF6BD5C7),
    onSecondary = Color(0xFF003734),
    secondaryContainer = Color(0xFF0D9488),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = Color(0xFFFFB86B),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFFB45F00),
    onTertiaryContainer = Color(0xFFFFE9CC),
    background = Color(0xFF14131A),
    onBackground = Color(0xFFE6E1E6),
    surface = Color(0xFF14131A),
    onSurface = Color(0xFFE6E1E6),
    surfaceVariant = Color(0xFF474550),
    onSurfaceVariant = Color(0xFFC8C4D0),
    outline = Color(0xFF928F99),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

/**
 * Tema do aplicativo: usa cor dinâmica do sistema (Android 12+),
 * senão aplica o tema PlayBox em claro/escuro conforme o sistema.
 */
@Composable
fun PlayBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

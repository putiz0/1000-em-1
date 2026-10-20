package com.playbox.offline.core.designsystem

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Capa provisória dos jogos (enquanto a arte definitiva não é baixada
 * junto ao pacote de conteúdo do jogo, na Fase 3).
 */
fun parseCoverColor(hex: String): Color = try {
    Color(AndroidColor.parseColor(hex))
} catch (_: IllegalArgumentException) {
    Color(0xFF64748B)
}

private fun coverColors(colors: List<String>): List<Color> {
    val parsed = colors.mapNotNull { hex ->
        runCatching { parseCoverColor(hex) }.getOrNull()
    }
    val valid = parsed.takeIf { it.size >= 2 }
        ?: listOf(Color(0xFF64748B), Color(0xFF1E293B))
    return valid
}

@Composable
fun CoverBox(
    glyph: String,
    coverColors: List<String>,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp
) {
    val colors = coverColors(coverColors)
    Box(
        modifier = modifier
            .background(
                brush = Brush.linearGradient(colors),
                shape = RoundedCornerShape(cornerRadius)
            )
    ) {
        Text(
            text = glyph.ifEmpty { "🎮" },
            fontSize = 40.sp,
            color = Color.White,
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
        )
    }
}

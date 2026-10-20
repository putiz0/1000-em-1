package com.playbox.offline.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/**
 * Ícones do aplicativo, definidos no designsystem para evitar acesso
 * direto ao R de módulos externos (R classes não transitivas).
 */
enum class PlayBoxIcon(val resId: Int) {
    HOME(R.drawable.ic_home),
    LIBRARY(R.drawable.ic_library),
    DOWNLOADS(R.drawable.ic_downloads),
    SETTINGS(R.drawable.ic_settings),
    SEARCH(R.drawable.ic_search),
    PLAY(R.drawable.ic_play),
    BACK(R.drawable.ic_back)
}

@Composable
fun PlayBoxIconPainter(icon: PlayBoxIcon): Painter = androidx.compose.ui.res.painterResource(icon.resId)

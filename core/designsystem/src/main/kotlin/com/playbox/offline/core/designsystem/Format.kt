package com.playbox.offline.core.designsystem

import java.util.Locale

/**
 * Formatação de tamanhos de download em pt-BR.
 * Base decimal (1 MB = 1 000 000 bytes), o padrão usado pela Play Store.
 */
fun formatBytes(bytes: Long): String {
    val locale = Locale("pt", "BR")
    return when {
        bytes >= 1_000_000_000L -> String.format(locale, "%.2f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000L -> String.format(locale, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000L -> String.format(locale, "%.0f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
}

package com.playbox.offline.core.catalog

import com.playbox.offline.core.catalog.model.CatalogData
import com.playbox.offline.core.catalog.model.CategoryInfo
import com.playbox.offline.core.catalog.model.GameCatalogEntry
import com.playbox.offline.core.database.GameInstallEntity
import com.playbox.offline.core.database.InstallStatus

/**
 * Estado de um jogo = dados do catálogo + estado de instalação local
 * (Room). Quando não há registro no banco, o jogo está "Disponível
 * para baixar" (ou "Em breve", quando available = false).
 */
data class GameWithStatus(
    val entry: GameCatalogEntry,
    val status: InstallStatus?,
    val installedVersion: Long = 0,
    val downloadedBytes: Long = 0,
    /** Tamanho total do pack (real, vindo do catálogo remoto; 0 = desconhecido). */
    val totalBytes: Long = 0,
    val occupiedBytes: Long = 0,
    val lastError: String? = null
) {
    val available: Boolean get() = entry.available
    val isInstalled: Boolean
        get() = status == InstallStatus.COMPLETED || status == InstallStatus.UPDATE_AVAILABLE
    val isDownloadActive: Boolean
        get() = status == InstallStatus.QUEUED ||
            status == InstallStatus.DOWNLOADING ||
            status == InstallStatus.PAUSED
    /** Bytes a usar como total no progresso: real (Room) > estimado (catálogo). */
    val progressTotalBytes: Long
        get() = if (totalBytes > 0) totalBytes else entry.estimatedBytes
}

data class CategoryWithCounting(
    val category: CategoryInfo,
    val availableCount: Int,
    val upcomingCount: Int
)

data class CatalogSnapshot(
    val schemaVersion: Int,
    val categories: List<CategoryInfo>,
    val games: List<GameWithStatus>
) {

    fun category(id: String): CategoryInfo? = categories.firstOrNull { it.id == id }

    fun byCategory(categoryId: String): List<GameWithStatus> =
        games.filter { it.entry.categoryId == categoryId }

    fun game(gameId: String): GameWithStatus? = games.firstOrNull { it.entry.id == gameId }

    fun availableGames(): List<GameWithStatus> =
        games.filter { it.available }

    /** "Populares": ordenação determinística por popularidade do catálogo. */
    fun popular(limit: Int = 10): List<GameWithStatus> =
        availableGames().sortedBy { it.entry.popularity }.take(limit)

    /** "Recentemente adicionado": games ordenados por addedOrder decrescente. */
    fun recentlyAdded(limit: Int = 5): List<GameWithStatus> =
        games.sortedByDescending { it.entry.addedOrder }.take(limit)

    fun installed(): List<GameWithStatus> = games.filter { it.isInstalled }

    fun downloadsActive(): List<GameWithStatus> =
        games.filter {
            it.isDownloadActive ||
                it.status == InstallStatus.FAILED ||
                it.status == InstallStatus.CORRUPTED
        }

    /** Espaço total ocupado por jogos baixados (tela de armazenamento). */
    fun totalOccupiedBytes(): Long =
        games.sumOf { if (it.isInstalled) it.occupiedBytes else 0L }

    fun categoriesWithCounts(): List<CategoryWithCounting> = categories.map { cat ->
        val group = byCategory(cat.id)
        CategoryWithCounting(
            category = cat,
            availableCount = group.count { it.available },
            upcomingCount = group.count { !it.available }
        )
    }

    companion object {
        fun of(
            data: CatalogData,
            installs: List<GameInstallEntity>
        ): CatalogSnapshot {
            val byId = installs.associateBy { it.gameId }
            val games = data.games.map { entry ->
                val row = byId[entry.id]
                GameWithStatus(
                    entry = entry,
                    status = row?.status,
                    installedVersion = row?.installedVersion ?: 0,
                    downloadedBytes = row?.downloadedBytes ?: 0,
                    totalBytes = row?.totalBytes ?: 0,
                    occupiedBytes = row?.occupiedBytes ?: 0,
                    lastError = row?.lastError
                )
            }
            return CatalogSnapshot(
                schemaVersion = data.schemaVersion,
                categories = data.categories,
                games = games
            )
        }
    }
}

package com.playbox.offline.core.catalog

import android.content.Context
import com.playbox.offline.core.database.PlayBoxDatabase
import com.playbox.offline.core.gameapi.RemoteCatalog
import com.playbox.offline.core.gameapi.RemoteCatalogSource
import com.playbox.offline.core.gameapi.joinUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Fonte única de verdade do catálogo para a interface:
 * catálogo embutido (assets) + estados de instalação (Room) + catálogo
 * remoto de packs (URL/tamanho/hash reais — via [RemoteCatalogSource]).
 *
 * O catálogo remoto NÃO adiciona nem remove jogos por conta própria: só
 * enriquece as entradas do catálogo embutido com dados de download. Assim
 * nenhum jogo fictício pode aparecer na UI por causa do remoto.
 */
class CatalogRepository(
    context: Context,
    database: PlayBoxDatabase,
    private val remoteSource: RemoteCatalogSource? = null,
    catalogScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val dao = database.gameInstallDao()
    private val catalogData by lazy { CatalogAssets.load(context) }

    private val baseFlow = dao.observeAll()
        .map { installs -> CatalogSnapshot.of(catalogData, installs) }
        .flowOn(Dispatchers.IO)

    val snapshot: StateFlow<CatalogSnapshot> =
        if (remoteSource == null) {
            baseFlow.stateIn(
                scope = catalogScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = CatalogSnapshot.of(catalogData, installs = emptyList())
            )
        } else {
            combine(baseFlow, remoteSource.remoteCatalog) { snap, remote ->
                if (remote == null) snap else snap.withRemotePackInfo(remote)
            }
                .flowOn(Dispatchers.IO)
                .stateIn(
                    scope = catalogScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = CatalogSnapshot.of(catalogData, installs = emptyList())
                )
        }

    /** Versão do schema do catálogo embutido (diagnóstico em Ajustes). */
    val embeddedSchemaVersion: Int
        get() = catalogData.schemaVersion

    /**
     * Sobrescreve a entrada de cada jogo com os dados do catálogo remoto:
     * URL de download, SHA-256 do zip, versão de conteúdo e tamanho real.
     */
    private fun CatalogSnapshot.withRemotePackInfo(remote: RemoteCatalog): CatalogSnapshot {
        val packs = remote.packs.associateBy { it.gameId }
        val enriched = games.map { game ->
            val pack = packs[game.entry.id]
            if (pack == null) game else game.copy(
                entry = game.entry.copy(
                    contentVersion = pack.contentVersion,
                    downloadUrl = joinUrl(remote.baseUrl, pack.fileName),
                    sha256 = pack.packSha256,
                    estimatedBytes = pack.packBytes
                )
            )
        }
        return copy(games = enriched)
    }
}

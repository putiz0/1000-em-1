package com.playbox.offline.core.catalog

import android.content.Context
import com.playbox.offline.core.catalog.model.CatalogData

/**
 * Carrega o catálogo embutido no APK (assets/catalog/catalog.json).
 * Esse catálogo garante que a biblioteca continue acessível mesmo sem
 * internet, no primeiro uso do aplicativo.
 */
object CatalogAssets {

    const val ASSET_PATH = "catalog/catalog.json"

    fun load(context: Context): CatalogData {
        context.assets.open(ASSET_PATH).use { stream ->
            val text = stream.bufferedReader(Charsets.UTF_8).readText()
            return CatalogJson.parse(text)
        }
    }
}

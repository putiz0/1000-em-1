package com.playbox.offline.core.catalog

import com.playbox.offline.core.catalog.model.CatalogData
import kotlinx.serialization.json.Json

/**
 * Parser do catálogo. Puro (sem dependência de Android) para permitir
 * testes de unidade na JVM e reuso pelo catálogo remoto na Fase 3.
 */
object CatalogJson {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun parse(text: String): CatalogData = json.decodeFromString(CatalogData.serializer(), text)

    fun encode(data: CatalogData): String = json.encodeToString(CatalogData.serializer(), data)
}

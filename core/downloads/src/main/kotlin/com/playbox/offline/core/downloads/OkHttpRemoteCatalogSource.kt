package com.playbox.offline.core.downloads

import com.playbox.offline.core.gameapi.RemoteCatalog
import com.playbox.offline.core.gameapi.RemoteCatalogSource
import com.playbox.offline.core.gameapi.RemoteCatalogUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client do catálogo remoto de packs (OkHttp).
 * A URL é provida por lambda, então o app pode apontar o servidor em
 * runtime (dev local hoje; GitHub Releases na Fase 5).
 */
class OkHttpRemoteCatalogSource(
    private val catalogUrl: () -> String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : RemoteCatalogSource {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val _remoteCatalog = MutableStateFlow<RemoteCatalog?>(null)
    override val remoteCatalog: StateFlow<RemoteCatalog?> = _remoteCatalog

    override suspend fun refresh(): RemoteCatalog? {
        val text = fetchCatalogText()
        val catalog = parseCatalog(text)
        _remoteCatalog.value = catalog
        return catalog
    }

    /** Executa o GET do catálogo (serializado por mutex; thread de E/S). */
    private suspend fun fetchCatalogText(): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val request = Request.Builder().url(catalogUrl()).build()
            val response: Response = client.newCall(request).execute()
            try {
                if (!response.isSuccessful) {
                    throw RemoteCatalogUnavailableException(
                        "Servidor de packs respondeu HTTP ${response.code} ao ler o catálogo."
                    )
                }
                response.body?.string()
                    ?: throw RemoteCatalogUnavailableException("Catálogo de packs vazio.")
            } finally {
                response.close()
            }
        }
    }

    /** Parse com validações (+ tradução de erros para mensagem entendível). */
    private fun parseCatalog(text: String): RemoteCatalog {
        val parsed: RemoteCatalog = try {
            json.decodeFromString<RemoteCatalog>(text)
        } catch (e: SerializationException) {
            throw RemoteCatalogUnavailableException("Catálogo de packs em formato inválido.")
        } catch (e: IllegalArgumentException) {
            throw RemoteCatalogUnavailableException("Catálogo de packs em formato inválido.")
        }

        if (parsed.schemaVersion != SCHEMA_VERSION) {
            throw RemoteCatalogUnavailableException(
                "Versão de catálogo remoto não suportada (${parsed.schemaVersion})."
            )
        }
        return parsed
    }

    private companion object {
        const val SCHEMA_VERSION = 1
    }
}

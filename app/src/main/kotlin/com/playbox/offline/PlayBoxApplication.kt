package com.playbox.offline

import android.app.Application
import android.content.Context
import com.playbox.offline.core.catalog.CatalogRepository
import com.playbox.offline.core.database.PlayBoxDatabase
import com.playbox.offline.core.database.PlayBoxDatabaseProvider
import com.playbox.offline.core.downloads.DownloadCoordinator
import com.playbox.offline.core.downloads.HttpPackDownloader
import com.playbox.offline.core.downloads.OkHttpRemoteCatalogSource
import com.playbox.offline.core.packs.PackManager
import com.playbox.offline.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Contêiner manual simples (sem Hilt) — agora com o motor de downloads da
 * Fase 3: catálogo remoto, packs instalados e coordenador de estado.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: PlayBoxDatabase = PlayBoxDatabaseProvider.get(appContext)

    /** Um único OkHttp para catálogo remoto + downloads de packs. */
    private val packHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * Endereço do servidor de packs escolhido em Ajustes (Fase 5: GitHub
     * Releases). null = usar o endereço embutido (desenvolvimento: PC via
     * `adb reverse`). O lambda do catálogo lê este fluxo a cada chamada,
     * então a troca vale imediatamente, sem reiniciar o aplicativo.
     */
    val packBaseUrlOverride = MutableStateFlow<String?>(null)

    val remoteCatalogSource = OkHttpRemoteCatalogSource(
        catalogUrl = {
            (packBaseUrlOverride.value ?: BuildConfig.PACK_BASE_URL)
                .trimEnd('/') + "/remote-catalog.json"
        },
        client = packHttpClient
    )

    val packManager = PackManager(appContext.filesDir)

    val downloadCoordinator = DownloadCoordinator(
        packManager = packManager,
        gameInstallDao = database.gameInstallDao(),
        remoteSource = remoteCatalogSource,
        downloader = HttpPackDownloader(packHttpClient)
    )

    val catalogRepository = CatalogRepository(
        appContext,
        database,
        remoteSource = remoteCatalogSource
    )

    val settingsRepository = SettingsRepository(appContext)

    init {
        appScope.launch {
            // carrega o endereço do servidor salvo (se houver) ANTES da primeira
            // leitura do catálogo, para o refresh inicial usar o endereço certo
            packBaseUrlOverride.value = settingsRepository.packBaseUrlFlow.first()
            // primeira carga do catálogo remoto + sincroniza UPDATE_AVAILABLE
            try {
                downloadCoordinator.refreshRemoteCatalog()
            } catch (e: Exception) {
                // falha esperada quando não há internet/servidor: tudo continua
                // funcional offline e o usuário baixa quando houver conexão.
            }
        }
        appScope.launch {
            // recupera estados órfãos se o processo foi morto durante um download
            downloadCoordinator.reconcileInterruptedDownloads()
        }
        packManager.ensureBaseDirs()
    }

    /** Troca o servidor de packs (Ajustes). null/vazio volta ao endereço embutido. */
    fun setPackServerBase(url: String?) {
        val cleaned = url?.trim()?.takeIf { it.isNotEmpty() }
        packBaseUrlOverride.value = cleaned
        appScope.launch { settingsRepository.setPackBaseUrl(cleaned) }
    }
}

class PlayBoxApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

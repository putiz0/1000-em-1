package com.playbox.offline.core.downloads

import com.playbox.offline.core.database.GameInstallDao
import com.playbox.offline.core.database.GameInstallEntity
import com.playbox.offline.core.database.InstallStatus
import com.playbox.offline.core.gameapi.RemoteCatalog
import com.playbox.offline.core.gameapi.RemoteCatalogSource
import com.playbox.offline.core.gameapi.RemoteCatalogUnavailableException
import com.playbox.offline.core.gameapi.RemotePack
import com.playbox.offline.core.packs.PackManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** DAO em memória para testes no JVM (Room real exige Android). */
class FakeGameInstallDao : GameInstallDao {

    private val rows = ConcurrentHashMap<String, GameInstallEntity>()
    private val rowsFlow = MutableStateFlow<List<GameInstallEntity>>(emptyList())

    private fun publish() {
        rowsFlow.value = rows.values.sortedBy { it.gameId }
    }

    override fun observeAll(): Flow<List<GameInstallEntity>> = rowsFlow

    override fun observe(gameId: String): Flow<GameInstallEntity?> = rowsFlow.map { list ->
        list.firstOrNull { it.gameId == gameId }
    }

    override suspend fun find(gameId: String): GameInstallEntity? = rows[gameId]

    override suspend fun upsert(entity: GameInstallEntity) {
        rows[entity.gameId] = entity
        publish()
    }

    override suspend fun upsertAll(entities: List<GameInstallEntity>) {
        entities.forEach { rows[it.gameId] = it }
        publish()
    }

    override suspend fun delete(entity: GameInstallEntity) {
        rows.remove(entity.gameId)
        publish()
    }

    override suspend fun deleteByGameId(gameId: String) {
        rows.remove(gameId)
        publish()
    }

    override suspend fun clear() {
        rows.clear()
        publish()
    }
}

/** Fonte remota fixa para os testes (sem rede). */
class FakeRemoteCatalogSource(
    private val catalog: RemoteCatalog,
    var failure: RemoteCatalogUnavailableException? = null,
    /** Falha "bruta" real (ex.: java.net.ConnectException do OkHttp — o
     *  app derrubava porque essa IOException escapava do coordenador). */
    var rawFailure: Throwable? = null
) : RemoteCatalogSource {

    override val remoteCatalog = MutableStateFlow<RemoteCatalog?>(catalog)

    override suspend fun refresh(): RemoteCatalog? {
        rawFailure?.let { throw it }
        failure?.let { throw it }
        return catalog
    }
}

/**
 * Teste de integração do coordenador: worker real (corrotina), downloader
 * real contra servidor HTTP real do JDK, verificação real, banco fake.
 */
class DownloadCoordinatorTest {

    private fun tempDir(): File = Files.createTempDirectory("playbox-coord").toFile()

    private fun sha256(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private suspend fun buildPack(baseDir: File, gameId: String, contentVersion: Int = 1): ByteArray {
        // conteúdo do pack: um arquivo de regras + um json de dados
        val regras = "Regras do $gameId (versão $contentVersion) — texto de conteúdo íntegro.\n".encodeToByteArray()
        val dados = "{\"layout\":\"ok\"}".encodeToByteArray()
        val manifest = com.playbox.offline.core.gameapi.PackManifest(
            gameId = gameId,
            contentVersion = contentVersion,
            files = listOf(
                com.playbox.offline.core.gameapi.ManifestFile("regras.txt", regras.size.toLong(), sha256(regras)),
                com.playbox.offline.core.gameapi.ManifestFile("dados.json", dados.size.toLong(), sha256(dados))
            )
        )
        val manifestBytes = kotlinx.serialization.json.Json
            .encodeToString(com.playbox.offline.core.gameapi.PackManifest.serializer(), manifest)
            .encodeToByteArray()

        val buffer = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(buffer).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
            zip.write(manifestBytes)
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("regras.txt"))
            zip.write(regras)
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("dados.json"))
            zip.write(dados)
            zip.closeEntry()
        }
        return buffer.toByteArray()
    }

    /** monta coordenador + servidor + fake dao; retorno fecha o servidor no fim. */
    private class Rig(
        val coordinator: DownloadCoordinator,
        val dao: FakeGameInstallDao,
        val packManager: PackManager,
        val server: HttpTestServer,
        val catalog: RemoteCatalog
    ) : AutoCloseable {
        override fun close() = server.close()
    }

    private fun newRig(
        packBytes: ByteArray,
        catalogSha: String? = null,
        freeSpace: Long = Long.MAX_VALUE,
        remoteFailure: RemoteCatalogUnavailableException? = null,
        remoteRawFailure: Throwable? = null,
        gameId: String = "damas",
        contentVersion: Int = 1
    ): Rig {
        val baseDir = tempDir()
        val packManager = PackManager(baseDir)
        val dao = FakeGameInstallDao()
        val server = HttpTestServer(packBytes, honorRange = true)
        val packSha = catalogSha ?: sha256(packBytes)
        val catalog = RemoteCatalog(
            schemaVersion = 1,
            baseUrl = "http://127.0.0.1:${server.port}/files/",
            packs = listOf(
                RemotePack(
                    gameId = gameId,
                    contentVersion = contentVersion,
                    fileName = "packs/pack-$gameId-$contentVersion.zip",
                    packBytes = packBytes.size.toLong(),
                    packSha256 = packSha
                )
            )
        )
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        val coordinator = DownloadCoordinator(
            packManager = packManager,
            gameInstallDao = dao,
            remoteSource = FakeRemoteCatalogSource(catalog, remoteFailure, remoteRawFailure),
            downloader = HttpPackDownloader(client),
            freeSpaceChecker = { freeSpace }
        )
        return Rig(coordinator, dao, packManager, server, catalog)
    }

    @Test
    fun `download feliz instala o pack e marca COMPLETED`() = runBlocking {
        val packBytes = buildPack(tempDir(), "damas")
        newRig(packBytes).use { rig ->
            rig.coordinator.startOrResumeDownload("damas")
            rig.coordinator.awaitIdle("damas")

            val row = rig.dao.find("damas")
            assertNotNull(row)
            assertEquals(InstallStatus.COMPLETED, row.status)
            assertEquals(1, row.installedVersion)
            assertEquals(packBytes.size.toLong(), row.totalBytes)
            assertTrue(row.occupiedBytes > 0)
            assertEquals(null, row.lastError)

            val installed = File(rig.packManager.packsRoot, "damas/1/regras.txt")
            assertTrue(installed.isFile)
            assertTrue(File(rig.packManager.packsRoot, "damas/1/manifest.json").isFile)
            // parciais eliminados depois da instalação
            assertTrue(!File(rig.packManager.tempRoot, "damas-1.zip").exists())
        }
    }

    @Test
    fun `hash divergente marca CORRUPTED e nada é instalado`() = runBlocking {
        val packBytes = buildPack(tempDir(), "damas")
        newRig(packBytes, catalogSha = "ab".repeat(32)).use { rig ->
            rig.coordinator.startOrResumeDownload("damas")
            rig.coordinator.awaitIdle("damas")

            val row = rig.dao.find("damas")
            assertNotNull(row)
            assertEquals(InstallStatus.CORRUPTED, row.status)
            assertTrue(row.lastError!!.contains("SHA-256"))
            assertTrue(!File(rig.packManager.packsRoot, "damas/1").exists())
        }
    }

    @Test
    fun `espaco insuficiente marca FAILED sem iniciar download`() = runBlocking {
        val packBytes = buildPack(tempDir(), "damas")
        newRig(packBytes, freeSpace = 0L).use { rig ->
            rig.coordinator.startOrResumeDownload("damas")
            rig.coordinator.awaitIdle("damas")

            val row = rig.dao.find("damas")
            assertNotNull(row)
            assertEquals(InstallStatus.FAILED, row.status)
            assertTrue(row.lastError!!.contains("Espaço insuficiente"))
        }
    }

    @Test
    fun `catalogo remoto indisponivel marca FAILED com mensagem`() = runBlocking {
        val packBytes = buildPack(tempDir(), "damas")
        newRig(
            packBytes,
            remoteFailure = RemoteCatalogUnavailableException("Sem conexão com o servidor de packs.")
        ).use { rig ->
            rig.coordinator.startOrResumeDownload("damas")
            rig.coordinator.awaitIdle("damas")

            val row = rig.dao.find("damas")
            assertNotNull(row)
            assertEquals(InstallStatus.FAILED, row.status)
            assertTrue(row.lastError!!.contains("Sem conexão"))
        }
    }

    @Test
    fun `falha de conexao bruta no catalogo marca FAILED em vez de derrubar o app`() = runBlocking {
        // antes desta correção, um java.net.ConnectException (servidor de dev
        // desligado ou adb reverse não ligado) estourava na tela e fechava o app
        val packBytes = buildPack(tempDir(), "damas")
        newRig(
            packBytes,
            remoteRawFailure = java.net.ConnectException("Failed to connect to localhost/127.0.0.1:9080")
        ).use { rig ->
            // sem estourar: executa e grava FAILED compreensível
            rig.coordinator.startOrResumeDownload("damas")
            rig.coordinator.awaitIdle("damas")

            val row = rig.dao.find("damas")
            assertNotNull(row)
            assertEquals(InstallStatus.FAILED, row.status)
            assertTrue(row.lastError!!.contains("Servidor de packs inacessível"))
        }
    }

    @Test
    fun `jogo sem pack no catalogo remoto marca FAILED`() = runBlocking {
        val packBytes = buildPack(tempDir(), "xadrez")
        newRig(packBytes).use { rig ->
            rig.coordinator.startOrResumeDownload("domino") // não está no catálogo
            rig.coordinator.awaitIdle("domino")

            val row = rig.dao.find("domino")
            assertNotNull(row)
            assertEquals(InstallStatus.FAILED, row.status)
            assertTrue(row.lastError!!.contains("pacote publicado"))
        }
    }

    @Test
    fun `cancelar jogo COMPLETO via cancelDownload nao apaga instalacao`() = runBlocking {
        val packBytes = buildPack(tempDir(), "damas")
        newRig(packBytes).use { rig ->
            rig.coordinator.startOrResumeDownload("damas")
            rig.coordinator.awaitIdle("damas")
            assertEquals(InstallStatus.COMPLETED, rig.dao.find("damas")?.status)

            // cancelar sem worker só deve mexer em estados de download
            rig.coordinator.cancelDownload("damas")
            rig.coordinator.awaitIdle("damas")

            assertEquals(InstallStatus.COMPLETED, rig.dao.find("damas")?.status)
            assertTrue(File(rig.packManager.packsRoot, "damas/1/regras.txt").isFile)
        }
    }

    @Test
    fun `deleteInstalledPack apaga arquivos e volta a ficar disponivel`() = runBlocking {
        val packBytes = buildPack(tempDir(), "damas")
        newRig(packBytes).use { rig ->
            rig.coordinator.startOrResumeDownload("damas")
            rig.coordinator.awaitIdle("damas")
            assertEquals(InstallStatus.COMPLETED, rig.dao.find("damas")?.status)

            rig.coordinator.deleteInstalledPack("damas")
            rig.coordinator.awaitIdle("damas")

            assertEquals(null, rig.dao.find("damas"))
            assertTrue(!File(rig.packManager.packsRoot, "damas").exists())
        }
    }

    @Test
    fun `syncUpdateStates marca UPDATE_AVAILABLE quando ha versao nova`() = runBlocking {
        val packBytes = buildPack(tempDir(), "damas", contentVersion = 2)
        newRig(packBytes, contentVersion = 2).use { rig ->
            // simula versão 1 instalada
            rig.dao.upsert(
                GameInstallEntity(
                    gameId = "damas",
                    status = InstallStatus.COMPLETED,
                    installedVersion = 1
                )
            )

            rig.coordinator.syncUpdateStates(rig.catalog)
            assertEquals(InstallStatus.UPDATE_AVAILABLE, rig.dao.find("damas")?.status)
        }
    }

    @Test
    fun `totalBytes sera o tamanho REAL do zip do catalogo remoto`() = runBlocking {
        val packBytes = buildPack(tempDir(), "damas")
        newRig(packBytes).use { rig ->
            rig.coordinator.startOrResumeDownload("damas")
            rig.coordinator.awaitIdle("damas")

            val row = rig.dao.find("damas")
            assertEquals(packBytes.size.toLong(), row?.totalBytes)
        }
    }
}

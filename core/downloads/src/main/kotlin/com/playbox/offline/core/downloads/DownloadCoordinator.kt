package com.playbox.offline.core.downloads

import com.playbox.offline.core.database.GameInstallDao
import com.playbox.offline.core.database.GameInstallEntity
import com.playbox.offline.core.database.InstallStatus
import com.playbox.offline.core.gameapi.RemoteCatalog
import com.playbox.offline.core.gameapi.RemoteCatalogSource
import com.playbox.offline.core.gameapi.RemoteCatalogUnavailableException
import com.playbox.offline.core.gameapi.RemotePack
import com.playbox.offline.core.gameapi.joinUrl
import com.playbox.offline.core.packs.PackManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Orquestrador de downloads de packs de conteúdo.
 *
 * Máquina de estados por jogo (coluna `status` no Room):
 *
 *   (linha ausente no banco)   => Disponível para baixar
 *   QUEUED -> DOWNLOADING -> COMPLETED
 *        |            \--> PAUSED (retomável; offset preservado)
 *        '---> FAILED     (rede/espaço; retomável pelo offset)
 *   verificação falhada     => CORRUPTED (recomeça do zero)
 *   COMPLETED + versão nova no catálogo => UPDATE_AVAILABLE
 *
 * Regras duras (não negociáveis):
 *  - COMPLETED só chega ao banco depois do SHA-256 do zip conferir
 *    E de cada arquivo interno conferir com o `manifest.json`;
 *  - falha JAMAIS instala o jogo (dados parciais ficam em download-temp);
 *  - remoção apaga os dados locais; o jogo volta a "Disponível";
 *  - downloads rodam em fila sequencial (1 por vez) por gentileza à rede.
 */
class DownloadCoordinator(
    private val packManager: PackManager,
    private val gameInstallDao: GameInstallDao,
    private val remoteSource: RemoteCatalogSource,
    private val downloader: HttpPackDownloader,
    private val freeSpaceChecker: FreeSpaceChecker = AndroidFreeSpaceChecker(),
    private val workerScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    private val slots = ConcurrentHashMap<String, Slot>()

    /** Fila sequencial de downloads (1 por vez). */
    private val queue = Semaphore(permits = 1)

    private enum class CancelMode { PAUSE, REMOVE }

    private class Slot {
        @Volatile var job: Job? = null
        @Volatile var cancelMode: CancelMode? = null
        /** estado do banco antes de o download começar (para cancelar uma ATUALIZAÇÃO). */
        @Volatile var previousRow: GameInstallEntity? = null

        // throttle de gravação de progresso (só o worker toca)
        var lastProgressBytes: Long = 0
        var lastProgressAtMs: Long = 0
    }

    // ==================================================================
    // Ações chamadas pelas telas
    // ==================================================================

    /**
     * Baixar ou retomar o jogo (QUEUED/PAUSED/FAILED vá para o worker).
     * Falhas de pré-checagem gravam FAILED com mensagem compreensível;
     * nada é instalado nesses casos.
     */
    suspend fun startOrResumeDownload(gameId: String) = withContext(Dispatchers.IO) {
        val slot = slots.getOrPut(gameId) { Slot() }
        if (slot.job?.isActive == true) return@withContext // já em andamento

        val catalog = try {
            remoteSource.refresh()
                ?: throw RemoteCatalogUnavailableException("Servidor de packs indisponível.")
        } catch (e: RemoteCatalogUnavailableException) {
            writeFailureRow(gameId, prev = null, keepOffset = false, status = InstallStatus.FAILED, message = e.message ?: "")
            return@withContext
        } catch (e: IOException) {
            // Conexão recusada/fora do ar (ex.: servidor de dev parado, sem
            // internet ou sem `adb reverse`): grava FAILED entendível em vez
            // de deixar a IOException estourar na tela.
            writeFailureRow(
                gameId, prev = null, keepOffset = false, status = InstallStatus.FAILED,
                message = "Servidor de packs inacessível (sem rede ou servidor desligado). Tente novamente."
            )
            return@withContext
        }

        val pack = catalog.pack(gameId)
        if (pack == null) {
            writeFailureRow(
                gameId, prev = null, keepOffset = false, status = InstallStatus.FAILED,
                message = "Este jogo ainda não tem pacote publicado no catálogo de downloads."
            )
            return@withContext
        }

        val packUrl = joinUrl(catalog.baseUrl, pack.fileName)
        if (packUrl.toHttpUrlOrNull() == null) {
            writeFailureRow(
                gameId, prev = null, keepOffset = false, status = InstallStatus.FAILED,
                message = "Servidor de packs mal configurado (URL inválida)."
            )
            return@withContext
        }

        val prevRow = gameInstallDao.find(gameId)
        slot.previousRow = prevRow

        // já instalado com a versão mais atual: nada a fazer
        if (prevRow != null &&
            (prevRow.status == InstallStatus.COMPLETED || prevRow.status == InstallStatus.UPDATE_AVAILABLE) &&
            prevRow.installedVersion >= pack.contentVersion
        ) {
            return@withContext
        }

        // manter a posição parcial apenas se o pacote é o mesmo (versão e tamanho)
        val resumeOffset = prevRow?.takeIf {
            (it.status == InstallStatus.PAUSED || it.status == InstallStatus.FAILED ||
                it.status == InstallStatus.DOWNLOADING || it.status == InstallStatus.QUEUED) &&
                it.totalBytes == pack.packBytes && it.installedVersion == pack.contentVersion.toLong()
        }?.downloadedBytes ?: 0L

        // estado CORRUPTED ou recomeço do zero => descarta parciais e pack baixado
        if (prevRow?.status == InstallStatus.CORRUPTED || resumeOffset == 0L) {
            clearGameTempArtifacts(gameId)
        }

        // checagem de espaço: download + descompactado + margem de arredondamento
        val neededBytes = pack.packBytes * 3
        val freeBytes = freeSpaceChecker.availableBytes(packManager.packsRoot.parentFile ?: File("."))
        if (freeBytes < neededBytes) {
            writeFailureRow(
                gameId = gameId,
                prev = prevRow,
                keepOffset = resumeOffset > 0,
                status = InstallStatus.FAILED,
                message = "Espaço insuficiente: o download precisa de cerca de ${humanBytes(neededBytes)} livres. " +
                    "Remova um jogo ou libere espaço e tente de novo."
            )
            return@withContext
        }

        gameInstallDao.upsert(
            GameInstallEntity(
                gameId = gameId,
                status = InstallStatus.QUEUED,
                // durante ATUALIZAÇÃO, installedVersion só muda na conclusão
                installedVersion = if (prevRow?.status == InstallStatus.UPDATE_AVAILABLE) prevRow.installedVersion
                else pack.contentVersion.toLong(),
                downloadedBytes = resumeOffset,
                totalBytes = pack.packBytes,
                occupiedBytes = prevRow?.occupiedBytes ?: 0,
                lastError = null,
                lastUpdatedAt = nowMs()
            )
        )

        slot.cancelMode = null
        slot.lastProgressBytes = resumeOffset
        slot.lastProgressAtMs = 0
        slot.job = workerScope.launch {
            try {
                queue.withPermit {
                    runDownload(gameId, pack, packUrl, resumeOffset)
                }
            } catch (e: CancellationException) {
                handleCancellation(slot, gameId)
                throw e
            } finally {
                slot.job = null
            }
        }
    }

    /** Pausa: cancela a transferência e preserva os bytes já baixados. */
    suspend fun pauseDownload(gameId: String) {
        val slot = slots[gameId]
        if (slot?.job?.isActive == true) {
            slot.cancelMode = CancelMode.PAUSE
            downloader.cancelCurrent()
            slot.job?.cancel()
            return
        }
        // sem job vivo: normaliza estados órfãos (QUEUED/DOWNLOADING falsos)
        val row = gameInstallDao.find(gameId) ?: return
        if (row.status == InstallStatus.DOWNLOADING || row.status == InstallStatus.QUEUED) {
            gameInstallDao.upsert(row.copy(status = InstallStatus.PAUSED, lastError = null, lastUpdatedAt = nowMs()))
        }
    }

    /**
     * Cancela e sai da fila: apaga arquivos parciais, volta a "Disponível".
     * Cancelando uma ATUALIZAÇÃO em andamento, a versão instalada é preservada.
     */
    suspend fun cancelDownload(gameId: String) {
        val slot = slots[gameId]
        if (slot?.job?.isActive == true) {
            slot.cancelMode = CancelMode.REMOVE
            downloader.cancelCurrent()
            slot.job?.cancel()
            return
        }
        val row = gameInstallDao.find(gameId)
        if (row?.isInstalledRow() == true) {
            // COMPLETO não é cancelado por aqui: a remoção correta é deleteInstalledPack
            return
        }
        clearGameTempArtifacts(gameId)
        gameInstallDao.deleteByGameId(gameId)
    }

    /** Remove o jogo (instalado ou inacabado) — UI pede confirmação antes. */
    suspend fun deleteInstalledPack(gameId: String) {
        val slot = slots[gameId]
        if (slot?.job?.isActive == true) {
            slot.cancelMode = CancelMode.REMOVE
            downloader.cancelCurrent()
            slot.job?.cancel()
            awaitIdle(gameId)
        }
        packManager.deleteRecursively(File(packManager.packsRoot, gameId))
        clearGameTempArtifacts(gameId)
        gameInstallDao.deleteByGameId(gameId)
    }

    /**
     * Recarrega o catálogo remoto e sincroniza estados UPDATE_AVAILABLE.
     * Chamado no arranque do app e antes de cada download.
     */
    suspend fun refreshRemoteCatalog(): RemoteCatalog? {
        val catalog = try {
            remoteSource.refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // indisponível (rede/servidor/parse): os callers tratam null
        } ?: return null
        syncUpdateStates(catalog)
        return catalog
    }

    /** COMPLETED com versão anterior no catálogo => UPDATE_AVAILABLE (e vice-versa). */
    suspend fun syncUpdateStates(catalog: RemoteCatalog) {
        for (pack in catalog.packs) {
            val row = gameInstallDao.find(pack.gameId) ?: continue
            val newStatus = when (row.status) {
                InstallStatus.COMPLETED, InstallStatus.UPDATE_AVAILABLE ->
                    if (pack.contentVersion > row.installedVersion) InstallStatus.UPDATE_AVAILABLE
                    else InstallStatus.COMPLETED
                else -> continue
            }
            if (newStatus != row.status) {
                gameInstallDao.upsert(row.copy(status = newStatus, lastUpdatedAt = nowMs()))
            }
        }
    }

    /**
     * Recuperação pós-morte do processo: linhas DOWNLOADING/QUEUED sem worker
     * vivo viram PAUSED (o usuário retoma com um toque, offset preservado).
     */
    suspend fun reconcileInterruptedDownloads() {
        gameInstallDao.observeAll().first().forEach { row ->
            if ((row.status == InstallStatus.QUEUED || row.status == InstallStatus.DOWNLOADING) &&
                slots[row.gameId]?.job?.isActive != true
            ) {
                gameInstallDao.upsert(
                    row.copy(status = InstallStatus.PAUSED, lastError = null, lastUpdatedAt = nowMs())
                )
            }
        }
    }

    /** Limpa temporários (parciais e dados de descompactação). Retorna bytes liberados. */
    suspend fun clearTemporaryFiles(): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        val tempRoot = packManager.tempRoot
        if (tempRoot.isDirectory) {
            freed = packManager.dirBytes(tempRoot)
            packManager.deleteRecursively(tempRoot)
        }
        packManager.ensureBaseDirs()
        freed
    }

    // ==================================================================
    // Worker
    // ==================================================================

    private suspend fun runDownload(gameId: String, pack: RemotePack, packUrl: String, startOffset: Long) {
        val slot = slots[gameId] ?: Slot()
        val partFile = packManager.partFile(gameId, pack.contentVersion)
        val unzipDir = File(packManager.tempRoot, "unpack-$gameId-${pack.contentVersion}")

        try {
            packManager.ensureBaseDirs()
            packManager.deleteRecursively(unzipDir)
            writeRow(gameId) { row ->
                row.copy(
                    status = InstallStatus.DOWNLOADING,
                    totalBytes = pack.packBytes,
                    downloadedBytes = startOffset,
                    lastError = null,
                    lastUpdatedAt = nowMs()
                )
            }

            val result = downloader.downloadAppend(
                url = packUrl,
                partFile = partFile,
                startOffset = startOffset
            ) { absolute ->
                maybeProgress(gameId, slot, absolute)
            }
            // a última gravação pode estar defasada; garante o valor final
            writeRow(gameId) { it.copy(downloadedBytes = result.finalOffset, lastUpdatedAt = nowMs()) }

            // 1) integridade do zip inteiro
            PackVerifier.verifyZipAgainstRemote(partFile, pack)

            // 2) descompactação em staging + verificação arquivo a arquivo
            unzipDir.mkdirs()
            PackVerifier.unzipVerified(
                zipFile = partFile,
                stagingDir = unzipDir,
                expectedGameId = gameId,
                expectedContentVersion = pack.contentVersion
            )

            // 3) instalação atômica: rename dentro do mesmo volume (filesDir)
            val finalDir = packManager.installedPackDir(gameId, pack.contentVersion)
            if (finalDir.exists()) packManager.deleteRecursively(finalDir)
            if (!unzipDir.renameTo(finalDir)) {
                unzipDir.copyRecursively(finalDir)
                packManager.deleteRecursively(unzipDir)
            }

            // 4) versões antigas saem (atualização concluída)
            packManager.installedVersions(gameId)
                .filter { it != pack.contentVersion }
                .forEach { packManager.deleteRecursively(packManager.installedPackDir(gameId, it)) }

            // 5) COMPLETED só agora — houve hash + manifesto válidos
            val occupied = packManager.dirBytes(finalDir)
            packManager.deleteRecursively(partFile)
            writeRow(gameId) { row ->
                row.copy(
                    status = InstallStatus.COMPLETED,
                    installedVersion = pack.contentVersion.toLong(),
                    downloadedBytes = pack.packBytes,
                    totalBytes = pack.packBytes,
                    occupiedBytes = occupied,
                    lastError = null,
                    lastUpdatedAt = nowMs()
                )
            }
        } catch (e: CancellationException) {
            throw e // tratado em handleCancellation
        } catch (e: PackIntegrityException) {
            packManager.deleteRecursively(partFile)
            packManager.deleteRecursively(unzipDir)
            writeRow(gameId) { row ->
                row.copy(
                    status = InstallStatus.CORRUPTED,
                    downloadedBytes = 0,
                    lastError = e.message,
                    lastUpdatedAt = nowMs()
                )
            }
        } catch (e: IOException) {
            // rede/E/S: mantém o parcial (retomável) e marca FAILED
            val partialBytes = if (partFile.isFile) partFile.length() else 0L
            writeRow(gameId) { row ->
                row.copy(
                    status = InstallStatus.FAILED,
                    downloadedBytes = partialBytes,
                    lastError = "Download interrompido: ${friendlyIOException(e)}",
                    lastUpdatedAt = nowMs()
                )
            }
        } catch (e: Exception) {
            writeRow(gameId) { row ->
                row.copy(
                    status = InstallStatus.FAILED,
                    lastError = "Erro inesperado no download: ${e.javaClass.simpleName}",
                    lastUpdatedAt = nowMs()
                )
            }
        } finally {
            slots[gameId]?.cancelMode = null
        }
    }

    /** Estado final após cancelamento (pausa -> PAUSED; remoção -> Disponível). */
    private suspend fun handleCancellation(slot: Slot, gameId: String) {
        val current = gameInstallDao.find(gameId)
        when (slot.cancelMode) {
            CancelMode.PAUSE -> {
                current?.let { row ->
                    gameInstallDao.upsert(
                        row.copy(status = InstallStatus.PAUSED, lastError = null, lastUpdatedAt = nowMs())
                    )
                }
            }
            else -> {
                // REMOVE/fora: parciais fora. Se era ATUALIZAÇÃO cancelada,
                // a versão anterior instalada permanece intacta.
                if (current != null && !current.isInstalledRow()) {
                    clearGameTempArtifacts(gameId)
                    gameInstallDao.deleteByGameId(gameId)
                } else if (current != null) {
                    // jogo já instalado: devolve estado anterior (COMPLETED/UPDATE)
                    gameInstallDao.upsert(slot.previousRow?.copy(lastUpdatedAt = nowMs()) ?: current)
                }
                clearGameTempArtifacts(gameId)
            }
        }
    }

    // ==================================================================
    // Gravação no banco
    // ==================================================================

    /**
     * Atualiza a linha do jogo mantendo campos não tocados (relê antes de
     * escrever, para não sobrescrever mudanças de outras corrotinas).
     */
    private suspend fun writeRow(gameId: String, transform: (GameInstallEntity) -> GameInstallEntity) {
        val job = slots[gameId]?.job
        if (job != null && (!job.isActive || job.isCancelled)) return
        val current = gameInstallDao.find(gameId) ?: return
        gameInstallDao.upsert(transform(current))
    }

    private suspend fun maybeProgress(gameId: String, slot: Slot, absoluteBytes: Long) {
        val now = System.currentTimeMillis()
        val byteStep = absoluteBytes - slot.lastProgressBytes >= PROGRESS_STEP_BYTES
        val timeStep = now - slot.lastProgressAtMs >= PROGRESS_STEP_MS
        if (!byteStep && !timeStep) return
        val job = slots[gameId]?.job
        if (job != null && (!job.isActive || job.isCancelled)) return
        val row = gameInstallDao.find(gameId) ?: return
        if (row.status != InstallStatus.DOWNLOADING) return
        slot.lastProgressBytes = absoluteBytes
        slot.lastProgressAtMs = now
        gameInstallDao.upsert(row.copy(downloadedBytes = absoluteBytes, lastUpdatedAt = now))
    }

    private suspend fun writeFailureRow(
        gameId: String,
        prev: GameInstallEntity?,
        keepOffset: Boolean,
        status: InstallStatus,
        message: String
    ) {
        gameInstallDao.upsert(
            GameInstallEntity(
                gameId = gameId,
                status = status,
                installedVersion = prev?.installedVersion ?: 0,
                downloadedBytes = if (keepOffset) prev?.downloadedBytes ?: 0 else 0,
                totalBytes = prev?.totalBytes ?: 0,
                occupiedBytes = prev?.occupiedBytes ?: 0,
                lastError = message,
                lastUpdatedAt = nowMs()
            )
        )
    }

    // ==================================================================
    // Utilidades
    // ==================================================================

    private fun GameInstallEntity.isInstalledRow(): Boolean =
        status == InstallStatus.COMPLETED || status == InstallStatus.UPDATE_AVAILABLE

    private fun clearGameTempArtifacts(gameId: String) {
        packManager.tempRoot.takeIf { it.isDirectory }?.listFiles()?.forEach { file ->
            if (file.name.startsWith("$gameId-")) packManager.deleteRecursively(file)
        }
    }

    private fun friendlyIOException(e: IOException): String = when {
        e.message?.contains("canceled", ignoreCase = true) == true -> "conexão interrompida"
        e.message?.contains("timeout", ignoreCase = true) == true -> "tempo de resposta esgotado"
        e.message?.contains("unreachable", ignoreCase = true) == true -> "sem internet"
        e.message?.contains("ENOSPC", ignoreCase = true) == true -> "espaço insuficiente no armazenamento"
        else -> e.message ?: "falha de conexão"
    }

    private fun humanBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }

    /** Testes aguardam o fim do worker com este gancho. */
    internal suspend fun awaitIdle(gameId: String, timeoutMs: Long = 60_000) {
        val start = System.currentTimeMillis()
        while (slots[gameId]?.job != null) {
            if (System.currentTimeMillis() - start > timeoutMs) break
            delay(25)
        }
    }

    private fun nowMs(): Long = System.currentTimeMillis()

    private fun String.toHttpUrlOrNull(): okhttp3.HttpUrl? = try {
        toHttpUrl()
    } catch (e: IllegalArgumentException) {
        null
    }

    private companion object {
        const val PROGRESS_STEP_BYTES: Long = 256 * 1024
        const val PROGRESS_STEP_MS: Long = 400
    }
}

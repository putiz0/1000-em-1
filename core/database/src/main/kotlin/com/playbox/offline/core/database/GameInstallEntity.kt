package com.playbox.offline.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

/**
 * Estado de instalação de um jogo no dispositivo.
 * Chave primária: identificador do jogo no catálogo (ex.: "damas").
 */
@Entity(tableName = "game_install")
@TypeConverters(InstallStatusConverter::class)
data class GameInstallEntity(
    @PrimaryKey val gameId: String,
    /** Versão do pacote de conteúdo presente no dispositivo (0 = nenhuma). */
    val installedVersion: Long = 0,
    val status: InstallStatus = InstallStatus.FAILED,
    /** Bytes já baixados do pacote atual (para progresso/retomada). */
    val downloadedBytes: Long = 0,
    /** Tamanho total do pacote em bytes. */
    val totalBytes: Long = 0,
    /** Espaço efetivamente ocupado pelo jogo no armazenamento. */
    val occupiedBytes: Long = 0,
    val lastUpdatedAt: Long = 0,
    /** Mensagem de erro compreensível, quando status = FAILED/CORRUPTED. */
    val lastError: String? = null
)

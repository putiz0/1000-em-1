package com.playbox.offline.core.database

import androidx.room.TypeConverter

/**
 * Estados do ciclo de vida de instalação de um jogo (pacote de conteúdo),
 * conforme especificação do projeto. A ausência de registro no banco
 * representa "Disponível para baixar".
 */
enum class InstallStatus {
    /** Aguardando download (enfileirado, ainda não iniciou). */
    QUEUED,

    /** Baixando (progresso em downloadedBytes/totalBytes). */
    DOWNLOADING,

    /** Download pausado (retomável, quando tecnicamente possível). */
    PAUSED,

    /** Download concluído, pacote íntegro e instalado. */
    COMPLETED,

    /** Existe uma versão mais nova do pacote no catálogo. */
    UPDATE_AVAILABLE,

    /** Erro no download (lastError traz a mensagem para a UI). */
    FAILED,

    /** Verificação de integridade falhou; pacote não é executável. */
    CORRUPTED,

    /** Dados apagados pelo usuário (com confirmação) liberando espaço. */
    REMOVED
}

/** Regras de conversão persistidas pelo Room. */
object InstallStatusConverter {

    @TypeConverter
    fun toColumn(status: InstallStatus): String = status.name

    @TypeConverter
    fun fromColumn(value: String): InstallStatus =
        InstallStatus.entries.firstOrNull { it.name == value } ?: InstallStatus.FAILED
}

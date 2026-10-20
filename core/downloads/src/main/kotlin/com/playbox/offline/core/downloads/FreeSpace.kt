package com.playbox.offline.core.downloads

import android.os.StatFs
import java.io.File

/**
 * Consulta de espaço livre no armazenamento. Interface em separado para
 * os testes automatizados simularem aparelhos cheios.
 */
fun interface FreeSpaceChecker {
    fun availableBytes(baseDir: File): Long
}

/** Implementação real no Android (StatFs). */
class AndroidFreeSpaceChecker : FreeSpaceChecker {
    override fun availableBytes(baseDir: File): Long = try {
        StatFs(baseDir.absolutePath).availableBytes
    } catch (e: Exception) {
        // se a consulta falhar por algum motivo raro, não bloqueia o download;
        // o espaço real do sistema continuará nos limites
        Long.MAX_VALUE
    }
}

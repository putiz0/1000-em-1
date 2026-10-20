package com.playbox.offline.core.gameapi

import kotlinx.serialization.Serializable

/**
 * Manifesto que acompanha CADA pacote de conteúdo (zip).
 * Fica dentro do zip como `manifest.json` (raiz) e descreve todos os
 * arquivos de conteúdo com hash SHA-256, permitindo verificar a integridade
 * dos dados na instalação e em verificações futuras.
 *
 * Regras de integridade:
 *  - Todo arquivo listado precisa existir no staging com hash idêntico;
 *  - Todo arquivo do zip (exceto o próprio manifesto) precisa estar listado;
 *  - A divergência de qualquer um dos dois marca o pacote como CORRUPTED.
 */
@Serializable
data class PackManifest(
    val gameId: String,
    /** Versão do conteúdo representada por este pacote. */
    val contentVersion: Int,
    val files: List<ManifestFile> = emptyList()
) {
    /** Soma dos bytes dos arquivos de conteúdo (sem contar o manifesto). */
    val totalContentBytes: Long get() = files.sumOf { it.bytes }
}

@Serializable
data class ManifestFile(
    /** Caminho relativo dentro do pacote (usar "/" como separador). */
    val path: String,
    val bytes: Long,
    /** SHA-256 em hex minúsculo do conteúdo do arquivo. */
    val sha256: String
)

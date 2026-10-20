package com.playbox.offline.games.xadrez

import com.playbox.offline.core.packs.PackManager
import kotlinx.serialization.Serializable

/**
 * Configuração que vem no pacote do jogo (config.json). Profundidades
 * da IA e chance de erro do nível fácil — parâmetros que a tela honra.
 */
@Serializable
data class XadrezConfig(
    val profundidadeFacil: Int = 1,
    val profundidadeMedia: Int = 3,
    val profundidadeDificil: Int = 5,
    val chanceLanceAleatorioFacil: Double = 0.35,
) {
    fun profundidade(indice: Int): Int = when (indice) {
        0 -> profundidadeFacil
        1 -> profundidadeMedia
        else -> profundidadeDificil
    }

    companion object {
        fun doPacote(texto: String?): XadrezConfig? {
            if (texto == null) return null
            return runCatching {
                PackManager.json.decodeFromString<XadrezConfig>(texto)
            }.getOrNull()
        }
    }
}

package com.playbox.offline.games.domino

import com.playbox.offline.core.packs.PackManager
import kotlinx.serialization.Serializable

@Serializable
data class DominoConfig(
    val maoInicial: Int = DominoRegras.MAO_PADRAO,
    val metaPontos: Int = DominoRegras.META_PADRAO,
    val bonusCarrocaBatida: Int = 1,
    val jogadoresMin: Int = 2,
    val jogadoresMax: Int = 4,
) {
    companion object {
        fun doPacote(texto: String?): DominoConfig? {
            if (texto == null) return null
            return runCatching {
                PackManager.json.decodeFromString<DominoConfig>(texto)
            }.getOrNull()
        }
    }
}

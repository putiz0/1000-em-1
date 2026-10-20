package com.playbox.offline.games.damas

import com.playbox.offline.core.packs.PackManager
import kotlinx.serialization.Serializable

/**
 * Configuração que vem no pacote do jogo (config.json dentro do zip).
 * Apenas parâmetros que o motor realmente honra: limite de empate e
 * cores de apresentação. As regras (captura obrigatória, maioria,
 * dama voadora) estão descritas no regras.md do pacote e são
 * implementadas pelo [DamasRegras].
 */
@Serializable
data class DamasConfig(
    val jogadasSemCapturaParaEmpate: Int = DamasRegras.LIMITE_SEM_CAPTURA,
    val casaClara: String = "#EED9B6",
    val casaEscura: String = "#8B5A2B",
    val pecaClara: String = "#F7F3E8",
    val pecaEscura: String = "#232323",
) {
    companion object {
        /**
         * Lê o config.json do pacote; devolve null quando o pacote não
         * está instalado (sem esse arquivo o jogo não abre).
         */
        fun doPacote(texto: String?): DamasConfig? {
            if (texto == null) return null
            return runCatching {
                PackManager.json.decodeFromString<DamasConfig>(texto)
            }.getOrNull()
        }
    }
}

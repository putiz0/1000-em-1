package com.playbox.offline.core.gameapi

import kotlinx.coroutines.flow.StateFlow

/**
 * Fonte do catálogo remoto. A implementação concreta (OkHttp) vive em
 * :core:downloads; assim :core:catalog pode mesclar os dados remotos
 * sem depender de bibliotecas de rede.
 */
interface RemoteCatalogSource {

    /** Último catálogo carregado com sucesso (null nunca carregou). */
    val remoteCatalog: StateFlow<RemoteCatalog?>

    /**
     * Recarrega do servidor. Lança IOException em falha de rede e
     * RemoteCatalogUnavailableException em resposta inválida.
     */
    suspend fun refresh(): RemoteCatalog?
}

/** Resposta inesperada/inválida do servidor de packs. */
class RemoteCatalogUnavailableException(message: String) : RuntimeException(message)

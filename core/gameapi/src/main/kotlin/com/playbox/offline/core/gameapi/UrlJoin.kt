package com.playbox.offline.core.gameapi

/** Junta uma base e um caminho, tolerando barras duplicadas nas pontas. */
fun joinUrl(base: String, path: String): String =
    base.trimEnd('/') + "/" + path.trimStart('/')

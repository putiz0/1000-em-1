package com.playbox.offline.core.downloads

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

/**
 * Servidor HTTP real usado nos testes (socket cru do JDK — o classpath de
 * teste do Android não expõe com.sun.net.httpserver): responde conteúdo
 * fixo com suporte opcional a GET/HEAD/Range, exercitando de verdade o
 * caminho de retomada do `HttpPackDownloader`.
 *
 * Cada conexão aceita recebe UMA requisição e é fechada
 * (`Connection: close`); o OkHttp trata isso sem ajustes extra.
 */
class HttpTestServer(
    private val bytes: ByteArray,
    private val honorRange: Boolean = true
) : AutoCloseable {

    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    val port: Int
        get() = serverSocket?.localPort ?: 0

    /** URL completa servida (aceita qualquer caminho dentro do servidor). */
    val url: String
        get() = "http://127.0.0.1:$port/packs/pack.zip"

    init {
        val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        serverSocket = socket
        acceptThread = Thread({ runLoop(socket) }, "http-test-server").apply {
            isDaemon = true
            start()
        }
    }

    private fun runLoop(socket: ServerSocket) {
        while (true) {
            val connection = try {
                socket.accept()
            } catch (e: SocketException) {
                return // socket fechado no close()
            }
            try {
                connection.soTimeout = 10_000
                handleConnection(connection)
            } catch (e: Exception) {
                // qualquer falha inicia nova iteração; o cliente vê conexão cortada
            } finally {
                try { connection.close() } catch (e: IOException) { /* ignore */ }
            }
        }
    }

    private fun handleConnection(socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
        val output = socket.getOutputStream()

        val requestLine = reader.readLine() ?: return
        var method = "GET"
        requestLine.split(" ").takeIf { it.size >= 3 }?.let { parts -> method = parts[0] }

        // lê cabeçalhos até a linha em branco
        var range: String? = null
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0 && line.substring(0, colon).trim().equals("Range", ignoreCase = true)) {
                range = line.substring(colon + 1).trim()
            }
        }

        if (method != "GET") {
            writeResponse(output, status = 200, extraHeaders = "Accept-Ranges: bytes\r\n", body = null)
            return
        }

        if (honorRange && range != null && range.startsWith("bytes=")) {
            val startText = range.removePrefix("bytes=").substringBefore('-')
            val start = startText.toLongOrNull()
            if (start != null && start in 0 until bytes.size) {
                val length = bytes.size - start
                writeResponse(
                    output,
                    status = 206,
                    extraHeaders = "Content-Range: bytes $start-${bytes.size - 1}/${bytes.size}\r\n",
                    body = bytes.copyOfRange(start.toInt(), bytes.size)
                )
                return
            }
            if (start != null && start >= bytes.size.toLong()) {
                writeResponse(
                    output,
                    status = 416,
                    extraHeaders = "Content-Range: bytes */${bytes.size}\r\n",
                    body = null
                )
                return
            }
        }

        writeResponse(output, status = 200, extraHeaders = "Accept-Ranges: bytes\r\n", body = bytes)
    }

    private fun writeResponse(output: OutputStream, status: Int, extraHeaders: String, body: ByteArray?) {
        val reason = when (status) {
            200 -> "OK"; 206 -> "Partial Content"; 416 -> "Range Not Satisfiable"
            else -> "Internal Server Error"
        }
        val head = buildString {
            append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n")
            append(extraHeaders)
            append("Content-Length: ").append(body?.size ?: 0).append("\r\n")
            append("Connection: close").append("\r\n")
            append("\r\n")
        }
        output.write(head.toByteArray(Charsets.ISO_8859_1))
        if (body != null) output.write(body)
        output.flush()
    }

    override fun close() {
        try { serverSocket?.close() } catch (e: IOException) { /* ignore */ }
    }

    companion object {
        /** Cria um arquivo binário determinístico de N bytes. */
        fun fakeBytes(size: Int): ByteArray =
            ByteArray(size) { index -> ('a' + (index * 7 % 26)).code.toByte() }
    }
}

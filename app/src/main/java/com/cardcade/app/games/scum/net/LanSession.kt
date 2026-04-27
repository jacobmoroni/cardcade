package com.cardcade.app.games.scum.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Generic LAN socket session for Scum multiplayer. The HOST opens a
 * [ServerSocket]; clients connect to it. Messages are newline-delimited raw
 * strings — encoding/decoding is the caller's responsibility so the session
 * stays game-type agnostic.
 *
 * [incoming] emits (senderId, rawLine) pairs. For HOST senders, [senderId] is
 * an internal client ID (> 0). For the CLIENT, [senderId] is always 0.
 */
class LanSession private constructor(
    val role: Role,
    parentScope: CoroutineScope,
) {
    enum class Role { HOST, CLIENT }

    private val scope = CoroutineScope(SupervisorJob(parentScope.coroutineContext[Job]) + Dispatchers.IO)

    private val _incoming = MutableSharedFlow<Pair<Int, String>>(extraBufferCapacity = 128)
    val incoming: SharedFlow<Pair<Int, String>> = _incoming.asSharedFlow()

    private val _listenPort = MutableStateFlow<Int?>(null)
    val listenPort: StateFlow<Int?> = _listenPort.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private val clients = ConcurrentHashMap<Int, ClientLink>()
    private val nextClientId = AtomicInteger(1)

    private var clientSocket: Socket? = null
    private var clientWriter: PrintWriter? = null

    // ---- HOST side --------------------------------------------------------------

    private fun startHost() {
        scope.launch {
            val srv = ServerSocket(0)
            serverSocket = srv
            _listenPort.value = srv.localPort
            _connected.value = true
            while (!srv.isClosed) {
                val sock = runCatching { srv.accept() }.getOrNull() ?: break
                acceptClient(sock)
            }
        }
    }

    private fun acceptClient(socket: Socket) {
        val id = nextClientId.getAndIncrement()
        val link = ClientLink(socket)
        clients[id] = link
        scope.launch {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            runCatching {
                reader.forEachLine { line ->
                    _incoming.tryEmit(id to line)
                }
            }
            clients.remove(id)
            // Emit a synthetic leave line so callers don't need to track socket lifecycle.
            _incoming.tryEmit(id to LEAVE_SENTINEL)
            runCatching { socket.close() }
        }
    }

    fun broadcast(line: String) {
        clients.values.forEach { it.send(line) }
    }

    fun sendToClient(clientId: Int, line: String) {
        clients[clientId]?.send(line)
    }

    // ---- CLIENT side ------------------------------------------------------------

    fun connectToHost(host: String, port: Int) {
        require(role == Role.CLIENT)
        scope.launch {
            val sock = runCatching { Socket(host, port) }.getOrNull() ?: run {
                _connected.value = false
                return@launch
            }
            clientSocket = sock
            clientWriter = PrintWriter(sock.getOutputStream(), /* autoFlush = */ true)
            _connected.value = true
            val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
            runCatching {
                reader.forEachLine { line ->
                    _incoming.tryEmit(0 to line)
                }
            }
            _connected.value = false
            runCatching { sock.close() }
        }
    }

    fun sendToHost(line: String) {
        clientWriter?.println(line)
    }

    // ---- Lifecycle --------------------------------------------------------------

    fun close() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        clients.values.forEach { it.close() }
        clients.clear()
        runCatching { clientSocket?.close() }
        clientSocket = null
        clientWriter = null
        _connected.value = false
        scope.cancel()
    }

    private class ClientLink(private val socket: Socket) {
        private val writer = PrintWriter(socket.getOutputStream(), /* autoFlush = */ true)
        fun send(line: String) { runCatching { writer.println(line) } }
        fun close() { runCatching { socket.close() } }
    }

    companion object {
        fun host(scope: CoroutineScope): LanSession =
            LanSession(Role.HOST, scope).also { it.startHost() }

        fun client(scope: CoroutineScope): LanSession = LanSession(Role.CLIENT, scope)
    }
}

package com.luckyunders.app.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * A single side of a LAN multiplayer link. The host opens a [ServerSocket];
 * clients connect to it. Messages are newline-delimited JSON objects and are
 * forwarded through [incoming] for the caller (typically the ViewModel) to
 * handle.
 */
class LanSession private constructor(
    val role: Role,
    private val parentScope: CoroutineScope,
) {
    enum class Role { HOST, CLIENT }

    private val scope = CoroutineScope(SupervisorJob(parentScope.coroutineContext[Job]) + Dispatchers.IO)

    private val _incoming = MutableSharedFlow<Pair<Int, LanMessage>>(extraBufferCapacity = 64)
    val incoming: SharedFlow<Pair<Int, LanMessage>> = _incoming.asSharedFlow()

    private val _listenPort = MutableStateFlow<Int?>(null)
    val listenPort: StateFlow<Int?> = _listenPort.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private val clients = ConcurrentHashMap<Int, ClientLink>()
    private val nextClientId = AtomicInteger(1)

    private var clientSocket: Socket? = null
    private var clientWriter: PrintWriter? = null

    fun startHost() {
        require(role == Role.HOST)
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
                    LanMessage.decode(line)?.let { msg ->
                        _incoming.tryEmit(id to msg)
                    }
                }
            }
            clients.remove(id)
            _incoming.tryEmit(id to LanMessage.Leave(id))
            runCatching { socket.close() }
        }
    }

    fun broadcast(message: LanMessage) {
        val line = message.encode()
        clients.values.forEach { it.send(line) }
    }

    fun sendToClient(clientId: Int, message: LanMessage) {
        clients[clientId]?.send(message.encode())
    }

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
                    LanMessage.decode(line)?.let { msg ->
                        _incoming.tryEmit(0 to msg)
                    }
                }
            }
            _connected.value = false
            runCatching { sock.close() }
        }
    }

    fun sendToHost(message: LanMessage) {
        clientWriter?.println(message.encode())
    }

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

        fun send(line: String) {
            runCatching { writer.println(line) }
        }

        fun close() {
            runCatching { socket.close() }
        }
    }

    companion object {
        fun host(scope: CoroutineScope): LanSession =
            LanSession(Role.HOST, scope).also { it.startHost() }

        fun client(scope: CoroutineScope): LanSession = LanSession(Role.CLIENT, scope)
    }
}

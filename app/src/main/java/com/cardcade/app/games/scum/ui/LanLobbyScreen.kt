package com.cardcade.app.games.scum.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardcade.app.games.scum.game.GameStateJson
import com.cardcade.app.games.scum.game.SessionMode
import com.cardcade.app.games.scum.game.SetupOptions
import com.cardcade.app.games.scum.net.LanDiscovery
import com.cardcade.app.games.scum.net.LanMessage
import com.cardcade.app.games.scum.net.LanSession
import com.cardcade.app.games.scum.net.LobbySeat
import com.cardcade.app.games.scum.net.toLanStart

/**
 * Pre-game LAN lobby for Scum. HOST advertises via NSD and waits for clients to
 * join; CLIENT discovers and connects. On start the host broadcasts the
 * SetupOptions and transitions everyone to the game screen.
 */
@Composable
fun LanLobbyScreen(
    setup: SetupOptions,
    onBack: () -> Unit,
    onStartAsHost: (SetupOptions, LanSession, Map<Int, Int>, Map<Int, String>) -> Unit,
    onStartAsClient: (LanSession, Int, SetupOptions) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val discovery = remember { LanDiscovery(context) }

    var role by remember { mutableStateOf<LanSession.Role?>(null) }
    val session = remember { mutableStateOf<LanSession?>(null) }
    val peers by remember { discovery.discoverPeers() }.collectAsState(initial = emptyList())
    val seats = remember { mutableStateOf(initialLobbySeats(setup, androidName(context))) }

    DisposableEffect(Unit) {
        onDispose {
            session.value?.close()
            discovery.unregister()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF063F23))
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Text("← Back", color = Color(0xFFE6B54A), fontSize = 16.sp)
                }
            }
            Text(
                "Online lobby (LAN)",
                color = Color(0xFFE6B54A),
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "Players on the same wifi will appear below.",
                color = Color(0xFFB9F5C9),
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(16.dp))
            LobbyRoleSelector(
                role = role,
                onSelect = { newRole ->
                    session.value?.close()
                    discovery.unregister()
                    session.value = null
                    role = newRole
                    if (newRole == LanSession.Role.HOST) {
                        session.value = LanSession.host(coroutineScope)
                    } else {
                        session.value = LanSession.client(coroutineScope)
                    }
                },
            )
            Spacer(Modifier.height(16.dp))
            when (role) {
                LanSession.Role.HOST -> HostPanel(
                    setup = setup,
                    session = session.value,
                    seats = seats.value,
                    discovery = discovery,
                    hostName = androidName(context),
                    onSeatsChanged = { seats.value = it },
                    onStart = { gameOpts, clientSeatMap, playerNames ->
                        onStartAsHost(gameOpts, session.value!!, clientSeatMap, playerNames)
                    },
                )
                LanSession.Role.CLIENT -> ClientPanel(
                    session = session.value,
                    peers = peers,
                    onGameStarted = onStartAsClient,
                )
                null -> Text("Pick Host or Join to begin.", color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

// ---- Host panel -----------------------------------------------------------------

@Composable
private fun HostPanel(
    setup: SetupOptions,
    session: LanSession?,
    seats: List<LobbySeat>,
    discovery: LanDiscovery,
    hostName: String,
    onSeatsChanged: (List<LobbySeat>) -> Unit,
    onStart: (SetupOptions, Map<Int, Int>, Map<Int, String>) -> Unit,
) {
    val port by (session?.listenPort?.collectAsState() ?: remember { mutableStateOf(null) })
    val registered by discovery.registeredName.collectAsState()

    LaunchedEffect(port) {
        val p = port ?: return@LaunchedEffect
        discovery.register(hostName, p)
    }

    // Track display names from JOIN messages (clientId → name)
    val clientNames = remember { mutableStateOf(mapOf<Int, String>()) }

    LaunchedEffect(session) {
        session?.incoming?.collect { (clientId, line) ->
            val msg = LanMessage.decode(line) ?: run {
                // LEAVE_SENTINEL: client disconnected
                val newSeats = seats.map {
                    if (it.id == clientId && it.kind == LobbySeat.Kind.REMOTE) {
                        it.copy(id = -1, name = "open seat", kind = LobbySeat.Kind.OPEN)
                    } else it
                }
                onSeatsChanged(newSeats)
                session.broadcast(LanMessage.Lobby(newSeats).encode())
                return@collect
            }
            when (msg) {
                is LanMessage.Join -> {
                    val openIdx = seats.indexOfFirst { it.kind == LobbySeat.Kind.OPEN }
                    if (openIdx >= 0) {
                        val updated = seats.toMutableList().apply {
                            this[openIdx] = LobbySeat(
                                id = clientId,
                                name = msg.displayName,
                                kind = LobbySeat.Kind.REMOTE,
                            )
                        }
                        onSeatsChanged(updated)
                        clientNames.value = clientNames.value + (clientId to msg.displayName)
                        session.broadcast(LanMessage.Lobby(updated).encode())
                        session.sendToClient(clientId, LanMessage.Hello(openIdx, hostName).encode())
                    }
                }
                else -> Unit
            }
        }
    }

    Column {
        Text(
            "Advertising as ${registered ?: hostName}" +
                if (port != null) " (port $port)" else " (starting…)",
            color = Color.White,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))
        SeatList(
            seats = seats,
            fillWithCpu = { i ->
                val updated = seats.toMutableList().apply {
                    if (i < size) {
                        this[i] = LobbySeat(id = -2 - i, name = "CPU ${i + 1}", kind = LobbySeat.Kind.CPU)
                    }
                }
                onSeatsChanged(updated)
                session?.broadcast(LanMessage.Lobby(updated).encode())
            },
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val remoteSeats = seats.filter { it.kind == LobbySeat.Kind.REMOTE }
                val gameOpts = setup.copy(
                    humanCount = 1 + remoteSeats.size,
                    mode = SessionMode.ONLINE_LAN,
                )
                val clientSeatMap = seats.mapIndexedNotNull { idx, seat ->
                    if (seat.kind == LobbySeat.Kind.REMOTE) seat.id to idx else null
                }.toMap()
                val playerNames: Map<Int, String> = buildMap {
                    put(0, hostName)
                    clientSeatMap.forEach { (clientId, seatIdx) ->
                        put(seatIdx, clientNames.value[clientId] ?: "Player $seatIdx")
                    }
                }
                session?.broadcast(gameOpts.toLanStart().encode())
                onStart(gameOpts, clientSeatMap, playerNames)
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE6B54A),
                contentColor = Color.Black,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Start game (${setup.totalPlayers} seats)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---- Client panel ---------------------------------------------------------------

@Composable
private fun ClientPanel(
    session: LanSession?,
    peers: List<LanDiscovery.Peer>,
    onGameStarted: (LanSession, Int, SetupOptions) -> Unit,
) {
    val connected by (session?.connected?.collectAsState() ?: remember { mutableStateOf(false) })
    var joinedPeer by remember { mutableStateOf<LanDiscovery.Peer?>(null) }
    var mySeat by remember { mutableStateOf(-1) }
    var playerName by remember { mutableStateOf("Guest") }

    LaunchedEffect(session) {
        session?.incoming?.collect { (_, line) ->
            val msg = LanMessage.decode(line) ?: return@collect
            when (msg) {
                is LanMessage.Hello -> mySeat = msg.seatId
                is LanMessage.Start -> {
                    if (mySeat >= 0) {
                        val opts = GameStateJson.decodeOptions(msg.optsJson)
                        session?.let { onGameStarted(it, mySeat, opts) }
                    }
                }
                else -> Unit
            }
        }
    }

    Column {
        Text(
            when {
                connected && joinedPeer != null -> "Connected to ${joinedPeer?.name} — waiting for host to start…"
                peers.isEmpty() -> "Scanning for Scum hosts on the network…"
                else -> "Scum hosts nearby:"
            },
            color = Color.White,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))
        peers.forEach { p ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0B3F27),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(vertical = 4.dp)
                    .border(1.dp, Color(0xFF145A34), RoundedCornerShape(10.dp))
                    .clickable {
                        session?.connectToHost(p.host, p.port)
                        session?.sendToHost(LanMessage.Join(playerName).encode())
                        joinedPeer = p
                    },
            ) {
                Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(12.dp)) {
                    Text(
                        "${p.name}  ·  ${p.host}:${p.port}",
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

// ---- Shared seat list -----------------------------------------------------------

@Composable
private fun SeatList(seats: List<LobbySeat>, fillWithCpu: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        seats.forEachIndexed { idx, seat ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0B3F27),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(1.dp, Color(0xFF145A34), RoundedCornerShape(10.dp)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Seat ${idx + 1}",
                        color = Color(0xFFE6B54A),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.weight(0.2f))
                    Text(
                        "${seat.kind.label()}: ${seat.name}",
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (seat.kind == LobbySeat.Kind.OPEN) {
                        TextButton(onClick = { fillWithCpu(idx) }) {
                            Text("Fill CPU", color = Color(0xFFE6B54A))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LobbyRoleSelector(role: LanSession.Role?, onSelect: (LanSession.Role) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(LanSession.Role.HOST to "Host a game", LanSession.Role.CLIENT to "Join a game").forEach { (r, label) ->
            val selected = role == r
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .background(
                        if (selected) Color(0xFFE6B54A) else Color(0xFF0B6A3A),
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { onSelect(r) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) Color.Black else Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

private fun LobbySeat.Kind.label(): String = when (this) {
    LobbySeat.Kind.HOST -> "Host"
    LobbySeat.Kind.REMOTE -> "Remote"
    LobbySeat.Kind.CPU -> "CPU"
    LobbySeat.Kind.OPEN -> "Open"
}

private fun initialLobbySeats(setup: SetupOptions, hostName: String): List<LobbySeat> {
    val list = mutableListOf(LobbySeat(id = 0, name = hostName, kind = LobbySeat.Kind.HOST))
    for (i in 1 until setup.totalPlayers) {
        list.add(LobbySeat(id = -1, name = "open seat", kind = LobbySeat.Kind.OPEN))
    }
    return list
}

private fun androidName(context: Context): String {
    val fallback = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
    return runCatching {
        android.provider.Settings.Global.getString(context.contentResolver, "device_name")
    }.getOrNull().takeUnless { it.isNullOrBlank() } ?: fallback
}

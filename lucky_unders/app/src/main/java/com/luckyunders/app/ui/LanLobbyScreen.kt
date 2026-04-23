package com.luckyunders.app.ui

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
import com.luckyunders.app.game.SetupOptions
import com.luckyunders.app.net.LanDiscovery
import com.luckyunders.app.net.LanMessage
import com.luckyunders.app.net.LanSession
import com.luckyunders.app.net.LobbySeat

/**
 * Pre-game LAN lobby. The user can either host a session (advertising via NSD
 * and opening a socket) or join a host already on the network. When the host
 * chooses Start, the local game kicks off with a seat plan that fills empty
 * online seats with CPUs.
 */
@Composable
fun LanLobbyScreen(
    setup: SetupOptions,
    onBack: () -> Unit,
    onStartLocal: (SetupOptions) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val discovery = remember { LanDiscovery(context) }

    var role by remember { mutableStateOf<LanSession.Role?>(null) }
    val session = remember { mutableStateOf<LanSession?>(null) }

    val peers by remember { discovery.discoverPeers() }.collectAsState(initial = emptyList())
    val seats = remember { mutableStateOf<List<LobbySeat>>(initialLobbySeats(setup, androidName(context))) }

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
            SelectorRow(
                items = listOf("Host a game", "Join a game"),
                selectedIndex = when (role) {
                    LanSession.Role.HOST -> 0
                    LanSession.Role.CLIENT -> 1
                    null -> -1
                }.coerceAtLeast(0),
                onSelect = {
                    session.value?.close()
                    discovery.unregister()
                    session.value = null
                    role = if (it == 0) LanSession.Role.HOST else LanSession.Role.CLIENT
                    if (role == LanSession.Role.HOST) {
                        val host = LanSession.host(coroutineScope)
                        session.value = host
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
                    onStart = { onStartLocal(setup) },
                )
                LanSession.Role.CLIENT -> ClientPanel(
                    session = session.value,
                    peers = peers,
                )
                null -> Text(
                    "Pick Host or Join to begin.",
                    color = Color.White,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun HostPanel(
    setup: SetupOptions,
    session: LanSession?,
    seats: List<LobbySeat>,
    discovery: LanDiscovery,
    hostName: String,
    onSeatsChanged: (List<LobbySeat>) -> Unit,
    onStart: () -> Unit,
) {
    val port by (session?.listenPort?.collectAsState() ?: remember { mutableStateOf(null) })
    val registered by discovery.registeredName.collectAsState()

    LaunchedEffect(port) {
        val p = port ?: return@LaunchedEffect
        discovery.register(hostName, p)
    }

    LaunchedEffect(session) {
        session?.incoming?.collect { (clientId, msg) ->
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
                        session.broadcast(LanMessage.Lobby(updated))
                        session.sendToClient(clientId, LanMessage.Hello(openIdx, hostName))
                    }
                }
                is LanMessage.Leave -> {
                    val newSeats = seats.map {
                        if (it.id == msg.seatId && it.kind == LobbySeat.Kind.REMOTE) {
                            it.copy(id = -1, name = "open seat", kind = LobbySeat.Kind.OPEN)
                        } else it
                    }
                    onSeatsChanged(newSeats)
                    session.broadcast(LanMessage.Lobby(newSeats))
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
        SeatList(seats = seats, fillWithCpu = { i ->
            val updated = seats.toMutableList().apply {
                if (i < size) {
                    this[i] = LobbySeat(id = -2 - i, name = "CPU ${i + 1}", kind = LobbySeat.Kind.CPU)
                }
            }
            onSeatsChanged(updated)
            session?.broadcast(LanMessage.Lobby(updated))
        })
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                session?.broadcast(LanMessage.Start)
                onStart()
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
        Spacer(Modifier.height(10.dp))
        Text(
            "Note: this build runs the game on the host device. Joined players see the lobby; full over-the-wire play is part of the ongoing online multiplayer work.",
            color = Color(0xFFFFE0B2),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun ClientPanel(
    session: LanSession?,
    peers: List<LanDiscovery.Peer>,
) {
    val connected by (session?.connected?.collectAsState() ?: remember { mutableStateOf(false) })
    var joinedPeer by remember { mutableStateOf<LanDiscovery.Peer?>(null) }

    LaunchedEffect(session) {
        session?.incoming?.collect { _ -> /* ignored in lobby-only build */ }
    }

    Column {
        Text(
            when {
                connected -> "Connected to ${joinedPeer?.name ?: "host"}"
                peers.isEmpty() -> "Scanning for hosts on the network…"
                else -> "Hosts nearby:"
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
                        session?.sendToHost(LanMessage.Join("Guest"))
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
                            Text("Fill with CPU", color = Color(0xFFE6B54A))
                        }
                    }
                }
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
    val seats = mutableListOf<LobbySeat>()
    seats.add(LobbySeat(id = 0, name = hostName, kind = LobbySeat.Kind.HOST))
    for (i in 1 until setup.totalPlayers) {
        seats.add(LobbySeat(id = -1, name = "open seat", kind = LobbySeat.Kind.OPEN))
    }
    return seats
}

private fun androidName(context: Context): String {
    val fallback = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
    return runCatching {
        android.provider.Settings.Global.getString(context.contentResolver, "device_name")
    }.getOrNull().takeUnless { it.isNullOrBlank() } ?: fallback
}

package com.cardcade.app.games.luckyunders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cardcade.app.games.luckyunders.game.GameState
import com.cardcade.app.games.luckyunders.game.Player
import com.cardcade.app.games.luckyunders.game.SeriesFormat
import com.cardcade.app.games.luckyunders.game.Zone

@Composable
fun GameScreen(vm: GameViewModel = viewModel(), onRestart: () -> Unit) {
    val state by vm.state.collectAsState()
    val selected by vm.selectedIndices.collectAsState()
    val selectedOversAddon by vm.selectedOversAddon.collectAsState()
    val mode by vm.mode.collectAsState()
    val revealed by vm.revealed.collectAsState()
    val swapHand by vm.swapHandIndex.collectAsState()
    val swapOvers by vm.swapOversIndex.collectAsState()
    val scores by vm.scores.collectAsState()
    val roundOrder by vm.roundOrder.collectAsState()
    val lastRoundPoints by vm.lastRoundPoints.collectAsState()
    val matchOver by vm.matchOver.collectAsState()
    val seriesConfig by vm.seriesConfig.collectAsState()
    val rapidPlay by vm.rapidPlay.collectAsState()
    val s = state ?: return
    var showMenu by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }

    val activePid = s.setupPlayer ?: s.currentPlayer
    val viewerId = if (mode == GameMode.PASS_AND_PLAY) activePid else 0

    if (s.isGameOver) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B6A3A)),
        ) {
            ScoreboardScreen(
                state = s,
                roundOrder = roundOrder,
                lastRoundPoints = lastRoundPoints,
                cumulativeScores = scores,
                matchOver = matchOver,
                seriesTarget = seriesConfig.targetScore,
                isSeries = seriesConfig.format == SeriesFormat.SERIES,
                onContinue = { vm.continueMatch() },
                onNewGame = onRestart,
            )
            TopLeftMenu(
                onOpen = { showMenu = true },
                showMenu = showMenu,
                onDismiss = { showMenu = false },
                onNewGame = { showMenu = false; onRestart() },
                onShowRules = { showMenu = false; showRules = true },
            )
        }
        if (showRules) RulesDialog(onDismiss = { showRules = false })
        return
    }

    if (mode == GameMode.PASS_AND_PLAY && !revealed) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF063F23)),
        ) {
            PassScreen(
                nextPlayer = s.players[activePid],
                isSetup = s.isSetupPhase,
                pile = s.pile.size,
                log = s.log.lastOrNull(),
                onReveal = { vm.reveal() },
            )
            TopLeftMenu(
                onOpen = { showMenu = true },
                showMenu = showMenu,
                onDismiss = { showMenu = false },
                onNewGame = { showMenu = false; onRestart() },
                onShowRules = { showMenu = false; showRules = true },
            )
        }
        if (showRules) RulesDialog(onDismiss = { showRules = false })
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B6A3A)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                OpponentsRow(s, viewerId)
                Spacer(Modifier.height(8.dp))
                PileArea(s)
            }
            Spacer(Modifier.height(8.dp))
            if (s.isSetupPhase) {
                SwapArea(
                    state = s,
                    viewerId = viewerId,
                    swapHand = swapHand,
                    swapOvers = swapOvers,
                    onSelectHand = { vm.selectSwapHand(it) },
                    onSelectOvers = { vm.selectSwapOvers(it) },
                    onDone = { vm.finishSwapping() },
                )
            } else {
                ViewerArea(
                    state = s,
                    viewerId = viewerId,
                    selected = selected,
                    selectedOversAddon = selectedOversAddon,
                    mode = mode,
                    rapidPlay = rapidPlay,
                    onToggle = { vm.toggleSelect(it) },
                    onToggleOversAddon = { vm.toggleOversAddon(it) },
                    onFlipUnder = { vm.flipUnder(it) },
                    onPlay = { vm.playSelected() },
                    onPickUp = { vm.pickUpPile() },
                    onToggleRapidPlay = { vm.toggleRapidPlay() },
                )
            }
            Spacer(Modifier.height(8.dp))
            LogPanel(s)
        }
        TopLeftMenu(
            onOpen = { showMenu = true },
            showMenu = showMenu,
            onDismiss = { showMenu = false },
            onNewGame = { showMenu = false; onRestart() },
            onShowRules = { showMenu = false; showRules = true },
        )
        if (showRules) RulesDialog(onDismiss = { showRules = false })
    }
}

@Composable
private fun TopLeftMenu(
    onOpen: () -> Unit,
    showMenu: Boolean,
    onDismiss: () -> Unit,
    onNewGame: () -> Unit,
    onShowRules: () -> Unit,
) {
    Box(
        modifier = Modifier
            .safeDrawingPadding()
            .padding(start = 4.dp, top = 4.dp),
    ) {
        HamburgerButton(onClick = onOpen)
    }
    if (showMenu) {
        MenuDialog(onDismiss = onDismiss, onNewGame = onNewGame, onShowRules = onShowRules)
    }
}

@Composable
private fun HamburgerButton(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0x66000000),
        modifier = Modifier
            .size(width = 36.dp, height = 32.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("\u2630", color = Color.White, fontSize = 18.sp)
        }
    }
}

@Composable
private fun MenuDialog(
    onDismiss: () -> Unit,
    onNewGame: () -> Unit,
    onShowRules: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF1F2937),
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text(
                    "Lucky Unders",
                    color = Color(0xFFE6B54A),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onShowRules,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF334155),
                            contentColor = Color.White,
                        ),
                    ) { Text("Rules") }
                    Button(
                        onClick = onNewGame,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE6B54A),
                            contentColor = Color.Black,
                        ),
                    ) { Text("New game") }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF334155),
                            contentColor = Color.White,
                        ),
                    ) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun PassScreen(
    nextPlayer: Player,
    isSetup: Boolean,
    pile: Int,
    log: String?,
    onReveal: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Pass the device to", color = Color.White, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            nextPlayer.name,
            color = Color(0xFFE6B54A),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        if (isSetup) {
            Text(
                "Setup — swap up to 2 hand cards with overs",
                color = Color.White,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Pile: $pile cards", color = Color.White, fontSize = 14.sp)
        if (log != null) {
            Spacer(Modifier.height(4.dp))
            Text(log, color = Color.White, fontSize = 12.sp)
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onReveal,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE6B54A),
                contentColor = Color.Black,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "I'm ${nextPlayer.name} — show my cards",
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ScoreboardScreen(
    state: GameState,
    roundOrder: List<Int>,
    lastRoundPoints: List<Int>,
    cumulativeScores: List<Int>,
    matchOver: Boolean,
    seriesTarget: Int,
    isSeries: Boolean,
    onContinue: () -> Unit,
    onNewGame: () -> Unit,
) {
    val winnerName = roundOrder.firstOrNull()?.let { state.players[it].name } ?: "?"
    val matchWinnerName = if (matchOver) {
        cumulativeScores.indices
            .maxByOrNull { cumulativeScores[it] }
            ?.let { state.players[it].name }
    } else null

    var showNewGameConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            if (matchOver) "Match over" else "Round complete",
            color = Color(0xFFE6B54A),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (matchOver) "$matchWinnerName wins the match!" else "$winnerName took the round",
            color = Color.White,
            fontSize = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x33000000))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text("#", color = Color(0xFFE6B54A), modifier = Modifier.width(24.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(
                "Player",
                color = Color(0xFFE6B54A),
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Round",
                color = Color(0xFFE6B54A),
                modifier = Modifier.width(64.dp),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
            )
            Text(
                "Total",
                color = Color(0xFFE6B54A),
                modifier = Modifier.width(56.dp),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            roundOrder.forEachIndexed { idx, pid ->
                val name = state.players[pid].name
                val pts = lastRoundPoints.getOrNull(pid) ?: 0
                val total = cumulativeScores.getOrNull(pid) ?: 0
                val ptsLabel = if (pts > 0) "+$pts" else pts.toString()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text("${idx + 1}", color = Color.White, modifier = Modifier.width(24.dp), fontSize = 13.sp)
                    Text(
                        name,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(ptsLabel, color = Color.White, modifier = Modifier.width(64.dp), fontSize = 13.sp, maxLines = 1)
                    Text(total.toString(), color = Color.White, modifier = Modifier.width(56.dp), fontSize = 13.sp, maxLines = 1)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            if (isSeries) "First to $seriesTarget wins the match." else "Single-game match.",
            color = Color.White,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))

        Text(
            "Round history",
            color = Color(0xFFE6B54A),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        LogPanel(s = state, minHeight = 120, maxHeight = 220)

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!matchOver && isSeries) {
                Button(
                    onClick = onContinue,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE6B54A),
                        contentColor = Color.Black,
                    ),
                    modifier = Modifier.weight(1f),
                ) { Text("Continue", maxLines = 1) }
            }
            Button(
                onClick = { showNewGameConfirm = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF334155),
                    contentColor = Color.White,
                ),
                modifier = Modifier.weight(1f),
            ) { Text("New game", maxLines = 1) }
        }
    }

    if (showNewGameConfirm) {
        Dialog(onDismissRequest = { showNewGameConfirm = false }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1F2937),
            ) {
                Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                    Text(
                        "Start a new game?",
                        color = Color(0xFFE6B54A),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (matchOver)
                            "This will return to the menu and discard the finished match."
                        else
                            "This will end the current match and return to the menu.",
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { showNewGameConfirm = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF334155),
                                contentColor = Color.White,
                            ),
                            modifier = Modifier.weight(1f),
                        ) { Text("Cancel") }
                        Button(
                            onClick = {
                                showNewGameConfirm = false
                                onNewGame()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE6B54A),
                                contentColor = Color.Black,
                            ),
                            modifier = Modifier.weight(1f),
                        ) { Text("New game") }
                    }
                }
            }
        }
    }
}

@Composable
private fun OpponentsRow(s: GameState, viewerId: Int) {
    val opponents = s.players.filter { it.id != viewerId }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        opponents.forEach { p ->
            val highlight = p.id == s.currentPlayer || p.id == s.setupPlayer
            OpponentColumn(p, isTurn = highlight)
        }
    }
}

@Composable
private fun OpponentColumn(p: Player, isTurn: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "${p.name}${if (isTurn) " ←" else ""}",
            color = if (isTurn) Color(0xFFE6B54A) else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("Hand: ${p.hand.size}", color = Color.White, fontSize = 11.sp, maxLines = 1)
        Row {
            repeat(minOf(p.hand.size, 5)) {
                CardView(null, CardFace.BACK, widthDp = 22, heightDp = 32,
                    modifier = Modifier.padding(1.dp))
            }
        }
        Row {
            val maxSlots = maxOf(p.unders.size, p.overs.size)
            repeat(maxSlots) { i ->
                Box(modifier = Modifier.padding(horizontal = 1.dp)) {
                    if (i < p.unders.size) {
                        CardView(null, CardFace.BACK, widthDp = 26, heightDp = 38)
                    } else {
                        CardView(null, CardFace.EMPTY, widthDp = 26, heightDp = 38)
                    }
                    if (i < p.overs.size) {
                        CardView(
                            p.overs[i], CardFace.FRONT,
                            widthDp = 26, heightDp = 38,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
        if (p.isFinished) Text("FINISHED", color = Color(0xFFE6B54A), fontSize = 11.sp)
    }
}

@Composable
private fun PileArea(s: GameState) {
    val top = s.pile.lastOrNull()
    val top3 = s.pile.takeLast(3)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(end = 16.dp),
        ) {
            Text("Draw (${s.drawPile.size})", color = Color.White, fontSize = 12.sp)
            Box(
                modifier = Modifier.size(width = 60.dp, height = 100.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (s.drawPile.isEmpty()) {
                    CardView(null, CardFace.EMPTY, widthDp = 52, heightDp = 74)
                } else {
                    CardView(null, CardFace.BACK, widthDp = 52, heightDp = 74)
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Pile (${s.pile.size})", color = Color.White, fontSize = 12.sp)
            Box(
                modifier = Modifier.size(width = 80.dp, height = 100.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (top == null) {
                    Text("empty", color = Color.White)
                } else {
                    top3.forEachIndexed { i, c ->
                        CardView(
                            c, CardFace.FRONT,
                            modifier = Modifier.padding(start = (i * 8).dp),
                            widthDp = 52, heightDp = 74,
                        )
                    }
                }
            }
            val eff = s.effectiveTopRank
            val label = when (eff) {
                null -> "Any card"
                11 -> "Must play ≥ J"
                12 -> "Must play ≥ Q"
                13 -> "Must play ≥ K"
                14 -> "Must play ≥ A"
                else -> "Must play ≥ $eff"
            }
            Text(label, color = Color(0xFFE6B54A), fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun LogPanel(
    s: GameState,
    minHeight: Int = 92,
    maxHeight: Int = 140,
) {
    val scrollState = rememberScrollState()
    val lineCount = s.log.size
    androidx.compose.runtime.LaunchedEffect(lineCount) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    Surface(
        color = Color(0x44000000),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight.dp, max = maxHeight.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .verticalScroll(scrollState),
        ) {
            s.log.forEach {
                Text(it, color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SwapArea(
    state: GameState,
    viewerId: Int,
    swapHand: Int?,
    swapOvers: Int?,
    onSelectHand: (Int) -> Unit,
    onSelectOvers: (Int) -> Unit,
    onDone: () -> Unit,
) {
    val viewer = state.players.firstOrNull { it.id == viewerId } ?: return
    val isMyTurn = state.setupPlayer == viewer.id && viewer.isHuman
    val swapsLeft = (2 - state.setupSwapsDone).coerceAtLeast(0)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "${viewer.name} — pre-game swap (${swapsLeft} swap${if (swapsLeft == 1) "" else "s"} left)",
            color = if (isMyTurn) Color(0xFFE6B54A) else Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "Tap an over and a hand card to swap them. Tap Done when finished.",
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val maxSlots = maxOf(viewer.unders.size, viewer.overs.size)
            repeat(maxSlots) { i ->
                Box {
                    if (i < viewer.unders.size) {
                        CardView(null, CardFace.BACK, widthDp = 54, heightDp = 76)
                    } else {
                        CardView(null, CardFace.EMPTY, widthDp = 54, heightDp = 76)
                    }
                    if (i < viewer.overs.size) {
                        val canPick = isMyTurn && swapsLeft > 0
                        CardView(
                            viewer.overs[i], CardFace.FRONT,
                            widthDp = 54, heightDp = 76,
                            selected = i == swapOvers,
                            onClick = if (canPick) { { onSelectOvers(i) } } else null,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "${viewer.name} — Hand (${viewer.hand.size})",
            color = if (isMyTurn) Color(0xFFE6B54A) else Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            viewer.hand.forEachIndexed { i, c ->
                val canPick = isMyTurn && swapsLeft > 0
                CardView(
                    c, CardFace.FRONT,
                    widthDp = 54, heightDp = 76,
                    selected = i == swapHand,
                    onClick = if (canPick) { { onSelectHand(i) } } else null,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onDone,
            enabled = isMyTurn,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE6B54A),
                contentColor = Color.Black,
            ),
        ) { Text("Done swapping", maxLines = 1) }
    }
}

@Composable
private fun ViewerArea(
    state: GameState,
    viewerId: Int,
    selected: Set<Int>,
    selectedOversAddon: Set<Int>,
    mode: GameMode,
    rapidPlay: Boolean,
    onToggle: (Int) -> Unit,
    onToggleOversAddon: (Int) -> Unit,
    onFlipUnder: (Int) -> Unit,
    onPlay: () -> Unit,
    onPickUp: () -> Unit,
    onToggleRapidPlay: () -> Unit,
) {
    val viewer = state.players.firstOrNull { it.id == viewerId } ?: return
    val isMyTurn = state.currentPlayer == viewer.id
    val isUndersPhase = viewer.activeZone == Zone.UNDERS
    val isOversPhase = viewer.activeZone == Zone.OVERS
    val isHandPhase = viewer.activeZone == Zone.HAND

    fun isCardEligible(rank: Int): Boolean {
        if (!isMyTurn) return true
        if (rank == 2 || rank == 10) return true
        val top = state.effectiveTopRank ?: return true
        return rank >= top
    }

    // Only treat selection as "in this zone" when the active zone matches —
    // this prevents hand-index selections from bleeding into overs highlights.
    val handSelected = if (isHandPhase) selected else emptySet()
    val oversSelected = if (isOversPhase) selected else emptySet()

    // Last-hand pairing rule: when the current hand selection would empty the
    // hand, allow selecting same-rank overs as addons.
    val handWouldEmpty = isHandPhase &&
        handSelected.isNotEmpty() &&
        handSelected.size == viewer.hand.size &&
        handSelected.mapNotNull { viewer.hand.getOrNull(it)?.rank }.distinct().size == 1
    val pairingRank = if (handWouldEmpty) viewer.hand[handSelected.first()].rank else null

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val maxSlots = maxOf(viewer.unders.size, viewer.overs.size)
            repeat(maxSlots) { i ->
                Box {
                    if (i < viewer.unders.size) {
                        val underClickable = isMyTurn && isUndersPhase
                        CardView(
                            null,
                            CardFace.BACK,
                            widthDp = 54, heightDp = 76,
                            selected = underClickable,
                            onClick = if (underClickable) { { onFlipUnder(i) } } else null,
                        )
                    } else {
                        CardView(null, CardFace.EMPTY, widthDp = 54, heightDp = 76)
                    }
                    if (i < viewer.overs.size) {
                        val oversPhaseClick = isMyTurn && isOversPhase
                        val canAddonClick = isMyTurn && isHandPhase && pairingRank != null &&
                            viewer.overs[i].rank == pairingRank
                        val isSel = (oversPhaseClick && i in oversSelected) ||
                            (canAddonClick && i in selectedOversAddon)
                        val onOverClick: (() -> Unit)? = when {
                            oversPhaseClick -> {
                                { onToggle(i) }
                            }
                            canAddonClick -> {
                                { onToggleOversAddon(i) }
                            }
                            else -> null
                        }
                        val overCard = viewer.overs[i]
                        val dimOver = isMyTurn && isOversPhase && !isCardEligible(overCard.rank)
                        CardView(
                            overCard, CardFace.FRONT,
                            widthDp = 54, heightDp = 76,
                            selected = isSel,
                            onClick = onOverClick,
                            modifier = Modifier.padding(top = 10.dp),
                            dimmed = dimOver,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        val label = when {
            isUndersPhase -> "${viewer.name} — Unders (${viewer.unders.size})${if (isMyTurn) " — tap a face-down card to flip" else ""}"
            isOversPhase -> "${viewer.name} — Overs (${viewer.overs.size})${if (isMyTurn) " — your turn" else ""}"
            else -> "${viewer.name} — Hand (${viewer.hand.size})${if (isMyTurn) " — your turn" else ""}"
        }
        Text(
            label,
            color = if (isMyTurn) Color(0xFFE6B54A) else Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            viewer.hand.forEachIndexed { i, c ->
                val clickable = isMyTurn && isHandPhase
                val dim = isMyTurn && isHandPhase && !isCardEligible(c.rank)
                CardView(
                    c, CardFace.FRONT,
                    widthDp = 54, heightDp = 76,
                    selected = i in handSelected,
                    onClick = if (clickable) { { onToggle(i) } } else null,
                    dimmed = dim,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onPlay,
                enabled = isMyTurn && selected.isNotEmpty() && !isUndersPhase,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE6B54A), contentColor = Color.Black),
            ) { Text("Play", maxLines = 1) }
            Button(
                onClick = onPickUp,
                enabled = isMyTurn && state.pile.isNotEmpty() && !isUndersPhase,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155), contentColor = Color.White),
            ) { Text("Pick up", maxLines = 1) }
            if (mode == GameMode.VS_CPU) {
                Button(
                    onClick = onToggleRapidPlay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (rapidPlay) Color(0xFFD97706) else Color(0xFF334155),
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        if (rapidPlay) "Rapid: ON" else "Rapid: OFF",
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

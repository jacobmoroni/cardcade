package com.cardcade.app.games.scum.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cardcade.app.games.scum.game.Card
import com.cardcade.app.games.scum.game.GameEngine
import com.cardcade.app.games.scum.game.GameState
import com.cardcade.app.games.scum.game.Phase
import com.cardcade.app.games.scum.game.Player
import com.cardcade.app.games.scum.game.Role
import com.cardcade.app.games.scum.game.SessionMode
import com.cardcade.app.games.scum.game.SetupOptions
import com.cardcade.app.games.scum.game.TradeSlot
import com.cardcade.app.games.scum.game.roleFor
import com.cardcade.app.games.scum.game.swapCountFor

@Composable
fun GameScreen(
    vm: GameViewModel,
    onExitToMenu: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val rapid by vm.rapidPlay.collectAsState()
    val state = ui.gameState
    val options = ui.options
    if (state == null || options == null) return

    // Who is the viewer of this device? In pass-and-play everyone takes turns
    // on the one screen (so the local seat is whoever's up); in AI-fill mode
    // the viewer is always the first human seat and never sees AI hands.
    val localSeat = localPlayerSeat(state, options)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF063F23))
            .safeDrawingPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HeaderBar(
                state = state,
                options = options,
                rapidPlay = rapid,
                onToggleRapid = vm::toggleRapidPlay,
                onExit = onExitToMenu,
            )
            Spacer(Modifier.height(6.dp))
            SeatStrip(state = state, localSeat = localSeat)
            Spacer(Modifier.height(6.dp))
            PileView(state = state)
            Spacer(Modifier.height(8.dp))
            HistoryWidget(log = state.log, modifier = Modifier.weight(1f))
            Spacer(Modifier.height(8.dp))
            HandAndActions(
                state = state,
                options = options,
                localSeat = localSeat,
                selected = ui.selectedCards,
                onToggle = vm::toggleSelection,
                onPlay = vm::playSelected,
                onPass = vm::passTurn,
                onStopSolo = vm::stopSoloStreak,
            )
        }

        when (state.phase) {
            Phase.TRADING -> TradingOverlay(state = state, vm = vm)
            Phase.ROUND_END -> RoundEndOverlay(state = state, options = options, onNext = vm::startNextRound)
            Phase.MATCH_END -> MatchEndOverlay(state = state, onExit = onExitToMenu, onReplay = {
                vm.clearGame()
                onExitToMenu()
            })
            else -> {}
        }
    }
}

// ---- Header + seats ---------------------------------------------------------------

@Composable
private fun HeaderBar(
    state: GameState,
    options: SetupOptions,
    rapidPlay: Boolean,
    onToggleRapid: () -> Unit,
    onExit: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onExit) {
            Text("← Menu", color = Color(0xFFE6B54A), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Round ${state.round}",
            color = Color(0xFFE6B54A),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(10.dp))
        val seriesDesc = when (options.seriesConfig.format) {
            com.cardcade.app.games.scum.game.SeriesFormat.TARGET_SCORE ->
                "to ${options.seriesConfig.targetScore}"
            com.cardcade.app.games.scum.game.SeriesFormat.FIXED_ROUNDS ->
                "of ${options.seriesConfig.fixedRounds}"
        }
        Text(seriesDesc, color = Color.White, fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        Surface(
            color = if (rapidPlay) Color(0xFFE6B54A) else Color(0xFF334155),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(vertical = 2.dp),
        ) {
            TextButton(onClick = onToggleRapid) {
                Text(
                    if (rapidPlay) "⚡ Rapid" else "Rapid",
                    color = if (rapidPlay) Color.Black else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun localPlayerSeat(state: GameState, options: SetupOptions): Int = when (options.mode) {
    SessionMode.PASS_AND_PLAY -> state.currentSeat
    SessionMode.AI_FILL -> state.players.indexOfFirst { it.isHuman }.coerceAtLeast(0)
}

/** 1-based finish place ("1st", "2nd", "3rd", "4th", …). */
private fun ordinal(place: Int): String {
    val mod100 = place % 100
    val mod10 = place % 10
    val suffix = when {
        mod100 in 11..13 -> "th"
        mod10 == 1 -> "st"
        mod10 == 2 -> "nd"
        mod10 == 3 -> "rd"
        else -> "th"
    }
    return "$place$suffix"
}

/**
 * Other seats displayed in turn-order starting from the seat after the local
 * player — so reading left-to-right you see who plays next, then next, and
 * so on around the table. The local seat is omitted (their hand lives at the
 * bottom of the screen).
 */
@Composable
private fun SeatStrip(state: GameState, localSeat: Int) {
    val n = state.players.size
    val order = (1 until n).map { (localSeat + it) % n }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        order.forEach { idx ->
            val p = state.players[idx]
            val finishPosIdx = state.finishOrder.indexOf(idx)
            SeatCard(
                index = idx,
                player = p,
                isCurrent = idx == state.currentSeat && state.phase == Phase.PLAYING,
                role = state.previousRoles.getOrElse(idx) { Role.COMMONER },
                score = state.cumulativeScores.getOrElse(idx) { 0 },
                showRole = state.round > 1 || state.phase != Phase.PLAYING,
                finishPlace = if (finishPosIdx >= 0) finishPosIdx + 1 else null,
            )
        }
    }
}

@Composable
private fun SeatCard(
    index: Int,
    player: Player,
    isCurrent: Boolean,
    role: Role,
    score: Int,
    showRole: Boolean,
    finishPlace: Int?,
) {
    Surface(
        color = Color(0xFF11402B),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .width(112.dp)
            .padding(vertical = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                player.name,
                color = if (isCurrent) Color(0xFFE6B54A) else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${player.hand.size} card${if (player.hand.size == 1) "" else "s"}",
                color = Color.White,
                fontSize = 11.sp,
            )
            if (showRole && role != Role.COMMONER) {
                Text(role.display, color = Color(0xFFE6B54A), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("Score: $score", color = Color(0xFFB9F5C9), fontSize = 11.sp)
            when {
                player.isOut && finishPlace != null ->
                    Text(
                        "Finished ${ordinal(finishPlace)}",
                        color = Color(0xFFE6B54A),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                player.isOut ->
                    Text("OUT", color = Color(0xFFFCA5A5), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                player.passedThisTrick ->
                    Text("passed", color = Color(0xFF9CA3AF), fontSize = 10.sp)
                isCurrent ->
                    Text("• turn", color = Color(0xFFE6B54A), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                else -> Spacer(Modifier.height(12.dp))
            }
        }
    }
}

// ---- Pile + log ------------------------------------------------------------------

/**
 * Shows only the current top play (the most recent set on the pile) — the
 * play that has to be beaten. Older plays move off into the history widget.
 */
@Composable
private fun PileView(state: GameState) {
    Surface(
        color = Color(0xFF11402B),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            val desc = if (state.pile.setSize == 0) {
                "Open — ${state.players[state.currentSeat].name} to lead"
            } else {
                val label = com.cardcade.app.games.scum.game.rankLabel(state.pile.topRank)
                val leader = state.players[state.pile.leaderSeat].name
                "Top play: ${state.pile.setSize} × $label — $leader"
            }
            Text(desc, color = Color(0xFFE6B54A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            val topPlay = state.pile.cards.takeLast(state.pile.setSize)
            if (topPlay.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    topPlay.forEach { MiniCard(it) }
                }
            } else {
                Text("(no cards yet)", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

/**
 * Full round history — auto-scrolls to the bottom so the latest entry is
 * always in view. Cleared between rounds by the engine.
 */
@Composable
private fun HistoryWidget(log: List<String>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(log.size) {
        if (log.isNotEmpty()) {
            listState.animateScrollToItem(log.size - 1)
        }
    }
    Surface(
        color = Color(0xFF0A2E1E),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                "Round history",
                color = Color(0xFFE6B54A),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(log) { entry ->
                    Text(entry, color = Color(0xFFB9F5C9), fontSize = 12.sp)
                }
            }
        }
    }
}

// ---- Hand + actions --------------------------------------------------------------

@Composable
private fun HandAndActions(
    state: GameState,
    options: SetupOptions,
    localSeat: Int,
    selected: Set<Card>,
    onToggle: (Card) -> Unit,
    onPlay: () -> Unit,
    onPass: () -> Unit,
    onStopSolo: () -> Unit,
) {
    // Only ever render the local viewer's hand — other players (humans or AI)
    // stay hidden. The local seat is always a human seat; if they're also the
    // current turn-taker, the Play/Pass buttons light up.
    val localPlayer = state.players[localSeat]
    val isMyTurn =
        state.phase == Phase.PLAYING && state.currentSeat == localSeat && localPlayer.isHuman

    val hand = localPlayer.hand.sortedBy(Card::sortKey)
    val legalPlays = if (isMyTurn) {
        GameEngine.legalPlays(hand, state.pile, options.jokerBeatsAll)
    } else emptyList()
    val playableCards = legalPlays.flatten().toSet()

    val sameRank = selected.isNotEmpty() && selected.map(Card::rank).distinct().size == 1
    val selectionIsLegal = sameRank && legalPlays.any { it.toSet() == selected.toSet() }

    val stillInTrick = state.players.withIndex().count { !it.value.isOut && !it.value.passedThisTrick }
    val isSolo = isMyTurn && stillInTrick == 1 && state.pile.setSize > 0

    val heading = when {
        localPlayer.isOut -> {
            val pos = state.finishOrder.indexOf(localSeat)
            if (pos >= 0) "You finished ${ordinal(pos + 1)} — waiting for round to end"
            else "You're out — waiting for round to end"
        }
        state.phase != Phase.PLAYING -> "Your hand"
        isMyTurn -> "Your turn — ${localPlayer.name}"
        else -> "${state.players[state.currentSeat].name} is playing…"
    }

    Column {
        Text(
            heading,
            color = Color(0xFFE6B54A),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            hand.forEach { card ->
                CardView(
                    card = card,
                    selected = card in selected,
                    enabled = isMyTurn && card in playableCards,
                    onClick = { onToggle(card) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onPlay,
                enabled = isMyTurn && selectionIsLegal,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE6B54A),
                    contentColor = Color.Black,
                    disabledContainerColor = Color(0xFF334155),
                    disabledContentColor = Color(0xFF9CA3AF),
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                val sel = selected.size
                Text(
                    if (sel == 0) "Play" else "Play ($sel)",
                    fontWeight = FontWeight.Bold,
                )
            }
            if (isSolo) {
                Button(
                    onClick = onStopSolo,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0B6A3A),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("I'm done — lead next", fontWeight = FontWeight.SemiBold) }
            } else {
                Button(
                    onClick = onPass,
                    enabled = isMyTurn && state.pile.setSize > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF334155),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF1F2937),
                        disabledContentColor = Color(0xFF6B7280),
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Pass", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

// ---- Trading overlay -------------------------------------------------------------

@Composable
private fun TradingOverlay(state: GameState, vm: GameViewModel) {
    // Trades process strictly in order. If the very first pending trade
    // belongs to a human, prompt them; otherwise the AI is working and we
    // show nothing.
    val slot = state.pendingTrades.firstOrNull() ?: return
    val fromPlayer = state.players[slot.fromSeat]
    if (!fromPlayer.isHuman) return

    val toPlayer = state.players[slot.toSeat]
    val ui by vm.ui.collectAsState()
    val selected = ui.selectedCards

    val eligible = (if (slot.jokerLocked) fromPlayer.hand.filter { !it.isJoker } else fromPlayer.hand)
        .sortedBy(Card::sortKey)
    val required = slot.required.coerceAtMost(eligible.size)

    // Royalty sides (mustPickLowest = true) get to choose what to send back.
    // Scum sides hand over their highest automatically — they only get a
    // notification screen with no picker.
    val userChooses = slot.mustPickLowest
    val autoPick = eligible.sortedByDescending(Card::sortKey).take(required)
    val effectiveSelection = if (userChooses) {
        selected.filter { it in eligible }.sortedBy(Card::sortKey)
    } else {
        autoPick
    }
    val valid = effectiveSelection.size == required

    // If the human already received cards from the other party in this swap
    // pair, show those so they can decide what to send back.
    val receivedFromCounterparty = state.completedTrades.firstOrNull {
        it.slot.fromSeat == slot.toSeat && it.slot.toSeat == slot.fromSeat
    }

    Dialog(onDismissRequest = {}) {
        Surface(
            color = Color(0xFF1F2937),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Trade: ${fromPlayer.name} → ${toPlayer.name}",
                    color = Color(0xFFE6B54A),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                )
                val instruction = when {
                    userChooses ->
                        "Pick $required card${if (required == 1) "" else "s"} to give to ${toPlayer.name}. Tradition says your lowest — but it's your call."
                    slot.jokerLocked ->
                        "Your $required highest non-joker card${if (required == 1) "" else "s"} go to ${toPlayer.name}. Jokers stay with you."
                    else ->
                        "Your $required highest card${if (required == 1) "" else "s"} go to ${toPlayer.name}."
                }
                Text(instruction, color = Color.White, fontSize = 13.sp)

                receivedFromCounterparty?.let { received ->
                    Text(
                        "${toPlayer.name} sent you:",
                        color = Color(0xFFE6B54A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        received.cards.sortedByDescending(Card::sortKey).forEach { MiniCard(it) }
                    }
                }

                if (userChooses) {
                    Text(
                        "Selected (${effectiveSelection.size}/$required):",
                        color = Color(0xFFB9F5C9),
                        fontSize = 12.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        effectiveSelection.forEach { MiniCard(it) }
                        if (effectiveSelection.isEmpty()) {
                            Text("(tap cards below)", color = Color(0xFF9CA3AF), fontSize = 12.sp)
                        }
                    }

                    Text("Your hand:", color = Color(0xFFB9F5C9), fontSize = 12.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        eligible.forEach { card ->
                            CardView(
                                card = card,
                                selected = card in selected,
                                enabled = true,
                                onClick = { vm.toggleSelection(card) },
                            )
                        }
                    }
                } else {
                    Text(
                        "Sending to ${toPlayer.name}:",
                        color = Color(0xFFB9F5C9),
                        fontSize = 12.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        effectiveSelection.forEach { MiniCard(it) }
                    }
                }

                Button(
                    onClick = { vm.executeHumanTrade(slot, effectiveSelection) },
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE6B54A),
                        contentColor = Color.Black,
                        disabledContainerColor = Color(0xFF334155),
                        disabledContentColor = Color(0xFF9CA3AF),
                    ),
                ) {
                    Text(
                        if (userChooses) "Confirm trade" else "Send these cards",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ---- Round end & match end -------------------------------------------------------

@Composable
private fun RoundEndOverlay(state: GameState, options: SetupOptions, onNext: () -> Unit) {
    val n = options.totalPlayers
    // Cumulative totals haven't been applied to state yet — preview them so
    // players can see where everyone will sit going into the next round.
    val previewTotals = state.finishOrder.mapIndexed { pos, seat ->
        val role = roleFor(pos, n, options.royaltyTiers)
        Triple(seat, role, state.cumulativeScores[seat] + role.points)
    }
    val targetText = when (options.seriesConfig.format) {
        com.cardcade.app.games.scum.game.SeriesFormat.TARGET_SCORE ->
            "  /  target ${options.seriesConfig.targetScore}"
        com.cardcade.app.games.scum.game.SeriesFormat.FIXED_ROUNDS ->
            "  /  round ${state.round} of ${options.seriesConfig.fixedRounds}"
    }

    Dialog(onDismissRequest = {}) {
        Surface(
            color = Color(0xFF1F2937),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Round ${state.round} complete",
                    color = Color(0xFFE6B54A),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                )

                Text("Finish order:", color = Color.White, fontSize = 13.sp)
                previewTotals.forEachIndexed { pos, (seat, role, _) ->
                    val swaps = swapCountFor(pos, n, options.royaltyTiers, options.topTierSwaps)
                    val suffix = if (swaps > 0) " · trades $swaps" else ""
                    val sign = if (role.points >= 0) "+" else ""
                    Text(
                        "${pos + 1}. ${state.players[seat].name} — ${role.display} ($sign${role.points})$suffix",
                        color = Color(0xFFB9F5C9),
                        fontSize = 13.sp,
                    )
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    "Total scores$targetText",
                    color = Color(0xFFE6B54A),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                // Sort by total desc so the standings read top-down.
                previewTotals.sortedByDescending { it.third }
                    .forEach { (seat, role, total) ->
                        val sign = if (role.points >= 0) "+" else ""
                        Text(
                            "${state.players[seat].name}: $total  ($sign${role.points} this round)",
                            color = Color.White,
                            fontSize = 13.sp,
                        )
                    }

                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE6B54A),
                        contentColor = Color.Black,
                    ),
                ) { Text("Next round", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun MatchEndOverlay(state: GameState, onExit: () -> Unit, onReplay: () -> Unit) {
    Dialog(onDismissRequest = {}) {
        Surface(
            color = Color(0xFF1F2937),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val winnerIdx = state.cumulativeScores.indices.maxByOrNull { state.cumulativeScores[it] } ?: 0
                Text(
                    "Match over!",
                    color = Color(0xFFE6B54A),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "${state.players[winnerIdx].name} wins with ${state.cumulativeScores[winnerIdx]} points.",
                    color = Color.White,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text("Final scores:", color = Color(0xFFB9F5C9), fontSize = 12.sp)
                state.players.forEachIndexed { i, p ->
                    Text(
                        "${p.name}: ${state.cumulativeScores[i]}",
                        color = Color.White,
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onReplay,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0B6A3A),
                            contentColor = Color.White,
                        ),
                    ) { Text("New match") }
                    Button(
                        onClick = onExit,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE6B54A),
                            contentColor = Color.Black,
                        ),
                    ) { Text("Menu", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}


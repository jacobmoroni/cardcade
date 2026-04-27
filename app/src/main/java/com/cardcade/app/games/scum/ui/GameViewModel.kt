package com.cardcade.app.games.scum.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cardcade.app.games.scum.game.AIDecision
import com.cardcade.app.games.scum.game.AIPlayer
import com.cardcade.app.games.scum.game.Card
import com.cardcade.app.games.scum.game.GameEngine
import com.cardcade.app.games.scum.game.GameState
import com.cardcade.app.games.scum.game.GameStateJson
import com.cardcade.app.games.scum.game.Phase
import com.cardcade.app.games.scum.game.Role
import com.cardcade.app.games.scum.game.SessionMode
import com.cardcade.app.games.scum.game.SetupOptions
import com.cardcade.app.games.scum.game.TradeSlot
import com.cardcade.app.games.scum.net.LanMessage
import com.cardcade.app.games.scum.net.LanSession
import com.cardcade.app.games.scum.net.LEAVE_SENTINEL
import com.cardcade.app.games.scum.net.MoveAction
import com.cardcade.app.games.scum.persistence.GameSnapshot
import com.cardcade.app.games.scum.persistence.GameSnapshotStore
import com.cardcade.app.games.scum.persistence.UserPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds the Scum game state + user selection, drives AI turns with a small
 * delay, and persists snapshots for Continue Game.
 *
 * In LAN mode the HOST runs the authoritative engine and broadcasts state to
 * connected clients after every move. The CLIENT only sends moves and renders
 * state received from the host.
 */
class GameViewModel(app: Application) : AndroidViewModel(app) {

    data class UiState(
        val gameState: GameState? = null,
        val options: SetupOptions? = null,
        val selectedCards: Set<Card> = emptySet(),
        val roleForSeat: List<Role> = emptyList(),
        /** LAN CLIENT: the seat index on this device. HOST + local games: 0. */
        val mySeat: Int = 0,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _rapidPlay = MutableStateFlow(UserPreferences.getRapidPlay(app))
    val rapidPlay: StateFlow<Boolean> = _rapidPlay.asStateFlow()

    private var aiJob: Job? = null

    // LAN state
    private var lanSession: LanSession? = null
    private var isLanHost: Boolean = false
    private val clientSeatMap = mutableMapOf<Int, Int>()   // clientId → seatIndex (HOST only)

    // ---- Lifecycle --------------------------------------------------------------

    fun toggleRapidPlay() {
        val next = !_rapidPlay.value
        _rapidPlay.value = next
        UserPreferences.setRapidPlay(getApplication(), next)
    }

    private fun aiPlayDelay(): Long = if (_rapidPlay.value) 120L else 600L
    private fun aiTradeDelay(): Long = if (_rapidPlay.value) 120L else 500L

    // ---- Local game start -------------------------------------------------------

    fun startGame(opts: SetupOptions) {
        val (state, _) = GameEngine.startMatch(opts)
        commit(state, opts)
        scheduleAiIfNeeded()
    }

    fun restoreFrom(snapshot: GameSnapshot) {
        commit(snapshot.state, snapshot.options)
        scheduleAiIfNeeded()
    }

    fun clearGame() {
        aiJob?.cancel()
        lanSession?.close()
        lanSession = null
        isLanHost = false
        clientSeatMap.clear()
        _ui.value = UiState()
        GameSnapshotStore.clear(getApplication())
    }

    // ---- LAN host ---------------------------------------------------------------

    /**
     * Called by ScumApp when this device hosts a LAN match and the lobby is
     * ready. [playerNames] maps seatIndex → display name for all seats.
     */
    fun startLanGame(
        opts: SetupOptions,
        session: LanSession,
        seatMap: Map<Int, Int>,
        playerNames: Map<Int, String>,
    ) {
        lanSession = session
        isLanHost = true
        clientSeatMap.clear()
        clientSeatMap.putAll(seatMap)

        val (rawState, _) = GameEngine.startMatch(opts)
        val state = rawState.copy(
            players = rawState.players.mapIndexed { idx, p ->
                playerNames[idx]?.let { p.copy(name = it) } ?: p
            },
        )

        commit(state, opts)
        scheduleAiIfNeeded()
        broadcastState(state)

        viewModelScope.launch {
            session.incoming.collect { (clientId, line) ->
                if (line == LEAVE_SENTINEL) {
                    handleClientDisconnect(clientId)
                    return@collect
                }
                val msg = LanMessage.decode(line) ?: return@collect
                handleClientMessage(clientId, msg)
            }
        }
    }

    private fun handleClientMessage(clientId: Int, msg: LanMessage) {
        val seat = clientSeatMap[clientId] ?: return
        val state = _ui.value.gameState ?: return
        val opts = _ui.value.options ?: return

        when (msg) {
            is LanMessage.Move -> {
                if (msg.seatId != seat) return
                when (val action = msg.action) {
                    is MoveAction.Play -> {
                        if (state.phase != Phase.PLAYING || state.currentSeat != seat) return
                        val next = GameEngine.play(state, seat, action.cards, opts.jokerBeatsAll)
                        commit(next, opts)
                        broadcastState(next)
                        scheduleAiIfNeeded()
                    }
                    is MoveAction.Pass -> {
                        if (state.phase != Phase.PLAYING || state.currentSeat != seat) return
                        val next = GameEngine.pass(state, seat)
                        commit(next, opts)
                        broadcastState(next)
                        scheduleAiIfNeeded()
                    }
                    is MoveAction.Trade -> {
                        if (state.phase != Phase.TRADING) return
                        val slot = state.pendingTrades.firstOrNull { it.fromSeat == seat } ?: return
                        val next = GameEngine.applyTrade(state, slot, action.cards)
                        commit(next, opts)
                        broadcastState(next)
                        scheduleAiIfNeeded()
                    }
                }
            }
            else -> Unit
        }
    }

    private fun handleClientDisconnect(clientId: Int) {
        val seat = clientSeatMap.remove(clientId) ?: return
        val state = _ui.value.gameState ?: return
        val opts = _ui.value.options ?: return
        // Convert disconnected remote seat to CPU.
        val updated = state.copy(
            players = state.players.mapIndexed { i, p ->
                if (i == seat) p.copy(isHuman = false, name = "CPU $i") else p
            },
        )
        val newOpts = opts.copy(humanCount = (opts.humanCount - 1).coerceAtLeast(1))
        commit(updated, newOpts)
        broadcastState(updated)
        scheduleAiIfNeeded()
    }

    private fun broadcastState(state: GameState) {
        val session = lanSession ?: return
        val stateJson = GameStateJson.encodeState(state)
        val line = LanMessage.State(stateJson).encode()
        clientSeatMap.keys.forEach { clientId -> session.sendToClient(clientId, line) }
    }

    // ---- LAN client -------------------------------------------------------------

    /** Called by ScumApp when this device joins an existing LAN match. */
    fun joinLanGame(session: LanSession, mySeatId: Int, opts: SetupOptions) {
        lanSession = session
        isLanHost = false
        _ui.value = _ui.value.copy(options = opts, mySeat = mySeatId)

        viewModelScope.launch {
            session.incoming.collect { (_, line) ->
                val msg = LanMessage.decode(line) ?: return@collect
                when (msg) {
                    is LanMessage.State -> {
                        val state = GameStateJson.decodeState(msg.stateJson)
                        val roles = if (state.previousRoles.size == state.players.size) {
                            state.previousRoles
                        } else {
                            List(state.players.size) { Role.COMMONER }
                        }
                        _ui.value = _ui.value.copy(
                            gameState = state,
                            options = _ui.value.options,
                            selectedCards = emptySet(),
                            roleForSeat = roles,
                        )
                    }
                    is LanMessage.End -> clearGame()
                    else -> Unit
                }
            }
        }
    }

    // ---- Human actions ----------------------------------------------------------

    fun toggleSelection(card: Card) {
        val current = _ui.value
        val state = current.gameState ?: return
        val seat = if (isLanClient()) current.mySeat else state.currentSeat
        val hand = state.players[seat].hand.sortedBy(Card::sortKey)

        // Tapping a selected card deselects just that one — lets the user trim
        // down from e.g. a triple auto-select to a pair before playing.
        if (card in current.selectedCards) {
            _ui.value = current.copy(selectedCards = current.selectedCards - card)
            return
        }

        // Tapping a new card: auto-select the right count for the trick context.
        val sameRankInHand = hand.filter { it.rank == card.rank }
        val newSelection: Set<Card> = if (state.pile.setSize == 0) {
            // Leading — select all cards of this rank so the player can deselect
            // down to the group size they want to play.
            sameRankInHand.toSet()
        } else {
            // Following — select exactly as many as the current set size requires.
            sameRankInHand.take(state.pile.setSize).toSet()
        }
        _ui.value = current.copy(selectedCards = newSelection)
    }

    fun playSelected() {
        val current = _ui.value
        val state = current.gameState ?: return
        val opts = current.options ?: return
        if (state.phase != Phase.PLAYING) return
        val seat = if (isLanClient()) current.mySeat else state.currentSeat
        if (!state.players[seat].isHuman && !isLanClient()) return
        if (isLanClient() && state.currentSeat != current.mySeat) return
        val selected = current.selectedCards.toList()
        if (selected.isEmpty() || selected.map(Card::rank).distinct().size != 1) return
        val rank = selected.first().rank
        val legal = GameEngine.legalPlays(state.players[seat].hand, state.pile, opts.jokerBeatsAll)
        // Resolve by rank+count so card-identity differences from auto-select don't block a valid play.
        val cards = legal.firstOrNull { it.size == selected.size && it.first().rank == rank } ?: return

        if (isLanClient()) {
            lanSession?.sendToHost(LanMessage.Move(seat, MoveAction.Play(cards)).encode())
            _ui.value = current.copy(selectedCards = emptySet())
            return
        }
        val next = GameEngine.play(state, seat, cards, opts.jokerBeatsAll)
        commit(next, opts, clearSelection = true)
        if (isLanHost) broadcastState(next)
        scheduleAiIfNeeded()
    }

    fun passTurn() {
        val current = _ui.value
        val state = current.gameState ?: return
        val opts = current.options ?: return
        if (state.phase != Phase.PLAYING) return
        if (state.pile.setSize == 0) return
        val seat = if (isLanClient()) current.mySeat else state.currentSeat
        if (!state.players[seat].isHuman && !isLanClient()) return
        if (isLanClient() && state.currentSeat != current.mySeat) return

        if (isLanClient()) {
            lanSession?.sendToHost(LanMessage.Move(seat, MoveAction.Pass).encode())
            return
        }
        val next = GameEngine.pass(state, seat)
        commit(next, opts, clearSelection = true)
        if (isLanHost) broadcastState(next)
        scheduleAiIfNeeded()
    }

    fun stopSoloStreak() {
        val current = _ui.value
        val state = current.gameState ?: return
        val opts = current.options ?: return
        if (state.phase != Phase.PLAYING) return
        val seat = state.currentSeat
        val stillIn = state.players.withIndex().count { !it.value.isOut && !it.value.passedThisTrick }
        if (stillIn != 1) return

        if (isLanClient()) {
            lanSession?.sendToHost(LanMessage.Move(current.mySeat, MoveAction.Pass).encode())
            return
        }
        val next = GameEngine.pass(state, seat)
        commit(next, opts, clearSelection = true)
        if (isLanHost) broadcastState(next)
        scheduleAiIfNeeded()
    }

    fun executeHumanTrade(slot: TradeSlot, cards: List<Card>) {
        val current = _ui.value
        val state = current.gameState ?: return
        val opts = current.options ?: return

        if (isLanClient()) {
            lanSession?.sendToHost(LanMessage.Move(current.mySeat, MoveAction.Trade(cards)).encode())
            return
        }
        val next = GameEngine.applyTrade(state, slot, cards)
        commit(next, opts, clearSelection = true)
        if (isLanHost) broadcastState(next)
        scheduleAiIfNeeded()
    }

    fun startNextRound() {
        if (isLanClient()) return  // CLIENT waits for HOST to advance.
        val current = _ui.value
        val state = current.gameState ?: return
        val opts = current.options ?: return
        if (state.phase != Phase.ROUND_END) return
        val next = GameEngine.startNextRound(state, opts)
        commit(next, opts, clearSelection = true)
        if (isLanHost) broadcastState(next)
        scheduleAiIfNeeded()
    }

    // ---- Internal ---------------------------------------------------------------

    private fun isLanClient(): Boolean = lanSession != null && !isLanHost

    private fun commit(state: GameState, opts: SetupOptions, clearSelection: Boolean = false) {
        val roles = if (state.previousRoles.size == state.players.size) {
            state.previousRoles
        } else {
            List(state.players.size) { Role.COMMONER }
        }
        _ui.value = _ui.value.copy(
            gameState = state,
            options = opts,
            selectedCards = if (clearSelection) emptySet() else _ui.value.selectedCards,
            roleForSeat = roles,
        )
        persistIfPlayable(state, opts)
    }

    private fun persistIfPlayable(state: GameState, opts: SetupOptions) {
        // Don't persist LAN client state — the host is authoritative.
        if (isLanClient()) return
        when (state.phase) {
            Phase.PLAYING, Phase.TRADING, Phase.ROUND_END ->
                GameSnapshotStore.save(
                    getApplication(),
                    GameSnapshot(state = state, options = opts),
                )
            Phase.MATCH_END -> GameSnapshotStore.clear(getApplication())
        }
    }

    private fun scheduleAiIfNeeded() {
        if (isLanClient()) return  // clients never run AI
        aiJob?.cancel()
        val ui = _ui.value
        val state = ui.gameState ?: return
        val opts = ui.options ?: return
        when (state.phase) {
            Phase.TRADING -> scheduleAiTrades(state, opts)
            Phase.PLAYING -> scheduleAiPlay(state, opts)
            else -> {}
        }
    }

    private fun scheduleAiTrades(state: GameState, opts: SetupOptions) {
        val slot = state.pendingTrades.firstOrNull() ?: return
        if (state.players[slot.fromSeat].isHuman) return
        aiJob = viewModelScope.launch {
            delay(aiTradeDelay())
            val hand = state.players[slot.fromSeat].hand
            val picked = GameEngine.autoPickCards(hand, slot)
            val next = GameEngine.applyTrade(state, slot, picked)
            commit(next, opts, clearSelection = false)
            if (isLanHost) broadcastState(next)
            scheduleAiIfNeeded()
        }
    }

    private fun scheduleAiPlay(state: GameState, opts: SetupOptions) {
        val seat = state.currentSeat
        val player = state.players[seat]
        if (player.isHuman) return
        aiJob = viewModelScope.launch {
            delay(aiPlayDelay())
            val latest = _ui.value.gameState ?: return@launch
            if (latest.currentSeat != seat || latest.phase != Phase.PLAYING) return@launch
            val decision = AIPlayer.decide(latest, opts.jokerBeatsAll, opts.aiDifficulty, opts)
            val next = when (decision) {
                is AIDecision.Play -> GameEngine.play(latest, seat, decision.cards, opts.jokerBeatsAll)
                AIDecision.Pass -> GameEngine.pass(latest, seat)
            }
            commit(next, opts, clearSelection = false)
            if (isLanHost) broadcastState(next)
            scheduleAiIfNeeded()
        }
    }
}

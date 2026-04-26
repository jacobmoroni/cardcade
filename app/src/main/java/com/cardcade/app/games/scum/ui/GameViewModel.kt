package com.cardcade.app.games.scum.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cardcade.app.games.scum.game.AIDecision
import com.cardcade.app.games.scum.game.AIPlayer
import com.cardcade.app.games.scum.game.Card
import com.cardcade.app.games.scum.game.GameEngine
import com.cardcade.app.games.scum.game.GameState
import com.cardcade.app.games.scum.game.Phase
import com.cardcade.app.games.scum.game.Role
import com.cardcade.app.games.scum.game.SessionMode
import com.cardcade.app.games.scum.game.SetupOptions
import com.cardcade.app.games.scum.game.TradeSlot
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
 * delay so the user can see what happens, and persists snapshots so Continue
 * Game resumes the exact position.
 */
class GameViewModel(app: Application) : AndroidViewModel(app) {

    data class UiState(
        val gameState: GameState? = null,
        val options: SetupOptions? = null,
        val selectedCards: Set<Card> = emptySet(),
        val roleForSeat: List<Role> = emptyList(),
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _rapidPlay = MutableStateFlow(UserPreferences.getRapidPlay(app))
    val rapidPlay: StateFlow<Boolean> = _rapidPlay.asStateFlow()

    private var aiJob: Job? = null

    fun toggleRapidPlay() {
        val next = !_rapidPlay.value
        _rapidPlay.value = next
        UserPreferences.setRapidPlay(getApplication(), next)
    }

    private fun aiPlayDelay(): Long = if (_rapidPlay.value) 120L else 600L
    private fun aiTradeDelay(): Long = if (_rapidPlay.value) 120L else 500L

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
        _ui.value = UiState()
        GameSnapshotStore.clear(getApplication())
    }

    fun toggleSelection(card: Card) {
        val current = _ui.value
        val newSet = current.selectedCards.toMutableSet()
        if (!newSet.add(card)) newSet.remove(card)
        _ui.value = current.copy(selectedCards = newSet)
    }

    fun playSelected() {
        val current = _ui.value
        val state = current.gameState ?: return
        val opts = current.options ?: return
        if (state.phase != Phase.PLAYING) return
        val seat = state.currentSeat
        if (!state.players[seat].isHuman) return
        val cards = current.selectedCards.toList().sortedByDescending(Card::sortKey)
        if (cards.isEmpty() || cards.map(Card::rank).distinct().size != 1) return
        val legal = GameEngine.legalPlays(state.players[seat].hand, state.pile, opts.jokerBeatsAll)
        if (!legal.any { it.toSet() == cards.toSet() }) return

        val next = GameEngine.play(state, seat, cards, opts.jokerBeatsAll)
        commit(next, opts, clearSelection = true)
        scheduleAiIfNeeded()
    }

    fun passTurn() {
        val current = _ui.value
        val state = current.gameState ?: return
        val opts = current.options ?: return
        if (state.phase != Phase.PLAYING) return
        if (state.pile.setSize == 0) return
        val seat = state.currentSeat
        if (!state.players[seat].isHuman) return
        val next = GameEngine.pass(state, seat)
        commit(next, opts, clearSelection = true)
        scheduleAiIfNeeded()
    }

    fun stopSoloStreak() {
        // User's "quit solo run" — equivalent to passing (ends trick,
        // they lead next).
        val current = _ui.value
        val state = current.gameState ?: return
        val opts = current.options ?: return
        if (state.phase != Phase.PLAYING) return
        val seat = state.currentSeat
        val stillIn = state.players.withIndex().count { !it.value.isOut && !it.value.passedThisTrick }
        if (stillIn != 1) return
        val next = GameEngine.pass(state, seat)
        commit(next, opts, clearSelection = true)
        scheduleAiIfNeeded()
    }

    fun executeHumanTrade(slot: TradeSlot, cards: List<Card>) {
        val current = _ui.value
        val state = current.gameState ?: return
        val opts = current.options ?: return
        val next = GameEngine.applyTrade(state, slot, cards)
        commit(next, opts, clearSelection = true)
        scheduleAiIfNeeded()
    }

    fun startNextRound() {
        val current = _ui.value
        val state = current.gameState ?: return
        val opts = current.options ?: return
        if (state.phase != Phase.ROUND_END) return
        val next = GameEngine.startNextRound(state, opts)
        commit(next, opts, clearSelection = true)
        scheduleAiIfNeeded()
    }

    // ---- Internal ---------------------------------------------------------------

    private fun commit(state: GameState, opts: SetupOptions, clearSelection: Boolean = false) {
        val roles = state.players.indices.map { seat ->
            if (state.previousRoles.size == state.players.size) {
                state.previousRoles[seat]
            } else Role.COMMONER
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
        // Process trades strictly in order so hand-swap visuals stay coherent.
        // If the first pending trade belongs to a human, wait for the overlay.
        val slot = state.pendingTrades.firstOrNull() ?: return
        if (state.players[slot.fromSeat].isHuman) return
        aiJob = viewModelScope.launch {
            delay(aiTradeDelay())
            val hand = state.players[slot.fromSeat].hand
            val picked = GameEngine.autoPickCards(hand, slot)
            val next = GameEngine.applyTrade(state, slot, picked)
            commit(next, opts, clearSelection = false)
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
            val decision = AIPlayer.decide(latest, opts.jokerBeatsAll, opts.aiDifficulty)
            val next = when (decision) {
                is AIDecision.Play -> GameEngine.play(latest, seat, decision.cards, opts.jokerBeatsAll)
                AIDecision.Pass -> GameEngine.pass(latest, seat)
            }
            commit(next, opts, clearSelection = false)
            scheduleAiIfNeeded()
        }
    }
}

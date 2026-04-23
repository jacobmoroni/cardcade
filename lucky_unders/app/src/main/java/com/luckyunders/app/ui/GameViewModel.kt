package com.luckyunders.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luckyunders.app.game.AIPlayer
import com.luckyunders.app.game.GameEngine
import com.luckyunders.app.game.GameState
import com.luckyunders.app.game.PlayerOrderRule
import com.luckyunders.app.game.Seat
import com.luckyunders.app.game.SeriesConfig
import com.luckyunders.app.game.SeriesFormat
import com.luckyunders.app.game.SessionMode
import com.luckyunders.app.game.SetupOptions
import com.luckyunders.app.game.Zone
import com.luckyunders.app.persistence.GameSnapshot
import com.luckyunders.app.persistence.GameSnapshotStore
import com.luckyunders.app.persistence.UserPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class GameMode { VS_CPU, PASS_AND_PLAY, ONLINE_MULTIPLAYER }

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<GameState?>(null)
    val state: StateFlow<GameState?> = _state.asStateFlow()

    private val _selected = MutableStateFlow<Set<Int>>(emptySet())
    val selectedIndices: StateFlow<Set<Int>> = _selected.asStateFlow()

    /** Secondary selection on the overs row during HAND phase (last-hand pairing). */
    private val _selectedOversAddon = MutableStateFlow<Set<Int>>(emptySet())
    val selectedOversAddon: StateFlow<Set<Int>> = _selectedOversAddon.asStateFlow()

    private val _mode = MutableStateFlow(GameMode.VS_CPU)
    val mode: StateFlow<GameMode> = _mode.asStateFlow()

    /** In pass-and-play, false means the "pass to next player" gate is up. */
    private val _revealed = MutableStateFlow(true)
    val revealed: StateFlow<Boolean> = _revealed.asStateFlow()

    private val _swapHandIndex = MutableStateFlow<Int?>(null)
    val swapHandIndex: StateFlow<Int?> = _swapHandIndex.asStateFlow()

    private val _swapOversIndex = MutableStateFlow<Int?>(null)
    val swapOversIndex: StateFlow<Int?> = _swapOversIndex.asStateFlow()

    private val _scores = MutableStateFlow<List<Int>>(emptyList())
    val scores: StateFlow<List<Int>> = _scores.asStateFlow()

    private val _roundOrder = MutableStateFlow<List<Int>>(emptyList())
    val roundOrder: StateFlow<List<Int>> = _roundOrder.asStateFlow()

    private val _lastRoundPoints = MutableStateFlow<List<Int>>(emptyList())
    val lastRoundPoints: StateFlow<List<Int>> = _lastRoundPoints.asStateFlow()

    private val _matchOver = MutableStateFlow(false)
    val matchOver: StateFlow<Boolean> = _matchOver.asStateFlow()

    /** When true, CPU turns resolve as fast as possible (1P rapid-play). */
    private val _rapidPlay = MutableStateFlow(UserPreferences.getRapidPlay(app))
    val rapidPlay: StateFlow<Boolean> = _rapidPlay.asStateFlow()

    private val _seriesConfig = MutableStateFlow(SeriesConfig())
    val seriesConfig: StateFlow<SeriesConfig> = _seriesConfig.asStateFlow()

    private var setupOptions: SetupOptions? = null
    private var roundScored: Boolean = false
    private var aiJob: Job? = null

    fun startGame(options: SetupOptions) {
        setupOptions = options
        _mode.value = when (options.mode) {
            SessionMode.SOLO -> GameMode.VS_CPU
            SessionMode.PASS_AND_PLAY -> GameMode.PASS_AND_PLAY
            SessionMode.ONLINE_LAN -> GameMode.ONLINE_MULTIPLAYER
        }
        _seriesConfig.value = options.series
        _selected.value = emptySet()
        _selectedOversAddon.value = emptySet()
        _swapHandIndex.value = null
        _swapOversIndex.value = null
        _scores.value = List(options.totalPlayers) { 0 }
        _roundOrder.value = emptyList()
        _lastRoundPoints.value = emptyList()
        _matchOver.value = false
        _rapidPlay.value = UserPreferences.getRapidPlay(getApplication())
        roundScored = false
        val seats = buildInitialSeats(options)
        _state.value = GameEngine.newGame(
            numPlayers = options.totalPlayers,
            humanCount = options.humanCount,
            startingPlayer = 0,
            seats = seats,
        )
        _revealed.value = true
        persist()
        runAi()
    }

    /** Continue the next round of the same match, applying the series' order rule. */
    fun continueMatch() {
        val s = _state.value ?: return
        val opts = setupOptions ?: return
        if (!s.isGameOver) return
        if (_matchOver.value) return
        if (_seriesConfig.value.format == SeriesFormat.SINGLE) return

        val previousSeats = s.players.map { Seat(it.name, it.isHuman) }
        val n = previousSeats.size
        val orderPre = when (_seriesConfig.value.orderRule) {
            PlayerOrderRule.MAINTAIN -> previousSeats
            PlayerOrderRule.RANDOM -> previousSeats.shuffled()
            PlayerOrderRule.WINNING_ORDER -> {
                val rank = _roundOrder.value.toMutableList()
                // Include any unfinished players at the end of the finishing list.
                for (pid in previousSeats.indices) if (pid !in rank) rank.add(pid)
                rank.map { previousSeats[it] }
            }
        }
        // Keep the bottom-of-screen player (previous seat 0) anchored at seat 0
        // so their hand stays interactive — rotate the new order around them
        // to preserve clockwise play. Rotate startingPlayer to match so the
        // intended leader still goes first.
        val anchor = previousSeats[0]
        val h = orderPre.indexOf(anchor).coerceAtLeast(0)
        val order = List(n) { i -> orderPre[(i + h) % n] }
        val startingPlayer = (n - h) % n

        roundScored = false
        _roundOrder.value = emptyList()
        _lastRoundPoints.value = emptyList()
        _selected.value = emptySet()
        _selectedOversAddon.value = emptySet()
        _swapHandIndex.value = null
        _swapOversIndex.value = null
        _rapidPlay.value = UserPreferences.getRapidPlay(getApplication())
        _state.value = GameEngine.newGame(
            numPlayers = opts.totalPlayers,
            humanCount = order.count { it.isHuman },
            startingPlayer = startingPlayer,
            seats = order,
        )
        _revealed.value = _mode.value != GameMode.PASS_AND_PLAY
        persist()
        runAi()
    }

    fun reveal() {
        _revealed.value = true
    }

    fun setRapidPlay(enabled: Boolean) {
        _rapidPlay.value = enabled
        UserPreferences.setRapidPlay(getApplication(), enabled)
        // Kick the loop in case it was idle waiting for a human.
        if (enabled) runAi()
    }

    fun toggleRapidPlay() {
        setRapidPlay(!_rapidPlay.value)
    }

    fun selectSwapHand(i: Int) {
        val s = _state.value ?: return
        if (!s.isSetupPhase) return
        val pid = s.setupPlayer ?: return
        if (!s.players[pid].isHuman) return
        if (_mode.value == GameMode.PASS_AND_PLAY && !_revealed.value) return
        if (i !in s.players[pid].hand.indices) return
        _swapHandIndex.value = if (_swapHandIndex.value == i) null else i
        tryPerformSwap()
    }

    fun selectSwapOvers(i: Int) {
        val s = _state.value ?: return
        if (!s.isSetupPhase) return
        val pid = s.setupPlayer ?: return
        if (!s.players[pid].isHuman) return
        if (_mode.value == GameMode.PASS_AND_PLAY && !_revealed.value) return
        if (i !in s.players[pid].overs.indices) return
        _swapOversIndex.value = if (_swapOversIndex.value == i) null else i
        tryPerformSwap()
    }

    private fun tryPerformSwap() {
        val s = _state.value ?: return
        val h = _swapHandIndex.value ?: return
        val o = _swapOversIndex.value ?: return
        if (s.setupSwapsDone >= 2) {
            _swapHandIndex.value = null
            _swapOversIndex.value = null
            return
        }
        _state.value = GameEngine.swapSetup(s, h, o)
        _swapHandIndex.value = null
        _swapOversIndex.value = null
        persist()
    }

    fun finishSwapping() {
        val s = _state.value ?: return
        if (!s.isSetupPhase) return
        val pid = s.setupPlayer ?: return
        if (!s.players[pid].isHuman) return
        if (_mode.value == GameMode.PASS_AND_PLAY && !_revealed.value) return
        val updated = GameEngine.finishSetup(s)
        _state.value = updated
        _swapHandIndex.value = null
        _swapOversIndex.value = null
        _selected.value = emptySet()
        _selectedOversAddon.value = emptySet()
        if (_mode.value == GameMode.PASS_AND_PLAY) {
            val nextActive = updated.setupPlayer ?: updated.currentPlayer
            if (nextActive != pid) _revealed.value = false
        }
        persist()
        runAi()
    }

    fun toggleSelect(index: Int) {
        val s = _state.value ?: return
        if (s.isSetupPhase) return
        val p = s.players[s.currentPlayer]
        if (!p.isHuman) return
        if (_mode.value == GameMode.PASS_AND_PLAY && !_revealed.value) return
        if (p.activeZone == Zone.UNDERS) return
        val cards = p.activeCards
        if (index !in cards.indices) return
        val clicked = cards[index]

        val next = _selected.value.toMutableSet()
        if (index in next) {
            next.remove(index)
        } else {
            val currentRank = next.firstOrNull()?.let { cards[it].rank }
            if (currentRank == null || currentRank == clicked.rank) {
                next.add(index)
            } else {
                next.clear()
                next.add(index)
                _selectedOversAddon.value = emptySet()
            }
        }
        _selected.value = next
        if (p.activeZone != Zone.HAND || !willEmptyHand(p, next, cards)) {
            _selectedOversAddon.value = emptySet()
        }
    }

    fun toggleOversAddon(index: Int) {
        val s = _state.value ?: return
        if (s.isSetupPhase) return
        val p = s.players[s.currentPlayer]
        if (!p.isHuman) return
        if (_mode.value == GameMode.PASS_AND_PLAY && !_revealed.value) return
        if (p.activeZone != Zone.HAND) return
        val handCards = p.hand
        if (!willEmptyHand(p, _selected.value, handCards)) return
        if (index !in p.overs.indices) return
        val selRank = _selected.value.firstOrNull()?.let { handCards[it].rank } ?: return
        if (p.overs[index].rank != selRank) return
        val next = _selectedOversAddon.value.toMutableSet()
        if (index in next) next.remove(index) else next.add(index)
        _selectedOversAddon.value = next
    }

    private fun willEmptyHand(p: com.luckyunders.app.game.Player, selected: Set<Int>, cards: List<com.luckyunders.app.game.Card>): Boolean {
        if (p.activeZone != Zone.HAND) return false
        if (selected.isEmpty()) return false
        if (selected.size != p.hand.size) return false
        val rank = cards.getOrNull(selected.first())?.rank ?: return false
        return selected.all { cards.getOrNull(it)?.rank == rank }
    }

    fun playSelected() {
        val s = _state.value ?: return
        if (s.isSetupPhase) return
        val p = s.players[s.currentPlayer]
        if (!p.isHuman) return
        if (_mode.value == GameMode.PASS_AND_PLAY && !_revealed.value) return
        val cards = p.activeCards
        val chosenHand = _selected.value.sorted().mapNotNull { cards.getOrNull(it) }
        val chosenOversAddon =
            if (p.activeZone == Zone.HAND) _selectedOversAddon.value.sorted().mapNotNull { p.overs.getOrNull(it) }
            else emptyList()
        val allCards = chosenHand + chosenOversAddon
        if (allCards.isEmpty() || !GameEngine.isLegalPlay(s, allCards)) return
        applyAndAdvance(GameEngine.playCards(s, allCards), s.currentPlayer)
    }

    fun pickUpPile() {
        val s = _state.value ?: return
        if (s.isSetupPhase) return
        val p = s.players[s.currentPlayer]
        if (!p.isHuman) return
        if (_mode.value == GameMode.PASS_AND_PLAY && !_revealed.value) return
        if (s.pile.isEmpty()) return
        applyAndAdvance(GameEngine.pickUpPile(s), s.currentPlayer)
    }

    fun flipUnder(index: Int) {
        val s = _state.value ?: return
        if (s.isSetupPhase) return
        val p = s.players[s.currentPlayer]
        if (!p.isHuman || p.activeZone != Zone.UNDERS) return
        if (_mode.value == GameMode.PASS_AND_PLAY && !_revealed.value) return
        applyAndAdvance(GameEngine.flipUnder(s, index), s.currentPlayer)
    }

    private fun applyAndAdvance(newState: GameState, previousPlayer: Int) {
        _state.value = newState
        _selected.value = emptySet()
        _selectedOversAddon.value = emptySet()
        if (_mode.value == GameMode.PASS_AND_PLAY &&
            !newState.isGameOver &&
            newState.currentPlayer != previousPlayer
        ) {
            _revealed.value = false
        }
        maybeFinalizeRound(newState)
        persist()
        runAi()
    }

    /** Award round points the first time we observe a finished round. */
    private fun maybeFinalizeRound(state: GameState) {
        if (!state.isGameOver || roundScored) return
        roundScored = true
        val n = state.players.size
        val ranks = state.winnerOrder.toMutableList()
        val loser = state.players.firstOrNull { it.id !in ranks }?.id
        if (loser != null) ranks.add(loser)
        val table = scoreTableFor(n)
        val perPlayer = MutableList(n) { 0 }
        ranks.forEachIndexed { idx, pid ->
            if (idx < table.size) perPlayer[pid] = table[idx]
        }
        val newScores = _scores.value.toMutableList()
        while (newScores.size < n) newScores.add(0)
        for (pid in 0 until n) newScores[pid] = newScores[pid] + perPlayer[pid]
        _lastRoundPoints.value = perPlayer
        _scores.value = newScores
        _roundOrder.value = ranks
        val target = _seriesConfig.value.targetScore
        val done = _seriesConfig.value.format == SeriesFormat.SINGLE ||
            newScores.any { it >= target }
        if (done) _matchOver.value = true
    }

    private fun runAi() {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            while (true) {
                val s = _state.value ?: return@launch
                if (s.isGameOver) return@launch
                if (s.isSetupPhase) {
                    val pid = s.setupPlayer ?: return@launch
                    if (s.players[pid].isHuman) return@launch
                    delay(if (_rapidPlay.value) 50 else 500)
                    val now = _state.value ?: return@launch
                    if (!now.isSetupPhase || now.isGameOver) continue
                    val cur = now.setupPlayer ?: return@launch
                    if (now.players[cur].isHuman) return@launch
                    var st = now
                    repeat(2) {
                        val swap = AIPlayer.decideOneSwap(st.players[cur])
                        if (swap != null) {
                            st = GameEngine.swapSetup(st, swap.first, swap.second)
                        }
                    }
                    st = GameEngine.finishSetup(st)
                    _state.value = st
                    if (_mode.value == GameMode.PASS_AND_PLAY) {
                        val nextActive = st.setupPlayer ?: st.currentPlayer
                        if (nextActive != cur) _revealed.value = false
                    }
                    persist()
                } else {
                    val p = s.players[s.currentPlayer]
                    if (p.isHuman) return@launch
                    delay(if (_rapidPlay.value) 80 else 700)
                    val current = _state.value ?: return@launch
                    if (current.isGameOver || current.isSetupPhase) return@launch
                    val np = current.players[current.currentPlayer]
                    if (np.isHuman) return@launch
                    val move = AIPlayer.decide(current)
                    val updated = when (move) {
                        is AIPlayer.Move.Play -> GameEngine.playCards(current, move.cards)
                        AIPlayer.Move.PickUp -> GameEngine.pickUpPile(current)
                        is AIPlayer.Move.FlipUnder -> GameEngine.flipUnder(current, move.index)
                    }
                    _state.value = updated
                    maybeFinalizeRound(updated)
                    persist()
                }
            }
        }
    }

    fun restoreFrom(snapshot: GameSnapshot) {
        setupOptions = SetupOptions(
            totalPlayers = snapshot.state.players.size,
            localHumans = snapshot.state.players.count { it.isHuman },
            cpuCount = snapshot.state.players.count { !it.isHuman },
            mode = snapshot.mode,
            series = snapshot.seriesConfig,
        )
        _state.value = snapshot.state
        _mode.value = when (snapshot.mode) {
            SessionMode.SOLO -> GameMode.VS_CPU
            SessionMode.PASS_AND_PLAY -> GameMode.PASS_AND_PLAY
            SessionMode.ONLINE_LAN -> GameMode.ONLINE_MULTIPLAYER
        }
        _seriesConfig.value = snapshot.seriesConfig
        _scores.value = snapshot.cumulativeScores
        _roundOrder.value = snapshot.roundOrder
        _lastRoundPoints.value = snapshot.lastRoundPoints
        _matchOver.value = snapshot.matchOver
        roundScored = snapshot.roundScored
        _selected.value = emptySet()
        _selectedOversAddon.value = emptySet()
        _swapHandIndex.value = null
        _swapOversIndex.value = null
        _revealed.value = true
        _rapidPlay.value = UserPreferences.getRapidPlay(getApplication())
        runAi()
    }

    fun clearGame() {
        _state.value = null
        _mode.value = GameMode.VS_CPU
        _selected.value = emptySet()
        _selectedOversAddon.value = emptySet()
        _scores.value = emptyList()
        _roundOrder.value = emptyList()
        _lastRoundPoints.value = emptyList()
        _matchOver.value = false
        _rapidPlay.value = UserPreferences.getRapidPlay(getApplication())
        GameSnapshotStore.clear(getApplication())
    }

    private fun persist() {
        val s = _state.value ?: return
        if (_matchOver.value || s.isGameOver) {
            // Don't persist a finished match — the user should start fresh.
            GameSnapshotStore.clear(getApplication())
            return
        }
        val snap = GameSnapshot(
            state = s,
            mode = when (_mode.value) {
                GameMode.VS_CPU -> SessionMode.SOLO
                GameMode.PASS_AND_PLAY -> SessionMode.PASS_AND_PLAY
                GameMode.ONLINE_MULTIPLAYER -> SessionMode.ONLINE_LAN
            },
            seriesConfig = _seriesConfig.value,
            cumulativeScores = _scores.value,
            lastRoundPoints = _lastRoundPoints.value,
            roundOrder = _roundOrder.value,
            matchOver = _matchOver.value,
            roundScored = roundScored,
        )
        GameSnapshotStore.save(getApplication(), snap)
    }

    private fun buildInitialSeats(options: SetupOptions): List<Seat> {
        val seats = mutableListOf<Seat>()
        when (options.mode) {
            SessionMode.SOLO -> {
                seats.add(Seat("You", isHuman = true))
                for (i in 1 until options.totalPlayers) seats.add(Seat("CPU $i", isHuman = false))
            }
            SessionMode.PASS_AND_PLAY -> {
                for (i in 0 until options.localHumans) {
                    seats.add(Seat("Player ${i + 1}", isHuman = true))
                }
                for (i in 0 until options.cpuCount) {
                    seats.add(Seat("CPU ${i + 1}", isHuman = false))
                }
            }
            SessionMode.ONLINE_LAN -> {
                seats.add(Seat("You", isHuman = true))
                for (i in 1 until options.totalPlayers) seats.add(Seat("CPU $i", isHuman = false))
            }
        }
        return seats
    }

    companion object {
        /** Round score by finish position. Index 0 = first place. */
        fun scoreTableFor(numPlayers: Int): List<Int> = when (numPlayers) {
            2 -> listOf(5, 0)
            3 -> listOf(10, 0, -5)
            4 -> listOf(10, 3, 0, -5)
            else -> List(numPlayers) { 0 }
        }

        /** Backwards-compatible default; series UI lets players pick a target. */
        const val MATCH_WIN_SCORE = 100
    }
}

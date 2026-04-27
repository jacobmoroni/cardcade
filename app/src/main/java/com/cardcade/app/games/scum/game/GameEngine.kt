package com.cardcade.app.games.scum.game

import kotlin.random.Random

/**
 * Pure functions that advance a Scum [GameState]. UI/ViewModel drives the
 * flow by calling these in response to user actions — none of them touch
 * platform APIs or mutate the state in place.
 */
object GameEngine {

    // ---- Round / match lifecycle -------------------------------------------------

    fun startMatch(opts: SetupOptions, random: Random = Random.Default): Pair<GameState, SetupOptions> {
        val players = buildPlayers(opts)
        val deck = buildDeck(opts.extraSuits, opts.jokerCount, random)
        val hands = dealAll(deck, opts.totalPlayers, dealStartIndex = 0)
        val seated = players.mapIndexed { i, p -> p.copy(hand = hands[i]) }
        val leader = seatHoldingTwoOfClubs(seated) ?: 0

        val state = GameState(
            players = seated,
            phase = Phase.PLAYING,
            currentSeat = leader,
            pile = TrickPile(leaderSeat = leader),
            trickLeader = leader,
            finishOrder = emptyList(),
            log = listOf("${seated[leader].name} holds the 2 of clubs and leads."),
            round = 1,
            cumulativeScores = List(opts.totalPlayers) { 0 },
            lastRoundPoints = List(opts.totalPlayers) { 0 },
            pendingTrades = emptyList(),
            completedTrades = emptyList(),
            previousRoles = List(opts.totalPlayers) { Role.COMMONER },
        )
        return state to opts
    }

    private fun buildPlayers(opts: SetupOptions): List<Player> = when (opts.mode) {
        SessionMode.PASS_AND_PLAY -> (0 until opts.totalPlayers).map {
            Player(id = it, name = "Player ${it + 1}", isHuman = true)
        }
        SessionMode.AI_FILL, SessionMode.ONLINE_LAN -> (0 until opts.totalPlayers).mapIndexed { i, _ ->
            Player(
                id = i,
                name = if (i < opts.humanCount) "You" else "CPU ${i - opts.humanCount + 1}",
                isHuman = i < opts.humanCount,
            )
        }
    }

    private fun seatHoldingTwoOfClubs(players: List<Player>): Int? {
        val target = Card(rank = 2, suit = Suit.CLUBS)
        players.forEachIndexed { idx, p ->
            if (p.hand.any { it.rank == target.rank && it.suit == target.suit }) return idx
        }
        return null
    }

    // ---- Valid-play logic --------------------------------------------------------

    /**
     * Every set of cards [hand] can play on top of [pile]. If [pile] is empty
     * (leader's move), any same-rank group is legal. Otherwise the set size
     * must match and the rank must be strictly higher — except when
     * [jokerBeatsAll] is on, in which case any joker-only play of any size
     * is legal as a follow-up.
     */
    fun legalPlays(
        hand: List<Card>,
        pile: TrickPile,
        jokerBeatsAll: Boolean = false,
    ): List<List<Card>> {
        val grouped = hand.groupBy { it.rank }
        val results = mutableListOf<List<Card>>()
        val leading = pile.setSize == 0
        for ((rank, cards) in grouped) {
            val availableCounts = 1..cards.size
            for (count in availableCounts) {
                val sample = cards.take(count)
                val isJokerPlay = rank == Card.JOKER_RANK
                val matchesSize = count == pile.setSize && rank > pile.topRank
                val jokerOverride = jokerBeatsAll && !leading && isJokerPlay
                if (leading || matchesSize || jokerOverride) {
                    results.add(sample)
                }
            }
        }
        return results
    }

    fun hasLegalPlay(hand: List<Card>, pile: TrickPile, jokerBeatsAll: Boolean = false): Boolean =
        legalPlays(hand, pile, jokerBeatsAll).isNotEmpty()

    // ---- Play / pass -------------------------------------------------------------

    fun play(
        state: GameState,
        seat: Int,
        cards: List<Card>,
        jokerBeatsAll: Boolean = false,
    ): GameState {
        require(state.phase == Phase.PLAYING)
        require(seat == state.currentSeat) { "Not this seat's turn" }
        val player = state.players[seat]
        require(!player.isOut && !player.passedThisTrick)
        require(cards.isNotEmpty() && cards.all { it.rank == cards.first().rank })
        val leading = state.pile.setSize == 0
        val isJokerOverride =
            jokerBeatsAll && !leading && cards.all(Card::isJoker)
        if (!leading && !isJokerOverride) {
            require(cards.size == state.pile.setSize) { "Must match set size" }
            require(cards.first().rank > state.pile.topRank) { "Must beat current pile" }
        }
        require(cards.all { c -> player.hand.any { it == c } })

        val newHand = player.hand.toMutableList()
        for (c in cards) newHand.remove(c)
        val wentOut = newHand.isEmpty()
        val updatedPlayer = player.copy(hand = newHand, isOut = wentOut)
        val newPlayers = state.players.toMutableList().also { it[seat] = updatedPlayer }

        val newFinishOrder = if (wentOut) state.finishOrder + seat else state.finishOrder
        val baseLog = state.log + buildPlayLog(player.name, cards, wentOut)
        val newLog = if (isJokerOverride) {
            baseLog + "${player.name}'s joker takes the trick."
        } else baseLog
        val newPile = TrickPile(
            cards = state.pile.cards + cards,
            topRank = cards.first().rank,
            setSize = cards.size,
            leaderSeat = seat,
        )

        val afterPlay = state.copy(
            players = newPlayers,
            pile = newPile,
            trickLeader = if (leading) seat else state.trickLeader,
            finishOrder = newFinishOrder,
            log = newLog,
        )

        if (isJokerOverride) {
            // Nothing beats jokers — short-circuit the rest of the trick.
            val activeSeats = afterPlay.players.withIndex()
                .filter { !it.value.isOut }.map { it.index }
            return if (activeSeats.size <= 1) {
                finalizeRound(appendRemainingToFinishOrder(afterPlay))
            } else {
                endTrick(afterPlay, fallbackLeader = nextActiveSeat(afterPlay, seat))
            }
        }

        return advanceAfterAction(afterPlay, seat)
    }

    fun pass(state: GameState, seat: Int): GameState {
        require(state.phase == Phase.PLAYING)
        require(seat == state.currentSeat) { "Not this seat's turn" }
        val player = state.players[seat]
        require(!player.passedThisTrick && !player.isOut)
        require(state.pile.setSize > 0) { "Cannot pass when leading" }

        val newPlayers = state.players.toMutableList().also {
            it[seat] = player.copy(passedThisTrick = true)
        }
        val afterPass = state.copy(
            players = newPlayers,
            log = state.log + "${player.name} passes.",
        )
        return advanceAfterAction(afterPass, seat)
    }

    // ---- Trick / round progression -----------------------------------------------

    private fun advanceAfterAction(state: GameState, actingSeat: Int): GameState {
        // If everyone is out, round is done.
        val activeSeats = state.players.withIndex()
            .filter { !it.value.isOut }
            .map { it.index }
        if (activeSeats.size <= 1) {
            // Final player gets auto-appended to finish order at round end.
            val scored = appendRemainingToFinishOrder(state)
            return finalizeRound(scored)
        }

        // If only one still-active player hasn't passed this trick, the trick
        // ends and that player leads the next one — per user's variant where
        // the last to play starts next trick. Unless they're the one who just
        // played and still have cards; then give them the choice to continue.
        val stillInTrick = state.players.withIndex()
            .filter { !it.value.isOut && !it.value.passedThisTrick }
            .map { it.index }

        return when {
            stillInTrick.isEmpty() -> {
                // Pile owner was last out — next seat in finish order leads.
                endTrick(state, fallbackLeader = nextActiveSeat(state, state.pile.leaderSeat))
            }
            stillInTrick.size == 1 -> {
                val soloSeat = stillInTrick.single()
                val solo = state.players[soloSeat]
                if (solo.isOut) {
                    endTrick(state, fallbackLeader = nextActiveSeat(state, soloSeat))
                } else {
                    // Give the solo player a free follow-up turn (they may play
                    // another set or pass to close the trick).
                    state.copy(currentSeat = soloSeat)
                }
            }
            else -> state.copy(currentSeat = nextActiveInTrick(state, actingSeat))
        }
    }

    private fun nextActiveInTrick(state: GameState, fromSeat: Int): Int {
        val n = state.players.size
        var idx = (fromSeat + 1) % n
        while (true) {
            val p = state.players[idx]
            if (!p.isOut && !p.passedThisTrick) return idx
            idx = (idx + 1) % n
        }
    }

    private fun nextActiveSeat(state: GameState, fromSeat: Int): Int {
        val n = state.players.size
        var idx = fromSeat
        // Prefer the pile's winner (leaderSeat) if still active.
        if (!state.players[idx].isOut) return idx
        idx = (fromSeat + 1) % n
        while (state.players[idx].isOut) idx = (idx + 1) % n
        return idx
    }

    private fun endTrick(state: GameState, fallbackLeader: Int): GameState {
        // The leader of the pile wins the trick and leads the next one, unless
        // they went out on their last play. In that case the next active seat
        // takes the lead.
        val pileLeader = state.pile.leaderSeat
        val nextLeader = if (state.players[pileLeader].isOut) fallbackLeader else pileLeader

        val clearedPlayers = state.players.map { it.copy(passedThisTrick = false) }
        val msg = "${state.players[nextLeader].name} wins the trick and leads."
        return state.copy(
            players = clearedPlayers,
            pile = TrickPile(leaderSeat = nextLeader),
            trickLeader = nextLeader,
            currentSeat = nextLeader,
            log = state.log + msg,
            playedCards = state.playedCards + state.pile.cards,
        )
    }

    private fun appendRemainingToFinishOrder(state: GameState): GameState {
        val remaining = state.players.withIndex()
            .filter { !it.value.isOut && it.index !in state.finishOrder }
            .map { it.index }
        return state.copy(finishOrder = state.finishOrder + remaining)
    }

    // ---- Round end + scoring -----------------------------------------------------

    fun finalizeRound(state: GameState): GameState {
        // Round-end scoring is applied by the ViewModel with access to
        // SetupOptions; this just transitions the phase.
        return state.copy(phase = Phase.ROUND_END)
    }

    /**
     * Apply scoring for a finished round using [opts]' royalty configuration,
     * produce the set of [TradeSlot]s for the next round, reshuffle, redeal,
     * and rotate to the King's turn. Does not advance past MATCH_END.
     */
    fun startNextRound(
        state: GameState,
        opts: SetupOptions,
        random: Random = Random.Default,
    ): GameState {
        require(state.phase == Phase.ROUND_END)
        val n = opts.totalPlayers
        val finish = state.finishOrder
        require(finish.size == n) { "finishOrder should cover every seat" }

        // Reorder so seat 0 = King (first out), seat N-1 = Scum (last out).
        // Player identity follows the row; roles and scores remap to the new
        // seat layout.
        val reorderedBase = List(n) { i ->
            state.players[finish[i]].copy(
                hand = emptyList(),
                passedThisTrick = false,
                isOut = false,
            )
        }
        val newRoles = List(n) { i -> roleFor(i, n, opts.royaltyTiers) }
        val points = newRoles.map { it.points }
        val oldCumulative = state.cumulativeScores
        val newCumulative = List(n) { i -> oldCumulative[finish[i]] + points[i] }

        val seriesOver = when (opts.seriesConfig.format) {
            SeriesFormat.TARGET_SCORE -> newCumulative.any { it >= opts.seriesConfig.targetScore }
            SeriesFormat.FIXED_ROUNDS -> state.round >= opts.seriesConfig.fixedRounds
        }
        if (seriesOver) {
            return state.copy(
                players = reorderedBase,
                phase = Phase.MATCH_END,
                lastRoundPoints = points,
                cumulativeScores = newCumulative,
                previousRoles = newRoles,
                finishOrder = emptyList(),
            )
        }

        // Deal against the new layout. Scum (seat N-1) is the dealer; first
        // card to self and then around — any imbalance lands on early seats.
        val deck = buildDeck(opts.extraSuits, opts.jokerCount, random)
        val hands = dealAll(deck, n, dealStartIndex = n - 1)
        val dealtPlayers = reorderedBase.mapIndexed { i, p -> p.copy(hand = hands[i]) }

        val trades = buildTradeSlots(n, opts)

        return state.copy(
            players = dealtPlayers,
            phase = if (trades.isEmpty()) Phase.PLAYING else Phase.TRADING,
            currentSeat = 0,
            pile = TrickPile(leaderSeat = 0),
            trickLeader = 0,
            finishOrder = emptyList(),
            log = listOf("Round ${state.round + 1} begins. ${dealtPlayers[0].name} leads as King."),
            round = state.round + 1,
            lastRoundPoints = points,
            cumulativeScores = newCumulative,
            pendingTrades = trades,
            completedTrades = emptyList(),
            previousRoles = newRoles,
            playedCards = emptyList(),
        )
    }

    private fun buildTradeSlots(n: Int, opts: SetupOptions): List<TradeSlot> {
        val pairs = mutableListOf<TradeSlot>()
        // Top tier <-> bottom tier, walking inward.
        for (tier in 0 until opts.royaltyTiers) {
            val royalSeat = tier
            val scumSeat = n - 1 - tier
            val swaps = (opts.topTierSwaps - tier).coerceAtLeast(1)

            pairs.add(
                TradeSlot(
                    fromSeat = scumSeat,
                    toSeat = royalSeat,
                    required = swaps,
                    mustPickLowest = false,
                    jokerLocked = opts.jokersUnswappable,
                ),
            )
            pairs.add(
                TradeSlot(
                    fromSeat = royalSeat,
                    toSeat = scumSeat,
                    required = swaps,
                    mustPickLowest = true,
                    jokerLocked = false,
                ),
            )
        }
        return pairs
    }

    // ---- Trade execution ---------------------------------------------------------

    /** Apply one trade — move [cards] from slot.fromSeat to slot.toSeat. */
    fun applyTrade(
        state: GameState,
        slot: TradeSlot,
        cards: List<Card>,
    ): GameState {
        require(state.phase == Phase.TRADING)
        require(slot in state.pendingTrades)
        require(cards.size == slot.required || (slot.jokerLocked && cards.size < slot.required))

        val from = state.players[slot.fromSeat]
        val to = state.players[slot.toSeat]
        val newFromHand = from.hand.toMutableList()
        for (c in cards) newFromHand.remove(c)
        val newToHand = (to.hand + cards).sortedByDescending(Card::sortKey)

        val newPlayers = state.players.toMutableList()
        newPlayers[slot.fromSeat] = from.copy(hand = newFromHand.sortedByDescending(Card::sortKey))
        newPlayers[slot.toSeat] = to.copy(hand = newToHand)

        val newPending = state.pendingTrades - slot
        val newCompleted = state.completedTrades + CompletedTrade(slot, cards)

        val phase = if (newPending.isEmpty()) Phase.PLAYING else Phase.TRADING
        val leader = state.finishOrder.firstOrNull() ?: state.currentSeat
        return state.copy(
            players = newPlayers,
            phase = phase,
            pendingTrades = newPending,
            completedTrades = newCompleted,
            currentSeat = if (phase == Phase.PLAYING) state.previousKingSeat() else state.currentSeat,
            log = state.log + "${from.name} → ${to.name}: ${cards.size} card(s).",
        )
    }

    /** Mandatory auto-pick for scum-style slots: hand lowest/highest. */
    fun autoPickCards(hand: List<Card>, slot: TradeSlot): List<Card> {
        val pool = if (slot.jokerLocked) hand.filter { !it.isJoker } else hand
        val sorted = if (slot.mustPickLowest) {
            pool.sortedBy(Card::sortKey)
        } else {
            pool.sortedByDescending(Card::sortKey)
        }
        return sorted.take(slot.required)
    }

    private fun GameState.previousKingSeat(): Int {
        val king = previousRoles.indexOfFirst { it == Role.KING }
        return if (king >= 0) king else currentSeat
    }

    // ---- Log helpers -------------------------------------------------------------

    private fun buildPlayLog(name: String, cards: List<Card>, wentOut: Boolean): String {
        val label = rankLabel(cards.first().rank)
        val count = when (cards.size) {
            1 -> "a $label"
            2 -> "a pair of ${label}s"
            3 -> "three ${label}s"
            4 -> "four ${label}s"
            else -> "${cards.size} ${label}s"
        }
        return if (wentOut) "$name played $count — out." else "$name played $count."
    }
}

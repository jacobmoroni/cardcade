package com.cardcade.app.games.luckyunders.game

import kotlin.random.Random

/** Seat definition used by [GameEngine.newGame] to seed per-player metadata. */
data class Seat(val name: String, val isHuman: Boolean)

object GameEngine {

    fun newGame(
        numPlayers: Int,
        humanCount: Int = 1,
        humanNames: List<String> = emptyList(),
        seed: Long? = null,
        startingPlayer: Int = 0,
        seats: List<Seat>? = null,
    ): GameState {
        require(numPlayers in 2..4) { "Supports 2-4 players" }
        require(humanCount in 1..numPlayers) { "humanCount must be 1..numPlayers" }
        require(startingPlayer in 0 until numPlayers) { "startingPlayer out of range" }
        val rng = if (seed != null) Random(seed) else Random.Default
        // Always deal from a full 52-card deck regardless of player count.
        val deck = buildFullDeck().toMutableList().also { it.shuffle(rng) }

        val resolvedSeats: List<Seat> = seats ?: (0 until numPlayers).map { i ->
            val isHuman = i < humanCount
            val name = if (isHuman) {
                humanNames.getOrNull(i) ?: if (humanCount == 1) "You" else "Player ${i + 1}"
            } else {
                "CPU $i"
            }
            Seat(name = name, isHuman = isHuman)
        }
        require(resolvedSeats.size == numPlayers) { "seats.size must match numPlayers" }

        val players = (0 until numPlayers).map { i ->
            val slice = deck.subList(i * 13, (i + 1) * 13).toList()
            // First 4 face down (unders), next 4 face up (overs), last 5 in hand.
            val unders = slice.subList(0, 4)
            val overs = slice.subList(4, 8)
            val hand = slice.subList(8, 13)
            Player(
                id = i,
                name = resolvedSeats[i].name,
                isHuman = resolvedSeats[i].isHuman,
                hand = hand.sortedBy { it.rank },
                overs = overs,
                unders = unders,
            )
        }
        // Remainder (0 cards for 4p, 13 for 3p, 26 for 2p) becomes the draw pile.
        val drawPile = deck.subList(numPlayers * 13, deck.size).toList()

        return GameState(
            players = players,
            currentPlayer = startingPlayer,
            drawPile = drawPile,
            setupPlayer = startingPlayer,
            firstPlayer = startingPlayer,
            log = listOf(
                "Dealt 52 cards (${drawPile.size} in draw pile).",
                "${players[startingPlayer].name}, swap up to 2 hand↔overs cards.",
            ),
        )
    }

    /**
     * A play is legal when all chosen cards share the same rank AND that rank
     * is greater than or equal to the pile's effective top (ignoring wild 2s),
     * OR when the rank is 2 or 10 (always legal), OR when the pile is empty.
     *
     * Additional rule: during the HAND phase a player may include same-rank
     * cards from their overs in a single play, provided that play consumes
     * the player's entire remaining hand (their "last hand card(s)").
     */
    fun isLegalPlay(state: GameState, cards: List<Card>): Boolean {
        if (state.isSetupPhase) return false
        if (cards.isEmpty()) return false
        val rank = cards.first().rank
        if (cards.any { it.rank != rank }) return false

        val p = state.players[state.currentPlayer]
        val (fromHand, fromOvers, fromUnders) = partitionByZone(p, cards) ?: return false
        when (p.activeZone) {
            Zone.HAND -> {
                if (fromUnders.isNotEmpty()) return false
                if (fromOvers.isNotEmpty()) {
                    // Overs add-on is only legal on a play that empties the hand.
                    val remainingHand = p.hand.toMutableList().apply {
                        for (c in fromHand) remove(c)
                    }
                    if (remainingHand.isNotEmpty()) return false
                }
            }
            Zone.OVERS -> {
                if (fromHand.isNotEmpty() || fromUnders.isNotEmpty()) return false
            }
            Zone.UNDERS -> {
                if (fromHand.isNotEmpty() || fromOvers.isNotEmpty()) return false
                if (fromUnders.size != 1) return false
            }
        }

        if (rank == 2 || rank == 10) return true
        val top = state.effectiveTopRank ?: return true
        return rank >= top
    }

    /**
     * Split a list of cards into hand/overs/unders subsets, matching cards the
     * current player actually owns. Returns null if any card is unowned or
     * appears more times than the player holds it.
     */
    private fun partitionByZone(
        p: Player,
        cards: List<Card>,
    ): Triple<List<Card>, List<Card>, List<Card>>? {
        val hand = p.hand.toMutableList()
        val overs = p.overs.toMutableList()
        val unders = p.unders.toMutableList()
        val fh = mutableListOf<Card>()
        val fo = mutableListOf<Card>()
        val fu = mutableListOf<Card>()
        for (c in cards) {
            when {
                hand.remove(c) -> fh.add(c)
                overs.remove(c) -> fo.add(c)
                unders.remove(c) -> fu.add(c)
                else -> return null
            }
        }
        return Triple(fh, fo, fu)
    }

    /** Returns all legal plays the current player can make from their active zone. */
    fun legalPlays(state: GameState): List<List<Card>> {
        if (state.isSetupPhase) return emptyList()
        val p = state.players[state.currentPlayer]
        if (p.activeZone == Zone.UNDERS) {
            // Unders are blind — choose by index, not rank; we expose them as
            // single-card plays keyed by position.
            return p.unders.map { listOf(it) }
        }
        val source = p.activeCards
        val byRank = source.groupBy { it.rank }
        val plays = mutableListOf<List<Card>>()
        for ((_, cards) in byRank) {
            if (isLegalPlay(state, listOf(cards.first()))) {
                // Any non-empty subset of same rank cards is legal.
                for (k in 1..cards.size) plays.add(cards.take(k))
            }
        }
        return plays
    }

    /**
     * Pre-game: swap one hand card with one overs card for the current setup
     * player. Allowed up to twice per player.
     */
    fun swapSetup(state: GameState, handIndex: Int, oversIndex: Int): GameState {
        val pid = requireNotNull(state.setupPlayer) { "Not in setup phase" }
        require(state.setupSwapsDone < 2) { "Already swapped 2 cards" }
        val p = state.players[pid]
        require(handIndex in p.hand.indices)
        require(oversIndex in p.overs.indices)
        val handCard = p.hand[handIndex]
        val overCard = p.overs[oversIndex]
        val newOvers = p.overs.toMutableList().also { it[oversIndex] = handCard }
        val newHand = p.hand.toMutableList().also { it[handIndex] = overCard }
            .sortedBy { it.rank }
        val updated = p.copy(hand = newHand, overs = newOvers)
        val players = state.players.toMutableList().also { it[pid] = updated }
        return state.copy(
            players = players,
            setupSwapsDone = state.setupSwapsDone + 1,
            log = state.log + "${p.name} swapped $handCard ↔ $overCard",
        )
    }

    /**
     * Pre-game: end the current setup player's turn (whether or not they
     * swapped). Advances to the next player; once all players have gone, the
     * play phase begins with player 0.
     */
    fun finishSetup(state: GameState): GameState {
        val pid = requireNotNull(state.setupPlayer) { "Not in setup phase" }
        val n = state.players.size
        val nextPid = (pid + 1) % n
        // Setup ends once we'd cycle back to the player who started this round.
        return if (nextPid == state.firstPlayer) {
            state.copy(
                setupPlayer = null,
                setupSwapsDone = 0,
                currentPlayer = state.firstPlayer,
                log = state.log + "Setup complete. ${state.players[state.firstPlayer].name} starts.",
            )
        } else {
            state.copy(
                setupPlayer = nextPid,
                setupSwapsDone = 0,
                log = state.log + "${state.players[nextPid].name}, swap up to 2 hand↔overs cards.",
            )
        }
    }

    /** Plays [cards] for the current player and resolves pile effects. */
    fun playCards(state: GameState, cards: List<Card>): GameState {
        require(isLegalPlay(state, cards)) { "Illegal play: $cards" }
        val p = state.players[state.currentPlayer]
        val (fromHand, fromOvers, fromUnders) =
            requireNotNull(partitionByZone(p, cards)) { "Partition failed for $cards" }

        val newHand = p.hand.toMutableList().apply { for (c in fromHand) remove(c) }
        val newOvers = p.overs.toMutableList().apply { for (c in fromOvers) remove(c) }
        val newUnders = p.unders.toMutableList().apply { for (c in fromUnders) remove(c) }

        var updatedPlayer = p.copy(hand = newHand, overs = newOvers, unders = newUnders)

        // Refill hand from draw pile up to 5, until the draw pile is gone.
        var newDrawPile = state.drawPile
        if (newDrawPile.isNotEmpty() && updatedPlayer.hand.size < 5) {
            val needed = 5 - updatedPlayer.hand.size
            val taken = newDrawPile.take(needed)
            newDrawPile = newDrawPile.drop(needed)
            val refilledHand = (updatedPlayer.hand + taken).sortedBy { it.rank }
            updatedPlayer = updatedPlayer.copy(hand = refilledHand)
        }

        val players = state.players.toMutableList().also { it[p.id] = updatedPlayer }

        val rank = cards.first().rank
        val isBurnByTen = rank == 10
        val isWild = rank == 2

        val pileAfterPlay = if (isBurnByTen) emptyList() else state.pile + cards
        // Four of the same rank in a row on top of the pile burns it too.
        val isBurnByFour = !isBurnByTen &&
            pileAfterPlay.size >= 4 &&
            pileAfterPlay.takeLast(4).all { it.rank == rank }
        val didBurn = isBurnByTen || isBurnByFour
        val newPile = if (isBurnByFour) emptyList() else pileAfterPlay

        val logEntry = buildString {
            append(p.name)
            append(" played ")
            append(cards.joinToString(" ") { it.toString() })
            when {
                isBurnByTen -> append(" — 10 burned the pile, next player")
                isBurnByFour -> append(" — four ${cards.first().rankLabel}s burned the pile, next player")
                isWild -> append(" — wild 2, played again")
            }
        }

        val winners = state.winnerOrder.toMutableList()
        if (updatedPlayer.isFinished && p.id !in winners) winners.add(p.id)

        // A 2 (wild) normally lets the same player go again. A burn (10 or
        // four-of-a-kind) passes play to the next player even if a 2 was
        // involved (e.g. four 2s). Finishing your last cards also advances.
        val keepTurn = isWild && !didBurn && !updatedPlayer.isFinished
        val nextPlayer = if (keepTurn) p.id else nextActive(players, p.id, winners)

        return state.copy(
            players = players,
            currentPlayer = nextPlayer,
            pile = newPile,
            drawPile = newDrawPile,
            winnerOrder = winners,
            log = state.log + logEntry,
            pendingFlippedUnder = null,
        )
    }

    /**
     * Called when the current player must attempt a blind under. The top card
     * from [index] is flipped; if it's playable it's auto-played, otherwise
     * the player picks up the pile plus the flipped card.
     */
    fun flipUnder(state: GameState, index: Int): GameState {
        val p = state.players[state.currentPlayer]
        require(p.activeZone == Zone.UNDERS) { "Not in unders phase" }
        require(index in p.unders.indices)
        val flipped = p.unders[index]
        // Try to play it
        return if (isLegalPlay(state, listOf(flipped))) {
            playCards(state, listOf(flipped))
        } else {
            pickUpPileForFailedFlip(state, p.id, flipped, index)
        }
    }

    private fun pickUpPileForFailedFlip(
        state: GameState, playerId: Int, flipped: Card, underIndex: Int,
    ): GameState {
        val p = state.players[playerId]
        val newUnders = p.unders.toMutableList().also { it.removeAt(underIndex) }
        val picked = state.pile + flipped
        val newHand = (p.hand + picked).sortedBy { it.rank }
        val updated = p.copy(hand = newHand, unders = newUnders)
        val players = state.players.toMutableList().also { it[playerId] = updated }
        val next = nextActive(players, playerId, state.winnerOrder)
        return state.copy(
            players = players,
            currentPlayer = next,
            pile = emptyList(),
            log = state.log + "${p.name} flipped $flipped and couldn't play — picked up the pile.",
            pendingFlippedUnder = null,
        )
    }

    /** Current player can't play; they pick up the pile and pass. */
    fun pickUpPile(state: GameState): GameState {
        val p = state.players[state.currentPlayer]
        require(state.pile.isNotEmpty()) { "Pile is empty" }
        val newHand = (p.hand + state.pile).sortedBy { it.rank }
        val updated = p.copy(hand = newHand)
        val players = state.players.toMutableList().also { it[p.id] = updated }
        val next = nextActive(players, p.id, state.winnerOrder)
        return state.copy(
            players = players,
            currentPlayer = next,
            pile = emptyList(),
            log = state.log + "${p.name} picked up the pile.",
        )
    }

    private fun nextActive(players: List<Player>, from: Int, winners: List<Int>): Int {
        val n = players.size
        var idx = (from + 1) % n
        var safety = 0
        while (players[idx].isFinished && safety < n * 2) {
            idx = (idx + 1) % n
            safety++
        }
        return idx
    }
}

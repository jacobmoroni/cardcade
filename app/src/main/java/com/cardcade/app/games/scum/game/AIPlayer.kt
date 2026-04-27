package com.cardcade.app.games.scum.game

import kotlin.random.Random

/**
 * Decision made by the AI for one turn. UI applies it by calling
 * [GameEngine.play] or [GameEngine.pass].
 */
sealed interface AIDecision {
    data class Play(val cards: List<Card>) : AIDecision
    data object Pass : AIDecision
}

object AIPlayer {
    /**
     * Easy: always play the cheapest legal beat; lead with lowest full group.
     *
     * Medium: same as Easy, but when the cheapest legal beat requires a high
     * card (rank ≥ Jack) and multiple opponents are still active, there is a
     * calibrated chance to pass and conserve that card for a better moment.
     *
     * Hard: counts cards that have been publicly played (from [GameState.playedCards]
     * and the current pile) to find plays no remaining unknown cards can beat.
     * Passes strategically when a high card would likely be beaten. Does not
     * look at opponent hands — only uses visible information.
     */
    fun decide(
        state: GameState,
        jokerBeatsAll: Boolean = false,
        difficulty: AIDifficulty = AIDifficulty.EASY,
        opts: SetupOptions? = null,
        random: Random = Random.Default,
    ): AIDecision {
        val seat = state.currentSeat
        val hand = state.players[seat].hand
        val leading = state.pile.setSize == 0

        if (leading) {
            val byRank = hand.groupBy { it.rank }
            val nonJokerGroups = byRank.filterKeys { it != Card.JOKER_RANK }
            val chosenMap = if (nonJokerGroups.isNotEmpty()) nonJokerGroups else byRank

            if (difficulty == AIDifficulty.HARD && opts != null) {
                return decideLeadHard(chosenMap, seat, state, opts, jokerBeatsAll)
            }
            val lowestRank = chosenMap.keys.min()
            return AIDecision.Play(chosenMap.getValue(lowestRank))
        }

        val followPlays = GameEngine.legalPlays(hand, state.pile, jokerBeatsAll)
        if (followPlays.isEmpty()) return AIDecision.Pass

        val cheapest = followPlays.minWith(
            compareBy<List<Card>> { it.first().rank }.thenBy { it.size },
        )
        // Hold a sole joker for late-game leverage unless nearly out.
        if (cheapest.any(Card::isJoker) && hand.size > 4) {
            val nonJoker = followPlays.filter { it.none(Card::isJoker) }
            if (nonJoker.isEmpty()) return AIDecision.Pass
            return AIDecision.Play(nonJoker.minBy { it.first().rank })
        }

        if (difficulty == AIDifficulty.HARD && opts != null) {
            return decideFollowHard(hand, seat, state, opts, jokerBeatsAll, followPlays)
        }

        if (difficulty == AIDifficulty.MEDIUM) {
            val beatRank = cheapest.first().rank
            if (hand.size > 3 && beatRank >= 11) {
                val activeOpponents = state.players.indices.count { i ->
                    i != seat && i !in state.finishOrder && state.players[i].hand.isNotEmpty()
                }
                val passProbability = when {
                    activeOpponents >= 3 && beatRank >= 13 -> 0.70f
                    activeOpponents >= 3 && beatRank >= 11 -> 0.50f
                    activeOpponents >= 2 && beatRank >= 13 -> 0.60f
                    activeOpponents >= 2 && beatRank >= 11 -> 0.35f
                    else -> 0f
                }
                if (passProbability > 0f && random.nextFloat() < passProbability) {
                    return AIDecision.Pass
                }
            }
        }

        return AIDecision.Play(cheapest)
    }

    // ── Hard difficulty helpers ───────────────────────────────────────────────

    private fun decideLeadHard(
        chosenMap: Map<Int, List<Card>>,
        seat: Int,
        state: GameState,
        opts: SetupOptions,
        jokerBeatsAll: Boolean,
    ): AIDecision {
        // Prefer unbeatable plays: not enough unseen cards of higher rank to form a beat.
        val unbeatable = chosenMap.flatMap { (_, cards) ->
            (1..cards.size).mapNotNull { count ->
                val play = cards.take(count)
                if (unseenBeaterCount(play, seat, state, opts, jokerBeatsAll) < play.size) play else null
            }
        }
        if (unbeatable.isNotEmpty()) {
            return AIDecision.Play(
                unbeatable.minWith(compareBy<List<Card>> { it.first().rank }.thenBy { it.size }),
            )
        }
        val lowestRank = chosenMap.keys.min()
        return AIDecision.Play(chosenMap.getValue(lowestRank))
    }

    private fun decideFollowHard(
        hand: List<Card>,
        seat: Int,
        state: GameState,
        opts: SetupOptions,
        jokerBeatsAll: Boolean,
        followPlays: List<List<Card>>,
    ): AIDecision {
        // Play the cheapest option that no remaining unseen card can beat.
        val guaranteed = followPlays.filter { play ->
            unseenBeaterCount(play, seat, state, opts, jokerBeatsAll) < play.size
        }
        if (guaranteed.isNotEmpty()) {
            return AIDecision.Play(
                guaranteed.minWith(compareBy<List<Card>> { it.first().rank }.thenBy { it.size }),
            )
        }

        // No guaranteed win. Pass strategically on high cards when beaters likely exist.
        val cheapest = followPlays.minWith(
            compareBy<List<Card>> { it.first().rank }.thenBy { it.size },
        )
        val beatRank = cheapest.first().rank
        val unseen = unseenBeaterCount(cheapest, seat, state, opts, jokerBeatsAll)
        if (hand.size > 2) {
            if (beatRank >= 13 && unseen >= cheapest.size) return AIDecision.Pass
            if (beatRank >= 11 && unseen >= cheapest.size * 2) return AIDecision.Pass
        }
        return AIDecision.Play(cheapest)
    }

    /**
     * Count unseen cards of rank higher than [play] that could form a legal follow.
     * "Unseen" = not in the AI's own hand, not in the current trick pile, and not
     * in [GameState.playedCards] (prior completed tricks). This is the card-counting
     * estimate of how many potential beaters remain distributed among opponents.
     *
     * A result less than [play].size means the full deck has too few remaining
     * high cards for anyone to assemble a complete same-size beat — a guaranteed win.
     */
    private fun unseenBeaterCount(
        play: List<Card>,
        seat: Int,
        state: GameState,
        opts: SetupOptions,
        jokerBeatsAll: Boolean,
    ): Int {
        val playRank = play.first().rank
        val setSize = play.size

        // All cards whose rank and identity are known to us.
        val seenCards = state.players[seat].hand + state.pile.cards + state.playedCards
        val seenByRank = seenCards.groupingBy { it.rank }.eachCount()

        // Copies of each rank in the deck (4 standard suits + any extra).
        val copiesPerRank = 4 + opts.extraSuits

        var total = 0
        // Regular ranks strictly above the play rank.
        for (rank in (playRank + 1)..Card.MAX_RANK) {
            val seen = seenByRank.getOrDefault(rank, 0)
            total += maxOf(0, copiesPerRank - seen)
        }
        // Jokers beat everything when jokerBeatsAll is on.
        if (jokerBeatsAll) {
            val seenJokers = seenByRank.getOrDefault(Card.JOKER_RANK, 0)
            total += maxOf(0, opts.jokerCount - seenJokers)
        }
        return total
    }
}

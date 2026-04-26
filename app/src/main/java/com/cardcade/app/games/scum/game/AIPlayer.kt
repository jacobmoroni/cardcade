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
     * The probability is randomised so the AI is imperfect, not mechanical.
     */
    fun decide(
        state: GameState,
        jokerBeatsAll: Boolean = false,
        difficulty: AIDifficulty = AIDifficulty.EASY,
        random: Random = Random.Default,
    ): AIDecision {
        val seat = state.currentSeat
        val hand = state.players[seat].hand
        val leading = state.pile.setSize == 0

        if (leading) {
            val byRank = hand.groupBy { it.rank }
            val nonJokerGroups = byRank.filterKeys { it != Card.JOKER_RANK }
            val chosenMap = if (nonJokerGroups.isNotEmpty()) nonJokerGroups else byRank
            val lowestRank = chosenMap.keys.min()
            val group = chosenMap.getValue(lowestRank)
            return AIDecision.Play(group)
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

        if (difficulty == AIDifficulty.MEDIUM) {
            val beatRank = cheapest.first().rank
            // Only evaluate conserving when the cheapest beat is already a face card.
            if (hand.size > 3 && beatRank >= 11) {
                val activeOpponents = state.players.indices.count { i ->
                    i != seat && i !in state.finishOrder && state.players[i].hand.isNotEmpty()
                }
                // More opponents + higher rank = stronger incentive to pass.
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
}

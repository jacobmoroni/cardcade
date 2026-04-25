package com.cardcade.app.games.scum.game

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
     * First-cut heuristics:
     * - Leading: smallest set size first (keep pairs/triples together), then
     *   lowest rank within that size. Avoids jokers unless they're all we have.
     * - Following: cheapest legal beat — same set size, lowest rank that
     *   exceeds the pile. Pass if nothing cheap enough (i.e. only jokers left
     *   and the pile is low, conservatively hold them).
     */
    fun decide(state: GameState, jokerBeatsAll: Boolean = false): AIDecision {
        val seat = state.currentSeat
        val hand = state.players[seat].hand
        val leading = state.pile.setSize == 0

        if (leading) {
            // Standard Scum advice is "rarely break up sets." When leading,
            // play every card of the lowest non-joker rank together — a
            // natural pair/triple/quad dump rather than a lone single.
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
        // Hold a sole joker for late-game leverage unless we're nearly out.
        if (cheapest.any(Card::isJoker) && hand.size > 4) {
            val nonJoker = followPlays.filter { it.none(Card::isJoker) }
            if (nonJoker.isEmpty()) return AIDecision.Pass
            return AIDecision.Play(nonJoker.minBy { it.first().rank })
        }
        return AIDecision.Play(cheapest)
    }
}

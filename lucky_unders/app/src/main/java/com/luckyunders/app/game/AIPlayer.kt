package com.luckyunders.app.game

/**
 * Very simple strategy:
 *  - If in UNDERS, flip a random under (no choice).
 *  - Prefer the lowest legal rank that is NOT a 10 (save burns).
 *  - Prefer NOT to play a 2 unless it's the only legal move.
 *  - Play all of the chosen rank at once to empty the hand faster.
 *  - If no legal play, pick up the pile.
 */
object AIPlayer {
    sealed interface Move {
        data class Play(val cards: List<Card>) : Move
        data object PickUp : Move
        data class FlipUnder(val index: Int) : Move
    }

    fun decide(state: GameState): Move {
        val p = state.players[state.currentPlayer]
        // Defensive: never invoked on a finished player after the engine fix,
        // but if it ever is we'd rather pick up than crash.
        if (p.isFinished) return Move.PickUp
        if (p.activeZone == Zone.UNDERS) {
            val idx = p.unders.indices.random()
            return Move.FlipUnder(idx)
        }

        val source = p.activeCards
        val byRank = source.groupBy { it.rank }
        val legalRanks = byRank.keys.filter { r ->
            GameEngine.isLegalPlay(state, listOf(Card(r, source.first { it.rank == r }.suit)))
        }.sorted()

        if (legalRanks.isEmpty()) return Move.PickUp

        // Prefer non-special lowest rank.
        val preferred = legalRanks.firstOrNull { it != 2 && it != 10 }
            ?: legalRanks.firstOrNull { it != 2 }
            ?: legalRanks.first()

        val group = byRank.getValue(preferred)
        return Move.Play(group)
    }

    /**
     * One-shot pre-game swap decision. Treats 2s and 10s as gold in hand
     * (we never want to lose them) and as ordinary cards if already in overs.
     * Returns null when no swap improves the board.
     */
    fun decideOneSwap(p: Player): Pair<Int, Int>? {
        fun handValue(c: Card): Int = if (c.rank == 2 || c.rank == 10) 100 else c.rank
        fun overValue(c: Card): Int = if (c.rank == 2 || c.rank == 10) 0 else c.rank
        val worstHandIdx = p.hand.indices.minByOrNull { handValue(p.hand[it]) } ?: return null
        val bestOverIdx = p.overs.indices.maxByOrNull { overValue(p.overs[it]) } ?: return null
        return if (overValue(p.overs[bestOverIdx]) > handValue(p.hand[worstHandIdx])) {
            worstHandIdx to bestOverIdx
        } else null
    }
}

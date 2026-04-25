package com.cardcade.app.games.scum.game

enum class Suit { CLUBS, DIAMONDS, HEARTS, SPADES }

/**
 * A single Scum card. [rank] is 2..14 for playing cards (14 = Ace) and
 * [JOKER_RANK] for jokers. Suit is null for jokers. [copy] distinguishes
 * duplicates when the deck contains extra suits or is doubled.
 */
data class Card(
    val rank: Int,
    val suit: Suit?,
    val copy: Int = 0,
) {
    val isJoker: Boolean get() = rank == JOKER_RANK

    /** Compares strictly by rank — the only thing that matters in Scum. */
    val sortKey: Int get() = rank

    companion object {
        const val JOKER_RANK = 100
        const val MIN_RANK = 2
        const val MAX_RANK = 14

        fun isRoyalRank(rank: Int): Boolean = rank in 11..14 || rank == JOKER_RANK
    }
}

fun rankLabel(rank: Int): String = when (rank) {
    Card.JOKER_RANK -> "Jkr"
    14 -> "A"
    13 -> "K"
    12 -> "Q"
    11 -> "J"
    else -> rank.toString()
}

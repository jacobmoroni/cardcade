package com.luckyunders.app.game

enum class Suit(val symbol: String, val isRed: Boolean) {
    SPADES("\u2660", false),
    HEARTS("\u2665", true),
    DIAMONDS("\u2666", true),
    CLUBS("\u2663", false),
}

/**
 * A standard playing card. [rank] is 2..14 where 11=J, 12=Q, 13=K, 14=A.
 * Aces are HIGH — higher than a King. In Lucky Unders, 2 is a wild restart
 * and 10 burns the pile, both of which can be played on any card including
 * an Ace.
 */
data class Card(val rank: Int, val suit: Suit) {
    init {
        require(rank in 2..14) { "Rank must be 2..14, got $rank" }
    }

    val isWildTwo: Boolean get() = rank == 2
    val isBurnTen: Boolean get() = rank == 10

    val rankLabel: String get() = when (rank) {
        11 -> "J"
        12 -> "Q"
        13 -> "K"
        14 -> "A"
        else -> rank.toString()
    }

    override fun toString(): String = "$rankLabel${suit.symbol}"
}

fun buildDeck(numSuits: Int): List<Card> {
    require(numSuits in 1..4)
    val suits = Suit.values().take(numSuits)
    return suits.flatMap { s -> (2..14).map { r -> Card(r, s) } }
}

/** A standard 52-card deck (all four suits). */
fun buildFullDeck(): List<Card> = buildDeck(4)

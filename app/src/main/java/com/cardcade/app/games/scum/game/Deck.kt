package com.cardcade.app.games.scum.game

import kotlin.random.Random

/**
 * Builds a shuffled Scum deck. Base is a standard 52-card deck; [extraSuits]
 * adds one additional full suit (13 cards) each, up to 2 decks' worth (4
 * extra suits). [jokerCount] appends 1 or 2 jokers after.
 *
 * Extra suits cycle in order (Clubs → Diamonds → Hearts → Spades) so adding
 * one full deck's worth lands on exactly two of everything.
 */
fun buildDeck(
    extraSuits: Int,
    jokerCount: Int,
    random: Random,
): List<Card> {
    require(extraSuits in 0..4)
    require(jokerCount in 1..2)

    val suitCycle = listOf(Suit.CLUBS, Suit.DIAMONDS, Suit.HEARTS, Suit.SPADES)
    val cards = mutableListOf<Card>()

    // Base single deck: copy 0 of every suit.
    for (suit in suitCycle) {
        for (rank in Card.MIN_RANK..Card.MAX_RANK) {
            cards.add(Card(rank = rank, suit = suit, copy = 0))
        }
    }
    // Each extra suit adds copy=1 of that suit.
    for (i in 0 until extraSuits) {
        val suit = suitCycle[i]
        for (rank in Card.MIN_RANK..Card.MAX_RANK) {
            cards.add(Card(rank = rank, suit = suit, copy = 1))
        }
    }
    // Jokers.
    for (j in 0 until jokerCount) {
        cards.add(Card(rank = Card.JOKER_RANK, suit = null, copy = j))
    }

    return cards.shuffled(random)
}

/**
 * Deals the full deck out one-at-a-time starting from [dealStartIndex] and
 * moving left (index + 1 mod players). Remaining cards go to the first few
 * seats dealt — historically the player to the dealer's left gets the extra.
 */
fun dealAll(
    deck: List<Card>,
    players: Int,
    dealStartIndex: Int,
): List<List<Card>> {
    val hands = List(players) { mutableListOf<Card>() }
    var seat = dealStartIndex
    for (card in deck) {
        hands[seat].add(card)
        seat = (seat + 1) % players
    }
    return hands.map { it.sortedByDescending(Card::sortKey) }
}

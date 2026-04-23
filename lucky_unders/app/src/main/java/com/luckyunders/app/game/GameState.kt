package com.luckyunders.app.game

enum class Zone { HAND, OVERS, UNDERS }

data class Player(
    val id: Int,
    val name: String,
    val isHuman: Boolean,
    val hand: List<Card> = emptyList(),
    val overs: List<Card> = emptyList(),
    val unders: List<Card> = emptyList(),
) {
    val isFinished: Boolean
        get() = hand.isEmpty() && overs.isEmpty() && unders.isEmpty()

    /** The zone the player must currently play from. */
    val activeZone: Zone
        get() = when {
            hand.isNotEmpty() -> Zone.HAND
            overs.isNotEmpty() -> Zone.OVERS
            else -> Zone.UNDERS
        }

    val activeCards: List<Card>
        get() = when (activeZone) {
            Zone.HAND -> hand
            Zone.OVERS -> overs
            Zone.UNDERS -> unders
        }
}

/**
 * Immutable snapshot of the whole game.
 *
 * [pile] is stacked so the LAST element is the top (most recently played).
 * [drawPile] is the face-down deck players draw from to refill their hand
 * to five after each play, until it's empty.
 * [setupPlayer] is non-null when in the pre-game swap phase, indicating
 * which player is currently choosing their hand↔overs swaps.
 */
data class GameState(
    val players: List<Player>,
    val currentPlayer: Int,
    val pile: List<Card> = emptyList(),
    val drawPile: List<Card> = emptyList(),
    val winnerOrder: List<Int> = emptyList(),
    val log: List<String> = emptyList(),
    val pendingFlippedUnder: Card? = null,
    val setupPlayer: Int? = null,
    val setupSwapsDone: Int = 0,
    /** The player id who began this round; setup wraps back here to end. */
    val firstPlayer: Int = 0,
) {
    val isSetupPhase: Boolean get() = setupPlayer != null

    val isGameOver: Boolean
        get() = !isSetupPhase && players.count { !it.isFinished } <= 1

    /**
     * The effective rank a new play must match or beat. When the top card is
     * a 2, the pile is considered restarted and any card is legal — so we
     * return null. Otherwise it's just the top card's rank.
     */
    val effectiveTopRank: Int?
        get() {
            val top = pile.lastOrNull() ?: return null
            return if (top.isWildTwo) null else top.rank
        }
}

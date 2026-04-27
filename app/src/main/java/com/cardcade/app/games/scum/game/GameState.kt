package com.cardcade.app.games.scum.game

/** High-level phase of the round the engine is currently in. */
enum class Phase {
    /** Waiting for royalty/scum to complete the card swap at round start. */
    TRADING,
    /** Normal play — tricks being played until everyone is out. */
    PLAYING,
    /** Round finished, scores applied, showing the scoreboard. */
    ROUND_END,
    /** Series target hit; match complete. */
    MATCH_END,
}

/**
 * A trade proposal offered to a human royal/scum. For non-swapping positions
 * [required] == 0. [mustPickLowest] applies to royalty — they must give their
 * lowest cards. [jokerLocked] applies when jokers are unswappable (a scum may
 * keep jokers rather than sending them up).
 */
data class TradeSlot(
    val fromSeat: Int,
    val toSeat: Int,
    val required: Int,
    val mustPickLowest: Boolean,
    val jokerLocked: Boolean,
)

/** A trade that has already happened, with the cards that moved. */
data class CompletedTrade(
    val slot: TradeSlot,
    val cards: List<Card>,
)

/**
 * The cards currently on the trick pile plus the size of the set — every
 * follow-up play has to match [setSize] and exceed [topRank].
 */
data class TrickPile(
    val cards: List<Card> = emptyList(),
    val topRank: Int = 0,
    val setSize: Int = 0,
    val leaderSeat: Int = 0,
)

data class GameState(
    val players: List<Player>,
    val phase: Phase,
    val currentSeat: Int,
    val pile: TrickPile,
    val trickLeader: Int,
    /** Finish order, earliest out first — role is derived from position here. */
    val finishOrder: List<Int>,
    val log: List<String>,
    val round: Int,
    val cumulativeScores: List<Int>,
    val lastRoundPoints: List<Int>,
    /** Pending trade slots at the start of every round after round 1. */
    val pendingTrades: List<TradeSlot>,
    val completedTrades: List<CompletedTrade>,
    /** Saved roles from the previous round so the board can label seats. */
    val previousRoles: List<Role>,
    /** All cards from completed tricks this round — visible public information for card counting. */
    val playedCards: List<Card> = emptyList(),
)

package com.cardcade.app.games.scum.game

/** How a single device hosts the players. */
enum class SessionMode {
    PASS_AND_PLAY,
    AI_FILL,
    /** Multiple devices on the same LAN; may be backfilled with CPUs. */
    ONLINE_LAN,
}

enum class AIDifficulty { EASY, MEDIUM, HARD }

enum class SeriesFormat { TARGET_SCORE, FIXED_ROUNDS }

data class SeriesConfig(
    val format: SeriesFormat = SeriesFormat.TARGET_SCORE,
    val targetScore: Int = 100,
    val fixedRounds: Int = 5,
)

/**
 * Everything the user picks on the Start Game screen. All fields are
 * validated inside [GameEngine.startGame]; construct through [SetupOptions]
 * so defaults stay in one place.
 */
data class SetupOptions(
    val totalPlayers: Int = 4,
    val mode: SessionMode = SessionMode.AI_FILL,
    val humanCount: Int = 1,
    val royaltyTiers: Int = 2,
    val topTierSwaps: Int = 2,
    val jokerCount: Int = 1,
    val jokersUnswappable: Boolean = false,
    /** When true, any number of jokers beats any pile, ending the trick. */
    val jokerBeatsAll: Boolean = false,
    val extraSuits: Int = 0,
    val seriesConfig: SeriesConfig = SeriesConfig(),
    val aiDifficulty: AIDifficulty = AIDifficulty.EASY,
) {
    val totalCards: Int get() = 52 + extraSuits * 13 + jokerCount

    init {
        require(totalPlayers in 4..8) { "Scum expects 4..8 players" }
        require(royaltyTiers in 1..3)
        require(topTierSwaps in 1..3)
        require(jokerCount in 1..2)
        require(extraSuits in 0..4)
        require(humanCount in 1..totalPlayers)
        require(mode == SessionMode.PASS_AND_PLAY || humanCount >= 1)
    }
}

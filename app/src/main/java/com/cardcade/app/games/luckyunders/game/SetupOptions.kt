package com.cardcade.app.games.luckyunders.game

enum class PlayerOrderRule {
    MAINTAIN,
    RANDOM,
    WINNING_ORDER,
}

enum class SeriesFormat { SINGLE, SERIES }

data class SeriesConfig(
    val format: SeriesFormat = SeriesFormat.SERIES,
    val targetScore: Int = 100,
    val orderRule: PlayerOrderRule = PlayerOrderRule.MAINTAIN,
)

/**
 * Full configuration used to start a new game. The ViewModel consumes this to
 * deal the first round and to re-deal subsequent rounds in a series.
 */
data class SetupOptions(
    val totalPlayers: Int,
    val localHumans: Int,
    val onlineHumans: Int = 0,
    val cpuCount: Int,
    val series: SeriesConfig = SeriesConfig(),
    val mode: SessionMode,
) {
    init {
        require(totalPlayers in 2..4) { "2-4 players only" }
        require(localHumans + onlineHumans + cpuCount == totalPlayers) { "player counts must sum to totalPlayers" }
    }

    val humanCount: Int get() = localHumans + onlineHumans
}

enum class SessionMode {
    /** One local human; the rest are CPU opponents. */
    SOLO,

    /** All humans share one device, passing between turns. */
    PASS_AND_PLAY,

    /** Multiple devices on the same LAN, may be backfilled with CPUs. */
    ONLINE_LAN,
}

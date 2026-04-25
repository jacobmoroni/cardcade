package com.cardcade.app.games.scum.game

/**
 * Role awarded to a player at the end of each round, based on the order in
 * which they went out. Roles are only present when the configured royalty
 * count supports them — e.g. PRINCE / PEASANT only when 3 royalty tiers.
 */
enum class Role(val display: String, val points: Int) {
    KING("King", 10),
    QUEEN("Queen", 5),
    PRINCE("Prince", 3),
    COMMONER("Commoner", 0),
    PEASANT("Peasant", -1),
    VICE_SCUM("Vice-Scum", -3),
    SCUM("Scum", -5),
}

/**
 * Maps a [finishPosition] (0 = first out, players-1 = last out) to a Role
 * given the number of players and configured royalty tiers (1..3).
 */
fun roleFor(
    finishPosition: Int,
    playerCount: Int,
    royaltyTiers: Int,
): Role {
    require(royaltyTiers in 1..3)
    require(finishPosition in 0 until playerCount)

    val topTiers = listOf(Role.KING, Role.QUEEN, Role.PRINCE).take(royaltyTiers)
    val bottomTiers = listOf(Role.SCUM, Role.VICE_SCUM, Role.PEASANT).take(royaltyTiers)

    return when {
        finishPosition < topTiers.size -> topTiers[finishPosition]
        finishPosition >= playerCount - bottomTiers.size -> {
            val fromBottom = playerCount - 1 - finishPosition
            bottomTiers[fromBottom]
        }
        else -> Role.COMMONER
    }
}

/** Number of cards swapped by the role at [finishPosition]. */
fun swapCountFor(
    finishPosition: Int,
    playerCount: Int,
    royaltyTiers: Int,
    topTierSwaps: Int,
): Int {
    val role = roleFor(finishPosition, playerCount, royaltyTiers)
    return when (role) {
        Role.KING, Role.SCUM -> topTierSwaps.coerceAtLeast(1)
        Role.QUEEN, Role.VICE_SCUM -> (topTierSwaps - 1).coerceAtLeast(1)
        Role.PRINCE, Role.PEASANT -> (topTierSwaps - 2).coerceAtLeast(1)
        Role.COMMONER -> 0
    }
}

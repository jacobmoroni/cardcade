package com.cardcade.app.games.scum.persistence

import com.cardcade.app.games.scum.game.GameState
import com.cardcade.app.games.scum.game.SetupOptions

/** Serializable snapshot of an in-progress Scum match. */
data class GameSnapshot(
    val state: GameState,
    val options: SetupOptions,
)

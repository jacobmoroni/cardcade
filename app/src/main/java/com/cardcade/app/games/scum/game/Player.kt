package com.cardcade.app.games.scum.game

data class Player(
    val id: Int,
    val name: String,
    val isHuman: Boolean,
    val hand: List<Card> = emptyList(),
    /** True once the player has passed during the current trick. */
    val passedThisTrick: Boolean = false,
    /** True once the player has played every card this round. */
    val isOut: Boolean = false,
)

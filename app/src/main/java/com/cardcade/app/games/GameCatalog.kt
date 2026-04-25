package com.cardcade.app.games

import com.cardcade.app.games.luckyunders.LuckyUndersGame
import com.cardcade.app.games.scum.ScumGame

object GameCatalog {
    val all: List<Game> = listOf(LuckyUndersGame, ScumGame)

    fun byId(id: String): Game? = all.firstOrNull { it.id == id }
}

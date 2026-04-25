package com.cardcade.app.games.luckyunders

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.cardcade.app.games.Game
import com.cardcade.app.games.luckyunders.persistence.GameSnapshotStore
import com.cardcade.app.games.luckyunders.ui.LuckyUndersApp
import com.cardcade.app.games.luckyunders.ui.LuckyUndersLogo

object LuckyUndersGame : Game {
    override val id: String = "lucky_unders"
    override val title: String = "Lucky Unders"
    override val tagline: String = "A lucky game of overs, unders, and 2s"

    override fun hasSavedGame(context: Context): Boolean =
        GameSnapshotStore.hasSnapshot(context)

    @Composable
    override fun TileLogo() {
        LuckyUndersLogo(size = 112.dp)
    }

    @Composable
    override fun Screen(onExit: () -> Unit) {
        LuckyUndersApp(onExit = onExit)
    }
}

package com.cardcade.app.games.scum

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.cardcade.app.games.Game
import com.cardcade.app.games.scum.persistence.GameSnapshotStore
import com.cardcade.app.games.scum.ui.ScumApp
import com.cardcade.app.games.scum.ui.ScumLogo

object ScumGame : Game {
    override val id: String = "scum"
    override val title: String = "Scum"
    override val tagline: String = "Climb the throne, dodge the Scum seat"

    override fun hasSavedGame(context: Context): Boolean =
        GameSnapshotStore.hasSnapshot(context)

    @Composable
    override fun TileLogo() {
        ScumLogo(size = 112.dp)
    }

    @Composable
    override fun Screen(onExit: () -> Unit) {
        ScumApp(onExit = onExit)
    }
}

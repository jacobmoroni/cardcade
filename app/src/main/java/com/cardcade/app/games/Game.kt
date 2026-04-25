package com.cardcade.app.games

import android.content.Context
import androidx.compose.runtime.Composable

/**
 * A card game that can be launched from the Cardcade hub. Each game brings
 * its own tile logo and top-level screen; the hub handles app-wide theming
 * and crash reporting.
 */
interface Game {
    val id: String
    val title: String
    val tagline: String

    fun hasSavedGame(context: Context): Boolean

    @Composable
    fun TileLogo()

    @Composable
    fun Screen(onExit: () -> Unit)
}

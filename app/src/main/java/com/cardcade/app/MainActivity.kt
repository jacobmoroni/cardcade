package com.cardcade.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.cardcade.app.games.Game
import com.cardcade.app.ui.CrashScreen
import com.cardcade.app.ui.HomeScreen
import com.cardcade.app.ui.theme.CardcadeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)

        setContent {
            CardcadeTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    var crashText by remember { mutableStateOf(CrashLog.read(context)) }
    var activeGame by remember { mutableStateOf<Game?>(null) }

    if (crashText != null) {
        CrashScreen(
            text = crashText!!,
            onDismiss = {
                CrashLog.clear(context)
                crashText = null
            },
        )
        return
    }

    val game = activeGame
    if (game == null) {
        HomeScreen(onSelectGame = { activeGame = it })
    } else {
        game.Screen(onExit = { activeGame = null })
    }
}

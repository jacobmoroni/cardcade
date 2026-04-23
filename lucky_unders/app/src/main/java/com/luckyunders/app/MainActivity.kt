package com.luckyunders.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.luckyunders.app.persistence.GameSnapshotStore
import com.luckyunders.app.ui.CrashScreen
import com.luckyunders.app.ui.GameScreen
import com.luckyunders.app.ui.GameViewModel
import com.luckyunders.app.ui.LanLobbyScreen
import com.luckyunders.app.ui.MenuScreen
import com.luckyunders.app.ui.StartGameScreen
import com.luckyunders.app.ui.theme.LuckyUndersTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)

        setContent {
            LuckyUndersTheme {
                AppRoot()
            }
        }
    }
}

private enum class Screen { MENU, START, LOBBY, GAME }

@Composable
private fun AppRoot() {
    val vm: GameViewModel = viewModel()
    val context = androidx.compose.ui.platform.LocalContext.current

    var crashText by remember { mutableStateOf(CrashLog.read(context)) }
    var screen by remember { mutableStateOf(Screen.MENU) }
    var pendingOptions by remember { mutableStateOf<com.luckyunders.app.game.SetupOptions?>(null) }
    var hasSavedGame by remember { mutableStateOf(GameSnapshotStore.hasSnapshot(context)) }

    LaunchedEffect(screen) {
        if (screen == Screen.MENU) {
            hasSavedGame = GameSnapshotStore.hasSnapshot(context)
        }
    }

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

    when (screen) {
        Screen.MENU -> MenuScreen(
            hasSavedGame = hasSavedGame,
            onNewGame = { screen = Screen.START },
            onContinueGame = {
                val snap = GameSnapshotStore.load(context)
                if (snap != null) {
                    vm.restoreFrom(snap)
                    screen = Screen.GAME
                }
            },
            onClearSavedGame = {
                GameSnapshotStore.clear(context)
                hasSavedGame = false
                vm.clearGame()
            },
        )
        Screen.START -> StartGameScreen(
            onBack = { screen = Screen.MENU },
            onStart = { opts ->
                pendingOptions = opts
                vm.startGame(opts)
                screen = Screen.GAME
            },
            onOpenOnlineLobby = { opts ->
                pendingOptions = opts
                screen = Screen.LOBBY
            },
        )
        Screen.LOBBY -> LanLobbyScreen(
            setup = pendingOptions ?: run {
                screen = Screen.START
                return
            },
            onBack = { screen = Screen.START },
            onStartLocal = { opts ->
                vm.startGame(opts)
                screen = Screen.GAME
            },
        )
        Screen.GAME -> GameScreen(
            vm = vm,
            onRestart = {
                vm.clearGame()
                screen = Screen.MENU
            },
        )
    }
}

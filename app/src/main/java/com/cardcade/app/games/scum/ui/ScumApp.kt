package com.cardcade.app.games.scum.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cardcade.app.games.scum.persistence.GameSnapshotStore

/**
 * Internal navigation shell for Scum. [onExit] returns the user to the
 * Cardcade home screen.
 */
@Composable
fun ScumApp(onExit: () -> Unit) {
    val vm: GameViewModel = viewModel()
    val context = LocalContext.current
    var screen by remember { mutableStateOf(Screen.MENU) }
    var hasSavedGame by remember { mutableStateOf(GameSnapshotStore.hasSnapshot(context)) }

    LaunchedEffect(screen) {
        if (screen == Screen.MENU) hasSavedGame = GameSnapshotStore.hasSnapshot(context)
    }

    BackHandler(enabled = true) {
        when (screen) {
            Screen.MENU -> onExit()
            Screen.START -> screen = Screen.MENU
            Screen.GAME -> { /* ignore — users exit via the menu button */ }
        }
    }

    when (screen) {
        Screen.MENU -> MenuScreen(
            hasSavedGame = hasSavedGame,
            onBack = onExit,
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
                vm.startGame(opts)
                screen = Screen.GAME
            },
        )
        Screen.GAME -> GameScreen(
            vm = vm,
            onExitToMenu = {
                vm.clearGame()
                screen = Screen.MENU
            },
        )
    }
}

private enum class Screen { MENU, START, GAME }

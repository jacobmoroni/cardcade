package com.cardcade.app.games.luckyunders.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cardcade.app.games.luckyunders.game.SetupOptions
import com.cardcade.app.games.luckyunders.persistence.GameSnapshotStore

/**
 * Internal navigation shell for Lucky Unders. Called by the hub once the user
 * picks this game; [onExit] returns the user to the Cardcade home screen.
 */
@Composable
fun LuckyUndersApp(onExit: () -> Unit) {
    val vm: GameViewModel = viewModel()
    val context = androidx.compose.ui.platform.LocalContext.current

    var screen by remember { mutableStateOf(Screen.MENU) }
    var pendingOptions by remember { mutableStateOf<SetupOptions?>(null) }
    var hasSavedGame by remember { mutableStateOf(GameSnapshotStore.hasSnapshot(context)) }

    LaunchedEffect(screen) {
        if (screen == Screen.MENU) {
            hasSavedGame = GameSnapshotStore.hasSnapshot(context)
        }
    }

    BackHandler(enabled = true) {
        when (screen) {
            Screen.MENU -> onExit()
            Screen.START -> screen = Screen.MENU
            Screen.LOBBY -> screen = Screen.START
            Screen.GAME -> { /* ignore back during an active game */ }
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

private enum class Screen { MENU, START, LOBBY, GAME }

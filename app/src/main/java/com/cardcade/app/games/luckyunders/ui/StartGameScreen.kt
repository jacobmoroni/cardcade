package com.cardcade.app.games.luckyunders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardcade.app.games.luckyunders.game.PlayerOrderRule
import com.cardcade.app.games.luckyunders.game.SeriesConfig
import com.cardcade.app.games.luckyunders.game.SeriesFormat
import com.cardcade.app.games.luckyunders.game.SessionMode
import com.cardcade.app.games.luckyunders.game.SetupOptions
import com.cardcade.app.games.luckyunders.persistence.UserPreferences

@Composable
fun StartGameScreen(
    onStart: (SetupOptions) -> Unit,
    onBack: () -> Unit,
    onOpenOnlineLobby: (SetupOptions) -> Unit,
) {
    val context = LocalContext.current
    val saved = remember {
        UserPreferences.getStartSelections(
            context,
            UserPreferences.StartSelections(
                totalPlayers = 3,
                mode = SessionMode.SOLO.name,
                passPlayHumans = 2,
                seriesFormat = SeriesFormat.SERIES.name,
                targetScore = 100,
                orderRule = PlayerOrderRule.MAINTAIN.name,
            ),
        )
    }
    var totalPlayers by remember { mutableStateOf(saved.totalPlayers.coerceIn(2, 4)) }
    var mode by remember {
        mutableStateOf(
            runCatching { SessionMode.valueOf(saved.mode) }.getOrDefault(SessionMode.SOLO)
        )
    }
    var passPlayHumans by remember {
        mutableStateOf(saved.passPlayHumans.coerceIn(2, totalPlayers))
    }
    var seriesFormat by remember {
        mutableStateOf(
            runCatching { SeriesFormat.valueOf(saved.seriesFormat) }
                .getOrDefault(SeriesFormat.SERIES)
        )
    }
    var targetScore by remember { mutableStateOf(saved.targetScore) }
    var orderRule by remember {
        mutableStateOf(
            runCatching { PlayerOrderRule.valueOf(saved.orderRule) }
                .getOrDefault(PlayerOrderRule.MAINTAIN)
        )
    }

    LaunchedEffect(totalPlayers, mode, passPlayHumans, seriesFormat, targetScore, orderRule) {
        UserPreferences.setStartSelections(
            context,
            UserPreferences.StartSelections(
                totalPlayers = totalPlayers,
                mode = mode.name,
                passPlayHumans = passPlayHumans,
                seriesFormat = seriesFormat.name,
                targetScore = targetScore,
                orderRule = orderRule.name,
            ),
        )
    }

    fun seatCounts(): Triple<Int, Int, Int> = when (mode) {
        SessionMode.SOLO -> Triple(1, 0, totalPlayers - 1)
        SessionMode.PASS_AND_PLAY -> {
            val humans = passPlayHumans.coerceIn(2, totalPlayers)
            Triple(humans, 0, totalPlayers - humans)
        }
        SessionMode.ONLINE_LAN -> Triple(1, 0, totalPlayers - 1)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF063F23))
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text("← Back", color = Color(0xFFE6B54A), fontSize = 16.sp)
                }
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "Start a game",
                color = Color(0xFFE6B54A),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
            )

            Spacer(Modifier.height(16.dp))
            SectionLabel("Game type")
            SelectorRow(
                items = listOf("1 Player", "Pass & Play", "Online"),
                selectedIndex = when (mode) {
                    SessionMode.SOLO -> 0
                    SessionMode.PASS_AND_PLAY -> 1
                    SessionMode.ONLINE_LAN -> 2
                },
                onSelect = {
                    mode = when (it) {
                        0 -> SessionMode.SOLO
                        1 -> SessionMode.PASS_AND_PLAY
                        else -> SessionMode.ONLINE_LAN
                    }
                },
            )
            Text(
                text = when (mode) {
                    SessionMode.SOLO -> "You play. The remaining seats are CPU opponents."
                    SessionMode.PASS_AND_PLAY -> "Multiple humans share this device. CPUs fill any empty seats."
                    SessionMode.ONLINE_LAN -> "Players on the same wifi can join. CPUs fill seats not joined."
                },
                color = Color(0xFFB9F5C9),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(16.dp))
            SectionLabel("Players")
            SelectorRow(
                items = (2..4).map { it.toString() },
                selectedIndex = totalPlayers - 2,
                onSelect = {
                    totalPlayers = it + 2
                    if (passPlayHumans > totalPlayers) passPlayHumans = totalPlayers
                },
            )

            if (mode == SessionMode.PASS_AND_PLAY) {
                Spacer(Modifier.height(12.dp))
                SectionLabel("Pass & Play humans")
                SelectorRow(
                    items = (2..totalPlayers).map { it.toString() },
                    selectedIndex = (passPlayHumans - 2).coerceIn(0, totalPlayers - 2),
                    onSelect = { passPlayHumans = it + 2 },
                )
                val (humans, _, cpus) = seatCounts()
                Text(
                    "$humans humans + $cpus CPU${if (cpus == 1) "" else "s"}",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Rules")
            SelectorRow(
                items = listOf("Single Game", "Series"),
                selectedIndex = if (seriesFormat == SeriesFormat.SINGLE) 0 else 1,
                onSelect = {
                    seriesFormat = if (it == 0) SeriesFormat.SINGLE else SeriesFormat.SERIES
                },
            )

            if (seriesFormat == SeriesFormat.SERIES) {
                Spacer(Modifier.height(12.dp))
                SectionLabel("Play to score")
                val presets = listOf(25, 50, 100, 200)
                SelectorRow(
                    items = presets.map { it.toString() },
                    selectedIndex = presets.indexOf(targetScore).coerceAtLeast(0),
                    onSelect = { targetScore = presets[it] },
                )

                Spacer(Modifier.height(12.dp))
                SectionLabel("Player order (next round)")
                SelectorRow(
                    items = listOf("Same order", "Random", "Winner first"),
                    selectedIndex = when (orderRule) {
                        PlayerOrderRule.MAINTAIN -> 0
                        PlayerOrderRule.RANDOM -> 1
                        PlayerOrderRule.WINNING_ORDER -> 2
                    },
                    onSelect = {
                        orderRule = when (it) {
                            0 -> PlayerOrderRule.MAINTAIN
                            1 -> PlayerOrderRule.RANDOM
                            else -> PlayerOrderRule.WINNING_ORDER
                        }
                    },
                )
                Text(
                    when (orderRule) {
                        PlayerOrderRule.MAINTAIN -> "Keep the original seating order each round."
                        PlayerOrderRule.RANDOM -> "Shuffle the seating order before each new round."
                        PlayerOrderRule.WINNING_ORDER -> "Last round's winner leads; remaining players follow in finish order."
                    },
                    color = Color(0xFFB9F5C9),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(Modifier.height(28.dp))
            val (humans, online, cpus) = seatCounts()
            val opts = SetupOptions(
                totalPlayers = totalPlayers,
                localHumans = humans,
                onlineHumans = online,
                cpuCount = cpus,
                mode = mode,
                series = SeriesConfig(
                    format = seriesFormat,
                    targetScore = targetScore,
                    orderRule = orderRule,
                ),
            )
            Button(
                onClick = {
                    if (mode == SessionMode.ONLINE_LAN) onOpenOnlineLobby(opts) else onStart(opts)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE6B54A),
                    contentColor = Color.Black,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    if (mode == SessionMode.ONLINE_LAN) "Open LAN lobby" else "Deal cards",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = Color(0xFFE6B54A),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun SelectorRow(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEachIndexed { i, label ->
            val isSel = i == selectedIndex
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSel) Color(0xFFE6B54A) else Color(0xFF0B3F27),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        width = 1.dp,
                        color = if (isSel) Color(0xFFE6B54A) else Color(0xFF145A34),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable { onSelect(i) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        color = if (isSel) Color.Black else Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

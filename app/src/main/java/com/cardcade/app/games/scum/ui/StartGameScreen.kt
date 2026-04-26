package com.cardcade.app.games.scum.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.cardcade.app.games.scum.game.AIDifficulty
import com.cardcade.app.games.scum.game.SeriesConfig
import com.cardcade.app.games.scum.game.SeriesFormat
import com.cardcade.app.games.scum.game.SessionMode
import com.cardcade.app.games.scum.game.SetupOptions
import com.cardcade.app.games.scum.persistence.UserPreferences

@Composable
fun StartGameScreen(
    onBack: () -> Unit,
    onStart: (SetupOptions) -> Unit,
) {
    val context = LocalContext.current
    val stored = remember { UserPreferences.getSetupOptions(context) }

    var totalPlayers by remember { mutableStateOf(stored.totalPlayers) }
    var mode by remember { mutableStateOf(stored.mode) }
    var humanCount by remember { mutableStateOf(stored.humanCount) }
    var royaltyTiers by remember { mutableStateOf(stored.royaltyTiers) }
    var topTierSwaps by remember { mutableStateOf(stored.topTierSwaps) }
    var jokerCount by remember { mutableStateOf(stored.jokerCount) }
    var jokersUnswappable by remember { mutableStateOf(stored.jokersUnswappable) }
    var jokerBeatsAll by remember { mutableStateOf(stored.jokerBeatsAll) }
    var extraSuits by remember { mutableStateOf(stored.extraSuits) }
    var aiDifficulty by remember { mutableStateOf(stored.aiDifficulty) }
    var seriesFormat by remember { mutableStateOf(stored.seriesConfig.format) }
    var targetScore by remember { mutableStateOf(stored.seriesConfig.targetScore) }
    var fixedRounds by remember { mutableStateOf(stored.seriesConfig.fixedRounds) }

    // Keep human count within bounds when total players changes.
    if (humanCount > totalPlayers) humanCount = totalPlayers

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF063F23))
            .safeDrawingPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Text(
                        "← Back",
                        color = Color(0xFFE6B54A),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Text(
                "Start Scum",
                color = Color(0xFFE6B54A),
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(16.dp))

            OptionSection("Players") {
                SegmentedOption(
                    values = (4..8).toList(),
                    selected = totalPlayers,
                    label = { it.toString() },
                    onSelect = { totalPlayers = it },
                )
            }

            OptionSection("Session") {
                SegmentedOption(
                    values = listOf(SessionMode.AI_FILL, SessionMode.PASS_AND_PLAY),
                    selected = mode,
                    label = { if (it == SessionMode.AI_FILL) "You + AI" else "Pass & play" },
                    onSelect = { mode = it },
                )
            }
            if (mode == SessionMode.AI_FILL && totalPlayers > 1) {
                OptionSection("Humans on this device") {
                    SegmentedOption(
                        values = (1..totalPlayers).toList(),
                        selected = humanCount,
                        label = { it.toString() },
                        onSelect = { humanCount = it },
                    )
                }
            }

            if (mode == SessionMode.AI_FILL) {
                OptionSection("AI difficulty") {
                    SegmentedOption(
                        values = listOf(AIDifficulty.EASY, AIDifficulty.MEDIUM),
                        selected = aiDifficulty,
                        label = { if (it == AIDifficulty.EASY) "Easy" else "Medium" },
                        onSelect = { aiDifficulty = it },
                    )
                }
            }

            OptionSection("Deck size") {
                val labels = listOf("1 deck", "+1 suit", "+2 suits", "+3 suits", "2 decks")
                SegmentedOption(
                    values = (0..4).toList(),
                    selected = extraSuits,
                    label = { labels[it] },
                    onSelect = { extraSuits = it },
                )
                Text(
                    "Total: ${52 + extraSuits * 13 + jokerCount} cards (incl. jokers)",
                    color = Color(0xFFB9F5C9),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            OptionSection("Jokers in deck") {
                SegmentedOption(
                    values = listOf(1, 2),
                    selected = jokerCount,
                    label = { "$it" },
                    onSelect = { jokerCount = it },
                )
                ToggleRow(
                    label = "Scum may keep jokers (don't trade)",
                    checked = jokersUnswappable,
                    onCheckedChange = { jokersUnswappable = it },
                )
                ToggleRow(
                    label = "Joker beats anything (any count, any pile)",
                    checked = jokerBeatsAll,
                    onCheckedChange = { jokerBeatsAll = it },
                )
            }

            OptionSection("Royalty tiers") {
                SegmentedOption(
                    values = listOf(1, 2, 3),
                    selected = royaltyTiers,
                    label = {
                        when (it) {
                            1 -> "King only"
                            2 -> "King + Queen"
                            else -> "King + Queen + Prince"
                        }
                    },
                    onSelect = { royaltyTiers = it },
                )
            }

            OptionSection("Top-tier card swaps") {
                SegmentedOption(
                    values = listOf(1, 2, 3),
                    selected = topTierSwaps,
                    label = { "$it card${if (it == 1) "" else "s"}" },
                    onSelect = { topTierSwaps = it },
                )
                Text(
                    swapPreview(royaltyTiers, topTierSwaps),
                    color = Color(0xFFB9F5C9),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            OptionSection("Match format") {
                SegmentedOption(
                    values = listOf(SeriesFormat.TARGET_SCORE, SeriesFormat.FIXED_ROUNDS),
                    selected = seriesFormat,
                    label = {
                        if (it == SeriesFormat.TARGET_SCORE) "Target score" else "Fixed rounds"
                    },
                    onSelect = { seriesFormat = it },
                )
                if (seriesFormat == SeriesFormat.TARGET_SCORE) {
                    Text("Target:", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    SegmentedOption(
                        values = listOf(25, 50, 75, 100),
                        selected = targetScore,
                        label = { "$it pts" },
                        onSelect = { targetScore = it },
                    )
                } else {
                    Text("Rounds:", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    SegmentedOption(
                        values = listOf(3, 5, 7, 10),
                        selected = fixedRounds,
                        label = { "$it" },
                        onSelect = { fixedRounds = it },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    val opts = SetupOptions(
                        totalPlayers = totalPlayers,
                        mode = mode,
                        humanCount = if (mode == SessionMode.PASS_AND_PLAY) totalPlayers else humanCount,
                        royaltyTiers = royaltyTiers,
                        topTierSwaps = topTierSwaps,
                        jokerCount = jokerCount,
                        jokersUnswappable = jokersUnswappable,
                        jokerBeatsAll = jokerBeatsAll,
                        extraSuits = extraSuits,
                        seriesConfig = SeriesConfig(
                            format = seriesFormat,
                            targetScore = targetScore,
                            fixedRounds = fixedRounds,
                        ),
                        aiDifficulty = aiDifficulty,
                    )
                    UserPreferences.setSetupOptions(context, opts)
                    onStart(opts)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE6B54A),
                    contentColor = Color.Black,
                ),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Deal cards", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OptionSection(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Text(
        title,
        color = Color(0xFFE6B54A),
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    content()
}

@Composable
private fun <T> SegmentedOption(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        values.forEach { v ->
            val isSel = v == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSel) Color(0xFFE6B54A) else Color(0xFF0B6A3A))
                    .clickable { onSelect(v) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(v),
                    color = if (isSel) Color.Black else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color(0xFFE6B54A),
                uncheckedThumbColor = Color(0xFFE6B54A),
                uncheckedTrackColor = Color(0xFF334155),
            ),
        )
    }
}

private fun swapPreview(tiers: Int, topTier: Int): String {
    val names = listOf("King", "Queen", "Prince").take(tiers)
    return names.mapIndexed { i, name ->
        val swaps = (topTier - i).coerceAtLeast(1)
        "$name $swaps"
    }.joinToString(" · ")
}

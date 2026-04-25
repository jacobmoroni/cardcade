package com.cardcade.app.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.cardcade.app.games.Game
import com.cardcade.app.games.GameCatalog

/**
 * Cardcade hub: header with branding, then a list of game tiles. Tapping a
 * tile launches that game's internal screen stack.
 */
@Composable
fun HomeScreen(onSelectGame: (Game) -> Unit) {
    val context = LocalContext.current
    val games = GameCatalog.all
    // Re-evaluate saved-game flags each time the hub recomposes so the
    // "Continue available" tag flips off after a game is finished or cleared.
    var savedVersion by remember { mutableStateOf(0) }
    val savedMap = remember(savedVersion) {
        games.associate { it.id to it.hasSavedGame(context) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF063F23))
            .safeDrawingPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            CardcadeLogo(size = 180.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                "Cardcade",
                color = Color(0xFFE6B54A),
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "A cascade of card games",
                color = Color(0xFFB9F5C9),
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(24.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                items(games, key = { it.id }) { game ->
                    GameTile(
                        game = game,
                        hasSavedGame = savedMap[game.id] == true,
                        onClick = {
                            onSelectGame(game)
                            savedVersion += 1
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GameTile(
    game: Game,
    hasSavedGame: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = Color(0xFF0B6A3A),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(modifier = Modifier.size(112.dp)) {
                game.TileLogo()
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    game.title,
                    color = Color(0xFFE6B54A),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    game.tagline,
                    color = Color.White,
                    fontSize = 13.sp,
                )
                if (hasSavedGame) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "● Continue available",
                        color = Color(0xFFE6B54A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

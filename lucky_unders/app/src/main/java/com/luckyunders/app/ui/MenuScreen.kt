package com.luckyunders.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Home screen: logo + title at the top, action buttons pinned at the bottom.
 * Continue Game is hidden when no saved game exists. Starting a new game with
 * an existing save asks the user to confirm before clearing it.
 */
@Composable
fun MenuScreen(
    hasSavedGame: Boolean,
    onNewGame: () -> Unit,
    onContinueGame: () -> Unit,
    onClearSavedGame: () -> Unit,
) {
    var showRules by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF063F23))
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            AppLogo(size = 220.dp)
            Spacer(Modifier.height(18.dp))
            Text(
                "Lucky Unders",
                color = Color(0xFFE6B54A),
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "A lucky game of overs, unders, and 2s",
                color = Color(0xFFB9F5C9),
                fontSize = 14.sp,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (hasSavedGame) {
                Button(
                    onClick = onContinueGame,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE6B54A),
                        contentColor = Color.Black,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Continue Game", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            Button(
                onClick = {
                    if (hasSavedGame) showClearConfirm = true else onNewGame()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0B6A3A),
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("New Game", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { showRules = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF334155),
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Rules", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showRules) {
        RulesDialog(onDismiss = { showRules = false })
    }

    if (showClearConfirm) {
        Dialog(onDismissRequest = { showClearConfirm = false }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1F2937),
            ) {
                Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                    Text(
                        "Clear saved game?",
                        color = Color(0xFFE6B54A),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Starting a new game will discard your current saved match. Continue?",
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Button(
                            onClick = { showClearConfirm = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF334155),
                                contentColor = Color.White,
                            ),
                        ) { Text("Cancel") }
                        Button(
                            onClick = {
                                showClearConfirm = false
                                onClearSavedGame()
                                onNewGame()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE6B54A),
                                contentColor = Color.Black,
                            ),
                        ) { Text("Start new game") }
                    }
                }
            }
        }
    }
}

// Local Row import to avoid cluttering the file header.
@Composable
private fun Row(
    horizontalArrangement: Arrangement.Horizontal,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        content = content,
    )
}

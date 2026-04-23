package com.luckyunders.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

val RULES_TEXT: String = """
Setup
• A full 52-card deck is dealt regardless of player count.
• Each player gets 4 face-down unders, 4 face-up overs (resting on top of the unders), and 5 cards in hand.
• The remainder forms a face-down draw pile.
• Pre-game swap: each player may swap up to 2 hand cards with any of their 4 overs.

Card values
• Aces are HIGH — higher than a King.
• A 2 is wild and restarts the pile. The next play can be any card. The player who played the 2 goes again.
• A 10 burns the pile. It can be played on any card, and play passes to the next player.
• Four of the same rank in a row on the pile also burns it (advances to next player).

A turn
• Play one or more cards of the same rank. The rank must be greater than or equal to the top of the pile (or any card if the pile is empty or the top is a wild 2).
• A 2 or a 10 may be played on any card.
• When playing your last hand cards, you may include any same-rank overs in the same play.
• If you cannot legally play, pick up the entire pile into your hand.

Refilling
• After playing from your hand, draw back up to 5 cards from the draw pile.
• When the draw pile is empty, you no longer refill.

Phases
1. Hand: play from your hand until it's empty.
2. Overs: once your hand is empty AND the draw pile is empty, play from your face-up overs.
3. Unders: once your overs are gone, flip blind unders one at a time. If the flipped card is legal it auto-plays; otherwise you pick up the pile plus the flipped card.

Winning
• First player to clear all three zones wins. Remaining players keep playing for second, third, etc.
""".trimIndent()

@Composable
fun RulesDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1F2937),
        ) {
            Column(modifier = Modifier.padding(18.dp).fillMaxWidth()) {
                Text(
                    "Rules",
                    color = Color(0xFFE6B54A),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(RULES_TEXT, color = Color(0xFFE5E7EB), fontSize = 13.sp)
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE6B54A),
                        contentColor = Color.Black,
                    ),
                    modifier = Modifier.align(Alignment.End),
                ) { Text("Close") }
            }
        }
    }
}

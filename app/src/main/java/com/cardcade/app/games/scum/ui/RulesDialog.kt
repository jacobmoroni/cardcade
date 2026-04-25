package com.cardcade.app.games.scum.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

@Composable
fun RulesDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF1F2937),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Scum",
                    color = Color(0xFFE6B54A),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "Be the first to run out of cards each round — you become King. Be last and you're Scum, who has to hand over their best cards next round.",
                    color = Color.White,
                    fontSize = 13.sp,
                )
                Section("Card ranks") {
                    Text(
                        "Jokers are highest. Then Ace, King, Queen, Jack, 10 … down to 2 (lowest). Suits don't matter — only rank.",
                        color = Color.White,
                        fontSize = 13.sp,
                    )
                }
                Section("Playing a trick") {
                    Text(
                        "Each trick, the leader plays a single, pair, triple, or quad of one rank. In turn, the next player either plays the same number of cards of a higher rank, or passes. Passing puts you out for the rest of that trick.",
                        color = Color.White,
                        fontSize = 13.sp,
                    )
                }
                Section("House rule — tricks keep going") {
                    Text(
                        "Play keeps looping around the table until everyone has passed. If only one player is still in the trick, they may keep playing more sets on their own until they choose to stop or run out. Whoever was last to play leads the next trick.",
                        color = Color(0xFFB9F5C9),
                        fontSize = 13.sp,
                    )
                }
                Section("Pre-round card exchange") {
                    Text(
                        "At the start of every round after the first, Scum gives their highest cards to the King and receives the King's lowest in return. Additional royalty tiers (Queen, Prince) do the same at lower swap counts. Jokers can be marked unswappable, in which case Scum may keep any jokers they're dealt.",
                        color = Color.White,
                        fontSize = 13.sp,
                    )
                }
                Section("Round 1 start") {
                    Text(
                        "Whoever holds the 2 of clubs leads the first trick of a brand-new match.",
                        color = Color.White,
                        fontSize = 13.sp,
                    )
                }
                Section("Scoring") {
                    Text(
                        "King +10 · Queen +5 · Prince +3 · Commoner 0 · Peasant −1 · Vice-Scum −3 · Scum −5. First to the target score wins the match.",
                        color = Color.White,
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE6B54A),
                        contentColor = Color.Black,
                    ),
                ) { Text("Got it", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        color = Color(0xFFE6B54A),
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
    )
    content()
}

package com.cardcade.app.games.luckyunders.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardcade.app.games.luckyunders.game.Card

enum class CardFace { FRONT, BACK, EMPTY }

@Composable
fun CardView(
    card: Card?,
    face: CardFace,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    widthDp: Int = 52,
    heightDp: Int = 74,
    dimmed: Boolean = false,
) {
    val border = when {
        selected -> BorderStroke(3.dp, Color(0xFFE6B54A))
        face == CardFace.EMPTY -> BorderStroke(1.dp, Color(0x55FFFFFF))
        else -> BorderStroke(1.dp, Color(0x33000000))
    }
    val bg = when (face) {
        CardFace.FRONT -> if (dimmed) Color(0xFFB6BCC4) else Color.White
        CardFace.BACK -> Color(0xFF1E3A8A)
        CardFace.EMPTY -> Color(0x22000000)
    }

    Surface(
        modifier = modifier
            .size(width = widthDp.dp, height = heightDp.dp)
            .let { if (onClick != null) it.clickable { onClick() } else it },
        shape = RoundedCornerShape(6.dp),
        color = bg,
        border = border,
        shadowElevation = if (face == CardFace.EMPTY) 0.dp else 3.dp,
    ) {
        when (face) {
            CardFace.FRONT -> if (card != null) {
                val baseColor = if (card.suit.isRed) Color(0xFFB91C1C) else Color.Black
                val color = if (dimmed) baseColor.copy(alpha = 0.45f) else baseColor
                Column(
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(card.rankLabel, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(card.suit.symbol, color = color, fontSize = 22.sp)
                    }
                }
            }
            CardFace.BACK -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E3A8A)),
                contentAlignment = Alignment.Center,
            ) {
                Text("\u2605", color = Color(0xFFE6B54A), fontSize = 22.sp)
            }
            CardFace.EMPTY -> {}
        }
    }
}

package com.cardcade.app.games.scum.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardcade.app.games.scum.game.Card
import com.cardcade.app.games.scum.game.Suit
import com.cardcade.app.games.scum.game.rankLabel

/**
 * A single playing-card tile. Jokers render with "JKR" across the face; other
 * cards show rank + suit glyph. Tapping toggles [selected]; [enabled] greys it
 * out when no legal play uses it.
 */
@Composable
fun CardView(
    card: Card,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    width: Dp = 52.dp,
    height: Dp = 76.dp,
) {
    // Opaque muted palette for ineligible cards — still readable, clearly not
    // playable. Full alpha keeps the background from turning translucent over
    // the dark felt.
    val red = card.isJoker || card.suit == Suit.HEARTS || card.suit == Suit.DIAMONDS
    val bg = when {
        !enabled -> Color(0xFFD1D5DB)
        card.isJoker -> Color(0xFFFFF7D6)
        else -> Color.White
    }
    val textColor = when {
        !enabled -> Color(0xFF6B7280)
        red -> Color(0xFFB91C1C)
        else -> Color.Black
    }
    val borderColor = when {
        selected -> Color(0xFFE6B54A)
        !enabled -> Color(0xFF374151)
        else -> Color(0xFF0B6A3A)
    }

    Box(
        modifier = Modifier
            .size(width, height)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        if (card.isJoker) {
            Text(
                "JKR",
                color = if (enabled) Color(0xFFB45309) else Color(0xFF6B7280),
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(2.dp),
            )
        } else {
            Text(
                rankLabel(card.rank),
                color = textColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
            Text(
                suitGlyph(card.suit),
                color = textColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
fun MiniCard(card: Card, width: Dp = 40.dp, height: Dp = 58.dp) {
    val bg = if (card.isJoker) Color(0xFFFFF7D6) else Color.White
    val red = card.isJoker || card.suit == Suit.HEARTS || card.suit == Suit.DIAMONDS
    val textColor = if (red) Color(0xFFB91C1C) else Color.Black
    Box(
        modifier = Modifier
            .size(width, height)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, Color(0xFF0B6A3A), RoundedCornerShape(6.dp)),
    ) {
        val label = if (card.isJoker) "JKR" else rankLabel(card.rank)
        Text(
            label,
            color = textColor,
            fontSize = if (card.isJoker) 12.sp else 18.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

private fun suitGlyph(suit: Suit?): String = when (suit) {
    Suit.CLUBS -> "♣"
    Suit.DIAMONDS -> "♦"
    Suit.HEARTS -> "♥"
    Suit.SPADES -> "♠"
    null -> "★"
}

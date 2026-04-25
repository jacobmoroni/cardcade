package com.cardcade.app.games.scum.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Scum logo: a gold crown resting on a stack of three cards. Represents the
 * climb from Scum to King at the heart of the game.
 */
@Composable
fun ScumLogo(
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
) {
    val measurer = rememberTextMeasurer()
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF063F23)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Three fanned cards at the bottom.
            val cardW = w * 0.40f
            val cardH = h * 0.42f
            val cardRadius = CornerRadius(cardW * 0.14f)
            val centerX = w / 2f
            val baseY = h * 0.78f

            val offsets = listOf(-cardW * 0.55f to -4f, 0f to 0f, cardW * 0.55f to -4f)
            val labels = listOf("K" to false, "A" to true, "2" to false)

            for (i in offsets.indices) {
                val (dx, dy) = offsets[i]
                val topLeft = Offset(centerX - cardW / 2f + dx, baseY - cardH + dy)
                drawRoundRect(
                    color = Color.White,
                    topLeft = topLeft,
                    size = Size(cardW, cardH),
                    cornerRadius = cardRadius,
                )
                drawRoundRect(
                    color = Color(0xFF0B6A3A),
                    topLeft = topLeft,
                    size = Size(cardW, cardH),
                    cornerRadius = cardRadius,
                    style = Stroke(width = w * 0.009f),
                )
                val (text, red) = labels[i]
                val style = TextStyle(
                    color = if (red) Color(0xFFB91C1C) else Color.Black,
                    fontSize = (cardH * 0.56f).toSp(),
                    fontWeight = FontWeight.Black,
                )
                val layout = measurer.measure(text, style)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        topLeft.x + (cardW - layout.size.width) / 2f,
                        topLeft.y + (cardH - layout.size.height) / 2f,
                    ),
                )
            }

            // Crown above the cards.
            val crownBaseY = h * 0.40f
            val crownTopY = h * 0.14f
            val crownLeft = w * 0.22f
            val crownRight = w * 0.78f
            val midX = (crownLeft + crownRight) / 2f

            val crown = Path().apply {
                moveTo(crownLeft, crownBaseY)
                lineTo(crownLeft + (crownRight - crownLeft) * 0.14f, crownTopY + h * 0.06f)
                lineTo(crownLeft + (crownRight - crownLeft) * 0.30f, crownBaseY - h * 0.06f)
                lineTo(midX - (crownRight - crownLeft) * 0.10f, crownTopY)
                lineTo(midX + (crownRight - crownLeft) * 0.10f, crownTopY)
                lineTo(crownLeft + (crownRight - crownLeft) * 0.70f, crownBaseY - h * 0.06f)
                lineTo(crownLeft + (crownRight - crownLeft) * 0.86f, crownTopY + h * 0.06f)
                lineTo(crownRight, crownBaseY)
                close()
            }
            drawPath(crown, color = Color(0xFFE6B54A))
            drawPath(crown, color = Color(0xFF7C5E12), style = Stroke(width = w * 0.012f))

            // Jewel studs along the crown's base.
            drawCircle(color = Color(0xFFB91C1C), radius = w * 0.028f, center = Offset(midX, crownBaseY - h * 0.03f))
            drawCircle(color = Color(0xFF1E3A8A), radius = w * 0.024f, center = Offset(midX - (crownRight - crownLeft) * 0.28f, crownBaseY - h * 0.03f))
            drawCircle(color = Color(0xFF1E3A8A), radius = w * 0.024f, center = Offset(midX + (crownRight - crownLeft) * 0.28f, crownBaseY - h * 0.03f))
        }
    }
}

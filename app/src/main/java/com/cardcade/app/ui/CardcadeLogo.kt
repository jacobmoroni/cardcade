package com.cardcade.app.ui

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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Cardcade hub logo: a fanned-out hand of four playing cards showing the four
 * suit glyphs. Represents the collection of card games the app offers.
 */
@Composable
fun CardcadeLogo(
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
) {
    val measurer = rememberTextMeasurer()
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF063F23)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            val cardW = w * 0.36f
            val cardH = h * 0.58f
            val cardRadius = CornerRadius(cardW * 0.14f)
            val pivotX = w / 2f
            val pivotY = h * 0.92f

            data class CardSpec(val glyph: String, val red: Boolean, val angle: Float)

            val cards = listOf(
                CardSpec("♣", false, -36f),
                CardSpec("♦", true, -12f),
                CardSpec("♥", true, 12f),
                CardSpec("♠", false, 36f),
            )

            cards.forEach { spec ->
                rotate(degrees = spec.angle, pivot = Offset(pivotX, pivotY)) {
                    val topLeft = Offset(pivotX - cardW / 2f, pivotY - cardH - h * 0.06f)
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
                        style = Stroke(width = w * 0.010f),
                    )
                    drawGlyph(
                        measurer = measurer,
                        glyph = spec.glyph,
                        red = spec.red,
                        topLeft = topLeft,
                        cardW = cardW,
                        cardH = cardH,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawGlyph(
    measurer: androidx.compose.ui.text.TextMeasurer,
    glyph: String,
    red: Boolean,
    topLeft: Offset,
    cardW: Float,
    cardH: Float,
) {
    val style = TextStyle(
        color = if (red) Color(0xFFB91C1C) else Color(0xFF111111),
        fontSize = (cardH * 0.52f).toSp(),
        fontWeight = FontWeight.Black,
    )
    val layout = measurer.measure(glyph, style)
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(
            topLeft.x + (cardW - layout.size.width) / 2f,
            topLeft.y + (cardH - layout.size.height) / 2f,
        ),
    )
}

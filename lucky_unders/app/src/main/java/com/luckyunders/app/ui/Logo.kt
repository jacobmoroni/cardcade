package com.luckyunders.app.ui

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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * App logo: four overs (one per seat) layered over four unders, crowned by an
 * oversized four-leaf clover. Two of the overs show "2" and "10" to call out
 * the game's key wild / burn ranks.
 */
@Composable
fun AppLogo(
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

            val cardCount = 4
            val cardGap = w * 0.025f
            val cardW = (w * 0.84f - cardGap * (cardCount - 1)) / cardCount
            val cardH = h * 0.50f
            val cardRadius = CornerRadius(cardW * 0.18f)
            val totalCardsW = cardW * cardCount + cardGap * (cardCount - 1)
            val leftX = (w - totalCardsW) / 2f
            val cardRowTop = h * 0.48f

            fun cardX(i: Int) = leftX + i * (cardW + cardGap)

            fun drawCardBack(x: Float, y: Float) {
                drawRoundRect(
                    color = Color(0xFF132D6B),
                    topLeft = Offset(x, y),
                    size = Size(cardW, cardH),
                    cornerRadius = cardRadius,
                )
                drawRoundRect(
                    color = Color(0xFFE6B54A),
                    topLeft = Offset(x, y),
                    size = Size(cardW, cardH),
                    cornerRadius = cardRadius,
                    style = Stroke(width = w * 0.010f),
                )
            }

            fun drawCardFront(x: Float, y: Float, label: String, isRed: Boolean) {
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(x, y),
                    size = Size(cardW, cardH),
                    cornerRadius = cardRadius,
                )
                drawRoundRect(
                    color = Color(0xFF0B6A3A),
                    topLeft = Offset(x, y),
                    size = Size(cardW, cardH),
                    cornerRadius = cardRadius,
                    style = Stroke(width = w * 0.009f),
                )
                val style = TextStyle(
                    color = if (isRed) Color(0xFFB91C1C) else Color.Black,
                    fontSize = (cardH * 0.58f).toSp(),
                    fontWeight = FontWeight.Black,
                )
                val layout = measurer.measure(label, style)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x + (cardW - layout.size.width) / 2f,
                        y + (cardH - layout.size.height) / 2f,
                    ),
                )
            }

            // Four unders (face down), subtly offset downward for layering depth.
            for (i in 0 until cardCount) {
                drawCardBack(cardX(i), cardRowTop + h * 0.03f)
            }

            // Four overs: 2 (red), 5 (black), 10 (black), K (red).
            val overLabels = listOf("2" to true, "5" to false, "10" to false, "K" to true)
            for (i in 0 until cardCount) {
                val (label, red) = overLabels[i]
                drawCardFront(cardX(i), cardRowTop - h * 0.02f, label, isRed = red)
            }

            // Oversized four-leaf clover crests the cards and overlaps their
            // upper third for the "crowning" effect.
            val cloverCenter = Offset(w / 2f, h * 0.38f)
            val leafRadius = w * 0.20f

            val stem = Path().apply {
                moveTo(cloverCenter.x - leafRadius * 0.1f, cloverCenter.y + leafRadius * 0.55f)
                quadraticTo(
                    cloverCenter.x + leafRadius * 0.55f,
                    cloverCenter.y + leafRadius * 1.35f,
                    cloverCenter.x + leafRadius * 0.05f,
                    cloverCenter.y + leafRadius * 2.05f,
                )
            }
            drawPath(stem, color = Color(0xFF1B5E20), style = Stroke(width = w * 0.020f))

            drawCloverLeaf(cloverCenter, leafRadius, angleDeg = -135f)
            drawCloverLeaf(cloverCenter, leafRadius, angleDeg = -45f)
            drawCloverLeaf(cloverCenter, leafRadius, angleDeg = 45f)
            drawCloverLeaf(cloverCenter, leafRadius, angleDeg = 135f)

            drawCircle(
                color = Color(0xFF1B5E20),
                radius = leafRadius * 0.28f,
                center = cloverCenter,
            )
        }
    }
}

private fun DrawScope.drawCloverLeaf(
    center: Offset,
    leafRadius: Float,
    angleDeg: Float,
) {
    val rad = Math.toRadians(angleDeg.toDouble())
    val dx = leafRadius * 0.70f * kotlin.math.cos(rad).toFloat()
    val dy = leafRadius * 0.70f * kotlin.math.sin(rad).toFloat()
    translate(left = dx, top = dy) {
        drawCircle(
            color = Color(0xFF2E7D32),
            radius = leafRadius,
            center = center,
        )
        drawCircle(
            color = Color(0xFF66BB6A),
            radius = leafRadius * 0.55f,
            center = Offset(
                center.x - leafRadius * 0.22f * kotlin.math.cos(rad).toFloat(),
                center.y - leafRadius * 0.22f * kotlin.math.sin(rad).toFloat(),
            ),
        )
    }
}

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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Scum logo: a gold crown resting on three fanned playing cards. The crown
 * sits on top of the card fan, representing the climb from Scum to King.
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
            val midX = w / 2f

            // ── Fanned cards (drawn first so crown sits on top) ──────────────────
            val cardW = w * 0.38f
            val cardH = cardW * 1.4f            // standard playing-card proportions
            val cardRadius = CornerRadius(cardW * 0.13f)
            // Rotation pivot far below the canvas → natural hand-of-cards fan
            val fanPivot = Offset(midX, h * 1.30f)
            val cardTopY = h * 0.46f

            val fanAngles = listOf(-21f, 0f, 21f)
            val fanCards = listOf("K" to false, "A" to true, "2" to false)

            // Draw back-to-front so the center card ends on top
            for (idx in listOf(0, 2, 1)) {
                withTransform({ rotate(fanAngles[idx], pivot = fanPivot) }) {
                    val cx = midX - cardW / 2f
                    val (label, isRed) = fanCards[idx]

                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(cx, cardTopY),
                        size = Size(cardW, cardH),
                        cornerRadius = cardRadius,
                    )
                    drawRoundRect(
                        color = Color(0xFF0B6A3A),
                        topLeft = Offset(cx, cardTopY),
                        size = Size(cardW, cardH),
                        cornerRadius = cardRadius,
                        style = Stroke(width = w * 0.009f),
                    )
                    val style = TextStyle(
                        color = if (isRed) Color(0xFFB91C1C) else Color.Black,
                        fontSize = (cardH * 0.50f).toSp(),
                        fontWeight = FontWeight.Black,
                    )
                    val layout = measurer.measure(label, style)
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(
                            cx + (cardW - layout.size.width) / 2f,
                            cardTopY + (cardH - layout.size.height) / 2f,
                        ),
                    )
                }
            }

            // ── Crown (drawn on top of the cards) ────────────────────────────────
            val cl = w * 0.14f
            val cr = w * 0.86f
            val cw = cr - cl
            val bandTop = h * 0.38f     // base of the spikes
            val bandBot = h * 0.54f     // bottom of the crown band

            val crown = Path().apply {
                moveTo(cl, bandBot)
                lineTo(cl, bandTop)
                // Left spike
                lineTo(cl + cw * 0.13f, h * 0.16f)
                // Left valley
                lineTo(cl + cw * 0.35f, bandTop)
                // Center spike — tallest
                lineTo(midX, h * 0.05f)
                // Right valley
                lineTo(cl + cw * 0.65f, bandTop)
                // Right spike
                lineTo(cr - cw * 0.13f, h * 0.16f)
                lineTo(cr, bandTop)
                lineTo(cr, bandBot)
                close()
            }

            // Crown fill
            drawPath(crown, color = Color(0xFFE6B54A))
            // Raised-band highlight inside the band area
            drawRoundRect(
                color = Color(0xFFCB9C0E),
                topLeft = Offset(cl + cw * 0.04f, bandTop + h * 0.012f),
                size = Size(cw * 0.92f, (bandBot - bandTop) * 0.70f),
                cornerRadius = CornerRadius(h * 0.016f),
            )
            // Crown outline
            drawPath(crown, color = Color(0xFF7C5E12), style = Stroke(width = w * 0.018f))

            // Orbs on spike tips
            val orbY = floatArrayOf(h * 0.05f, h * 0.16f, h * 0.16f)
            val orbX = floatArrayOf(midX, cl + cw * 0.13f, cr - cw * 0.13f)
            val orbR = floatArrayOf(w * 0.036f, w * 0.028f, w * 0.028f)
            for (i in 0..2) {
                drawCircle(Color(0xFFE6B54A), radius = orbR[i], center = Offset(orbX[i], orbY[i]))
                drawCircle(Color(0xFF7C5E12), radius = orbR[i], center = Offset(orbX[i], orbY[i]), style = Stroke(width = w * 0.012f))
                // Highlight on each orb
                drawCircle(Color(0xFFFFE88A), radius = orbR[i] * 0.38f, center = Offset(orbX[i] - orbR[i] * 0.22f, orbY[i] - orbR[i] * 0.22f))
            }

            // Jewels in the crown band
            val jewelY = bandTop + (bandBot - bandTop) * 0.50f
            // Center: ruby
            drawCircle(Color(0xFFDC2626), radius = w * 0.040f, center = Offset(midX, jewelY))
            drawCircle(Color(0xFFFF6B6B), radius = w * 0.016f, center = Offset(midX - w * 0.010f, jewelY - w * 0.013f))
            // Left: sapphire
            drawCircle(Color(0xFF1D4ED8), radius = w * 0.028f, center = Offset(cl + cw * 0.26f, jewelY))
            drawCircle(Color(0xFF93C5FD), radius = w * 0.010f, center = Offset(cl + cw * 0.255f, jewelY - w * 0.009f))
            // Right: sapphire
            drawCircle(Color(0xFF1D4ED8), radius = w * 0.028f, center = Offset(cl + cw * 0.74f, jewelY))
            drawCircle(Color(0xFF93C5FD), radius = w * 0.010f, center = Offset(cl + cw * 0.735f, jewelY - w * 0.009f))
        }
    }
}

package com.sridhar.harbor.ui.ai

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.sridhar.harbor.ui.components.reducedMotion
import kotlin.math.PI
import kotlin.math.sin

/**
 * "Jelly", the assistant mascot: a little jellyfish that bobs, blinks and waves its tentacles. While [busy]
 * (thinking / downloading) its eyes look up, tentacles swirl faster and a sparkle orbits it. One Canvas, no images.
 * Reduced-motion devices get the still pose.
 */
@Composable
fun JellyBuddy(size: Dp, busy: Boolean = false, modifier: Modifier = Modifier) {
    val still = reducedMotion()
    val t = rememberInfiniteTransition(label = "jelly")
    val bob by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (busy) 900 else 2200, easing = LinearEasing)), label = "bob")
    val wave by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (busy) 700 else 1600, easing = LinearEasing)), label = "wave")
    val blink by t.animateFloat(1f, 1f, infiniteRepeatable(keyframes { durationMillis = 4200; 1f at 3900; 0.1f at 4000; 1f at 4120 }, RepeatMode.Restart), label = "blink")
    val orbit by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "orbit")
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 100f
        val dy = if (still) 0f else sin(bob * 2 * PI).toFloat() * 3f * s
        val ph = if (still) 0f else wave * 2 * PI.toFloat()
        // tile
        drawCircle(Brush.linearGradient(listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))), radius = 50 * s)
        // tentacles
        listOf(36f, 50f, 64f).forEachIndexed { i, x ->
            val sway = sin(ph + i * 1.3f) * 5f * s
            val path = Path().apply {
                moveTo(x * s, 58 * s + dy)
                cubicTo((x - 6) * s + sway, 68 * s + dy, (x + 6) * s - sway, 74 * s + dy, x * s + sway * 0.6f, 84 * s + dy)
            }
            drawPath(path, Color(0xFF8EC5FF), style = Stroke(width = 5 * s, cap = StrokeCap.Round))
        }
        // bell
        val bell = Brush.verticalGradient(listOf(Color(0xFF8B5CF6), Color(0xFF38BDF8)), startY = 18 * s + dy, endY = 62 * s + dy)
        drawArc(bell, 180f, 180f, true, Offset(22 * s, 18 * s + dy), Size(56 * s, 68 * s))
        drawRect(bell, Offset(22 * s, 51.5f * s + dy), Size(56 * s, 8.5f * s))
        drawOval(Color.White.copy(alpha = .33f), Offset(32 * s, 24 * s + dy), Size(14 * s, 8 * s))
        // eyes (blink) – look up while thinking
        val look = if (busy) -2.2f * s else 0f
        listOf(41f, 59f).forEach { ex ->
            drawOval(Color.White, Offset((ex - 6) * s, (44 - 6 * blink) * s + dy), Size(12 * s, 12 * s * blink))
            if (blink > 0.4f) drawCircle(Color(0xFF0F172A), 3 * s, Offset((ex + 1) * s, 45 * s + dy + look))
        }
        drawArc(Color(0xFF0F172A), 20f, 140f, false, Offset(44 * s, 47 * s + dy), Size(12 * s, 9 * s), style = Stroke(2.6f * s, cap = StrokeCap.Round))
        // thinking sparkle
        if (busy && !still) {
            val a = Math.toRadians(orbit.toDouble())
            val c = Offset(50 * s + (38 * s * kotlin.math.cos(a)).toFloat(), 50 * s + (38 * s * kotlin.math.sin(a)).toFloat())
            drawCircle(Color(0xFFFDE68A), 3.2f * s, c)
            drawCircle(Color(0x55FDE68A), 6f * s, c)
        }
    }
}

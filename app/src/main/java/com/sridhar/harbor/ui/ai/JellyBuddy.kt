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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import com.sridhar.harbor.ui.components.reducedMotion
import kotlin.math.PI
import kotlin.math.sin

/**
 * The assistant mascot – a Shih Tzu with top-knot pigtails ([PetArt]). It blinks, bobs, wiggles its pigtails and
 * tilts its head; while [busy] (thinking / downloading) it pants with its tongue out and the pigtails bounce faster.
 * Reduced-motion devices get the still pose.
 */
@Composable
fun JellyBuddy(size: Dp, busy: Boolean = false, modifier: Modifier = Modifier) {
    val still = reducedMotion()
    val t = rememberInfiniteTransition(label = "pet")
    val bob by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (busy) 700 else 2400, easing = LinearEasing)), label = "bob")
    val wag by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (busy) 520 else 1500, easing = LinearEasing)), label = "wag")
    val blink by t.animateFloat(1f, 1f, infiniteRepeatable(keyframes { durationMillis = 4600; 1f at 4250; 0f at 4350; 1f at 4480 }, RepeatMode.Restart), label = "blink")
    val tiltPhase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "tilt")
    Canvas(modifier.size(size)) {
        val tw = 2 * PI.toFloat()
        // A curious head tilt now and then (only in the first part of each 7 s cycle).
        val tilt = if (still) 0f else if (tiltPhase < 0.18f) sin(tiltPhase / 0.18f * PI.toFloat()) * 9f else 0f
        drawIntoCanvas { c ->
            PetArt.draw(
                c.nativeCanvas, this.size.minDimension,
                open = if (still) 1f else blink,
                sway = if (still) 0f else sin(wag * tw),
                bob = if (still) 0f else sin(bob * tw) * (if (busy) 1.6f else 1.1f),
                tilt = tilt,
                tongue = if (busy && !still) 0.65f + 0.35f * sin(bob * tw * 2) else 0f,
            )
        }
    }
}

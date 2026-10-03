package com.sridhar.harbor.radio

import android.content.Context
import android.graphics.Bitmap

/**
 * The assistant mascot (a Shih Tzu, see [com.sridhar.harbor.ui.ai.PetArt]) as a bitmap for notifications – Android
 * doesn't animate notification images; the in-app version, [com.sridhar.harbor.ui.ai.JellyBuddy], is animated.
 */
object Avatar {
    private var cached: Bitmap? = null

    fun bitmap(ctx: Context): Bitmap = cached ?: draw((96 * ctx.resources.displayMetrics.density).toInt()).also { cached = it }

    fun draw(px: Int): Bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also {
        com.sridhar.harbor.ui.ai.PetArt.draw(android.graphics.Canvas(it), px.toFloat())
    }
}

package com.sridhar.harbor.tile

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.sridhar.harbor.HarborApp
import com.sridhar.harbor.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick Settings tile: qBittorrent alternative ("turtle") speed on/off. */
class TurtleTileService : TileService() {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val qbit get() = (application as HarborApp).container.qbit

    override fun onStartListening() {
        scope.launch { render(runCatching { qbit.altSpeedEnabled() }.getOrNull()) }
    }

    override fun onClick() {
        scope.launch {
            val now = runCatching { qbit.toggleAltSpeed(); qbit.altSpeedEnabled() }.getOrNull()
            render(now)
        }
    }

    private fun render(on: Boolean?) {
        val t = qsTile ?: return
        t.icon = Icon.createWithResource(this, R.drawable.ic_turtle)
        t.label = getString(R.string.tile_turtle)
        // Tile subtitles only exist on Android 10+; calling it on 8/9 crashes the tile.
        if (android.os.Build.VERSION.SDK_INT >= 29) t.subtitle = getString(when (on) { true -> R.string.tile_slowed; false -> R.string.tile_full_speed; null -> R.string.tile_offline })
        t.state = when (on) { true -> Tile.STATE_ACTIVE; false -> Tile.STATE_INACTIVE; null -> Tile.STATE_UNAVAILABLE }
        t.updateTile()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

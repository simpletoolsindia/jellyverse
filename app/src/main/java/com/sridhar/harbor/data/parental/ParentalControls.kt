package com.sridhar.harbor.data.parental

import android.content.Context
import com.sridhar.harbor.data.jellyfin.BaseItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

/** Which content ratings count as adults-only (18+). Accepts plain ("R") and country-prefixed ("IN-A", "DE:18") forms. */
object Ratings {
    private val adult = setOf("R", "NC-17", "NC17", "X", "XXX", "AO", "TV-MA", "A", "S", "18", "18+", "R18", "R18+", "FSK 18", "FSK18", "RP18")

    fun isAdult(rating: String?): Boolean {
        val r = rating?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return false
        if (r in adult) return true
        // Strip a country prefix: "IN-A" → "A", "DE:18" → "18" (but keep "NC-17" / "TV-MA" intact above).
        val core = Regex("^[A-Z]{2}[-:]\\s*(.+)$").find(r)?.groupValues?.get(1)?.trim() ?: r
        return core in adult || Regex("(^|[^0-9])18\\+?$").containsMatchIn(core)
    }
}

data class ParentalState(val enabled: Boolean = false, val protectAdult: Boolean = true, val locked: Set<String> = emptySet(),
    /** Whether the 🔞 18+ entry is shown in the menu (hidden = only reachable after unhiding it in settings). */
    val showMenu: Boolean = true)

/**
 * Parental control: a 4-digit PIN (stored only as a salted hash), 18+ titles kept off Home and behind the PIN,
 * and per-title locks. A correct PIN unlocks for a short while so families aren't asked on every tap.
 */
class ParentalControls(context: Context) {
    private val prefs = context.getSharedPreferences("harbor_parental", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<ParentalState> = _state.asStateFlow()
    @Volatile private var unlockedUntil = 0L

    private fun read() = ParentalState(
        enabled = prefs.getString("pin", null) != null,
        protectAdult = prefs.getBoolean("protect_adult", true),
        locked = prefs.getStringSet("locked", emptySet()).orEmpty(),
        showMenu = prefs.getBoolean("show_menu", true),
    )
    private fun publish() { _state.value = read() }

    private fun hash(pin: String) = MessageDigest.getInstance("SHA-256").digest("jellyverse-parental:$pin".toByteArray()).joinToString("") { "%02x".format(it) }

    fun setPin(pin: String) { require(pin.length == 4 && pin.all(Char::isDigit)); prefs.edit().putString("pin", hash(pin)).apply(); unlock(); publish() }
    fun clearPin() { prefs.edit().remove("pin").remove("locked").apply(); unlockedUntil = 0; publish() }
    fun setShowMenu(on: Boolean) { prefs.edit().putBoolean("show_menu", on).apply(); publish() }
    fun setProtectAdult(on: Boolean) { prefs.edit().putBoolean("protect_adult", on).apply(); publish() }

    /** Checks a PIN; on success everything stays unlocked for [UNLOCK_MS]. */
    fun verify(pin: String): Boolean = (prefs.getString("pin", null) == hash(pin)).also { if (it) unlock() }
    private fun unlock() { unlockedUntil = System.currentTimeMillis() + UNLOCK_MS }
    fun isUnlocked() = System.currentTimeMillis() < unlockedUntil
    fun relock() { unlockedUntil = 0 }

    fun isLocked(id: String?) = id != null && id in _state.value.locked
    fun setLocked(id: String, locked: Boolean) {
        val set = _state.value.locked.toMutableSet().apply { if (locked) add(id) else remove(id) }
        prefs.edit().putStringSet("locked", set).apply(); publish()
    }

    /** Series rated 18+ – episodes usually carry no rating of their own, so they inherit it from here. */
    @Volatile var adultSeries: Set<String> = emptySet()

    /** Adult or individually locked – kept off Home and shown only in the protected menu. */
    fun isProtected(item: BaseItem): Boolean {
        val s = _state.value
        if (!s.enabled) return false
        return isLocked(item.id) || isLocked(item.seriesId) ||
            (s.protectAdult && (Ratings.isAdult(item.officialRating) || item.seriesId in adultSeries))
    }

    /** Refreshes [adultSeries] from the library (cheap; called when Home loads with protection on). */
    suspend fun refreshAdultSeries(titles: suspend () -> List<BaseItem>) {
        if (!_state.value.enabled) return
        runCatching { titles() }.onSuccess { list -> adultSeries = list.filter { it.type == "Series" && Ratings.isAdult(it.officialRating) }.map { it.id }.toSet() }
    }

    fun hideFromHome(item: BaseItem) = isProtected(item)
    fun needsPin(item: BaseItem) = isProtected(item) && !isUnlocked()

    companion object { const val UNLOCK_MS = 15 * 60_000L }
}

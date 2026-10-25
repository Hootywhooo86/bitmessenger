package app.bitmessenger.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** Every user-tweakable option in one immutable snapshot. */
data class BitSettings(
    // Look & feel (same names and meaning as bitcall, so theme codes work in both apps).
    val palette: String = "PIXEL_NIGHT",
    val colorOverrides: Map<String, Long> = emptyMap(), // ColorSlot name -> ARGB
    val keyColors: Map<Int, Long> = emptyMap(),         // kept so bitcall theme codes round-trip
    val corners: String = "PIXEL",    // PIXEL | ROUND | SQUARE
    val cardSize: String = "BIG",     // COMPACT | NORMAL | BIG | HUGE
    val pixelDetail: Int = 32,
    val textScale: Float = 1f,
    val crt: Boolean = true,
    val keyLetters: Boolean = true,
    val haptics: Boolean = true,
    val wallpaper: Long? = null,                        // whole-app chat wallpaper colour
    val chatWallpapers: Map<Long, Long> = emptyMap(),   // thread id -> wallpaper colour
    val sounds: Boolean = true,
    // Conversations
    val pinned: Set<Long> = emptySet(),
    val archived: Set<Long> = emptySet(),
    val trashed: Set<Long> = emptySet(),
    val spam: Set<Long> = emptySet(),
    val spamBannerHiddenAt: Long = 0L,
    // Bitter
    val bitterBrain: String = "AUTO",  // AUTO | FAMILY | CLAUDE | OFFLINE
    val claudeKey: String = "",
    val bitterName: String = "Bitter",
)

object Settings {
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(BitSettings())
    val state: StateFlow<BitSettings> = _state
    val now: BitSettings get() = _state.value

    fun init(context: Context) {
        prefs = context.getSharedPreferences("bitmessenger", Context.MODE_PRIVATE)
        _state.value = load()
    }

    fun update(block: (BitSettings) -> BitSettings) {
        val next = block(_state.value)
        _state.value = next
        if (::prefs.isInitialized) save(next)
    }

    private fun longMap(key: String): Map<Long, Long>? = prefs.getString(key, null)?.let { j ->
        JSONObject(j).let { o -> o.keys().asSequence().associate { it.toLong() to o.getLong(it) } }
    }

    private fun longSet(key: String): Set<Long> = prefs.getStringSet(key, emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()

    private fun load(): BitSettings {
        val d = BitSettings()
        return BitSettings(
            palette = prefs.getString("palette", d.palette)!!,
            colorOverrides = prefs.getString("colorOverrides", null)?.let { j ->
                JSONObject(j).let { o -> o.keys().asSequence().associateWith { o.getLong(it) } }
            } ?: d.colorOverrides,
            keyColors = prefs.getString("keyColors", null)?.let { j ->
                JSONObject(j).let { o -> o.keys().asSequence().associate { it.toInt() to o.getLong(it) } }
            } ?: d.keyColors,
            corners = prefs.getString("corners", d.corners)!!,
            cardSize = prefs.getString("cardSize", d.cardSize)!!,
            pixelDetail = prefs.getInt("pixelDetail", d.pixelDetail),
            textScale = prefs.getFloat("textScale", d.textScale),
            crt = prefs.getBoolean("crt", d.crt),
            keyLetters = prefs.getBoolean("keyLetters", d.keyLetters),
            haptics = prefs.getBoolean("haptics", d.haptics),
            wallpaper = if (prefs.contains("wallpaper")) prefs.getLong("wallpaper", 0) else null,
            chatWallpapers = longMap("chatWallpapers") ?: d.chatWallpapers,
            sounds = prefs.getBoolean("sounds", d.sounds),
            pinned = longSet("pinned"),
            archived = longSet("archived"),
            trashed = longSet("trashed"),
            spam = longSet("spam"),
            spamBannerHiddenAt = prefs.getLong("spamBannerHiddenAt", 0L),
            bitterBrain = prefs.getString("bitterBrain", d.bitterBrain)!!,
            claudeKey = prefs.getString("claudeKey", d.claudeKey)!!,
            bitterName = prefs.getString("bitterName", d.bitterName)!!,
        )
    }

    private fun save(s: BitSettings) {
        prefs.edit()
            .putString("palette", s.palette)
            .putString("colorOverrides", JSONObject(s.colorOverrides).toString())
            .putString("keyColors", JSONObject(s.keyColors.mapKeys { it.key.toString() }).toString())
            .putString("corners", s.corners)
            .putString("cardSize", s.cardSize)
            .putInt("pixelDetail", s.pixelDetail)
            .putFloat("textScale", s.textScale)
            .putBoolean("crt", s.crt)
            .putBoolean("keyLetters", s.keyLetters)
            .putBoolean("haptics", s.haptics)
            .apply { if (s.wallpaper != null) putLong("wallpaper", s.wallpaper) else remove("wallpaper") }
            .putString("chatWallpapers", JSONObject(s.chatWallpapers.mapKeys { it.key.toString() }).toString())
            .putBoolean("sounds", s.sounds)
            .putStringSet("pinned", s.pinned.map { it.toString() }.toSet())
            .putStringSet("archived", s.archived.map { it.toString() }.toSet())
            .putStringSet("trashed", s.trashed.map { it.toString() }.toSet())
            .putStringSet("spam", s.spam.map { it.toString() }.toSet())
            .putLong("spamBannerHiddenAt", s.spamBannerHiddenAt)
            .putString("bitterBrain", s.bitterBrain)
            .putString("claudeKey", s.claudeKey)
            .putString("bitterName", s.bitterName)
            .apply()
    }
}

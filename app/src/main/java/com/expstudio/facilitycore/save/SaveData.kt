package com.expstudio.facilitycore.save

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One save slot. A world owns a chapter and its progress inside that chapter. */
data class WorldSave(
    val id: String,
    var name: String,
    var chapter: Int,
    var stage: Int,
    var checkpoint: Int,
    var playSeconds: Long,
    var createdAt: Long,
    var lastPlayedAt: Long,
    var completed: Boolean
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("chapter", chapter)
        put("stage", stage)
        put("checkpoint", checkpoint)
        put("playSeconds", playSeconds)
        put("createdAt", createdAt)
        put("lastPlayedAt", lastPlayedAt)
        put("completed", completed)
    }

    companion object {
        fun fromJson(o: JSONObject): WorldSave = WorldSave(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", "World"),
            chapter = o.optInt("chapter", 1).coerceIn(1, 2),
            stage = o.optInt("stage", 0).coerceAtLeast(0),
            checkpoint = o.optInt("checkpoint", 0).coerceAtLeast(0),
            playSeconds = o.optLong("playSeconds", 0L).coerceAtLeast(0L),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            lastPlayedAt = o.optLong("lastPlayedAt", System.currentTimeMillis()),
            completed = o.optBoolean("completed", false)
        )
    }
}

/** Device-wide options plus the cross-world unlocks. */
class Settings(private val prefs: SharedPreferences) {
    var sfxEnabled: Boolean
        get() = prefs.getBoolean("sfx", true)
        set(v) { prefs.edit().putBoolean("sfx", v).apply() }

    var ambienceEnabled: Boolean
        get() = prefs.getBoolean("ambience", true)
        set(v) { prefs.edit().putBoolean("ambience", v).apply() }

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(v) { prefs.edit().putBoolean("haptics", v).apply() }

    var showFps: Boolean
        get() = prefs.getBoolean("showFps", false)
        set(v) { prefs.edit().putBoolean("showFps", v).apply() }

    /** 0.8 .. 1.3, applied to the virtual gamepad. */
    var controlScale: Float
        get() = prefs.getFloat("controlScale", 1f).coerceIn(0.8f, 1.3f)
        set(v) { prefs.edit().putFloat("controlScale", v.coerceIn(0.8f, 1.3f)).apply() }

    /** Set once any world finishes Chapter 1; gates Chapter 2 world creation. */
    var chapter2Unlocked: Boolean
        get() = prefs.getBoolean("ch2Unlocked", false)
        set(v) { prefs.edit().putBoolean("ch2Unlocked", v).apply() }
}

/**
 * Persists the world list as a single JSON blob. The list is small (20 slots at
 * most) so rewriting it wholesale is simpler and safer than keyed entries.
 */
class WorldStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("facility_core", Context.MODE_PRIVATE)

    val settings = Settings(prefs)

    fun list(): MutableList<WorldSave> {
        val raw = prefs.getString(KEY_WORLDS, null) ?: return ArrayList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<WorldSave>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(WorldSave.fromJson(o))
            }
            out.sortByDescending { it.lastPlayedAt }
            out
        } catch (t: Throwable) {
            // A corrupt blob must not brick the menu; start clean instead.
            ArrayList()
        }
    }

    fun isFull(): Boolean = list().size >= MAX_SLOTS

    fun nameTaken(name: String, exceptId: String? = null): Boolean =
        list().any { it.name.equals(name.trim(), ignoreCase = true) && it.id != exceptId }

    fun create(name: String, chapter: Int): WorldSave? {
        val worlds = list()
        if (worlds.size >= MAX_SLOTS) return null
        val clean = sanitize(name)
        if (clean.isEmpty()) return null
        if (worlds.any { it.name.equals(clean, ignoreCase = true) }) return null
        val now = System.currentTimeMillis()
        val world = WorldSave(
            id = UUID.randomUUID().toString(),
            name = clean,
            chapter = chapter.coerceIn(1, 2),
            stage = 0,
            checkpoint = 0,
            playSeconds = 0L,
            createdAt = now,
            lastPlayedAt = now,
            completed = false
        )
        worlds.add(world)
        persist(worlds)
        return world
    }

    fun rename(id: String, name: String): Boolean {
        val clean = sanitize(name)
        if (clean.isEmpty()) return false
        val worlds = list()
        if (worlds.any { it.name.equals(clean, ignoreCase = true) && it.id != id }) return false
        val target = worlds.firstOrNull { it.id == id } ?: return false
        target.name = clean
        persist(worlds)
        return true
    }

    fun delete(id: String) {
        val worlds = list()
        if (worlds.removeAll { it.id == id }) persist(worlds)
    }

    fun duplicate(id: String): WorldSave? {
        val worlds = list()
        if (worlds.size >= MAX_SLOTS) return null
        val src = worlds.firstOrNull { it.id == id } ?: return null
        var candidate = sanitize("${src.name} copy")
        var n = 2
        while (worlds.any { it.name.equals(candidate, ignoreCase = true) }) {
            candidate = sanitize("${src.name} copy $n")
            n++
            if (n > 50) return null
        }
        val now = System.currentTimeMillis()
        val copy = src.copy(id = UUID.randomUUID().toString(), name = candidate, createdAt = now, lastPlayedAt = now)
        worlds.add(copy)
        persist(worlds)
        return copy
    }

    fun get(id: String): WorldSave? = list().firstOrNull { it.id == id }

    fun update(world: WorldSave) {
        val worlds = list()
        val idx = worlds.indexOfFirst { it.id == world.id }
        if (idx < 0) return
        worlds[idx] = world
        persist(worlds)
        if (world.completed && world.chapter == 1) settings.chapter2Unlocked = true
    }

    private fun persist(worlds: List<WorldSave>) {
        val arr = JSONArray()
        worlds.take(MAX_SLOTS).forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_WORLDS, arr.toString()).apply()
    }

    /** Trims, collapses whitespace and caps length so the list stays readable. */
    private fun sanitize(name: String): String =
        name.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH)

    companion object {
        const val MAX_SLOTS = 20
        const val MAX_NAME_LENGTH = 24
        private const val KEY_WORLDS = "worlds_v1"
    }
}

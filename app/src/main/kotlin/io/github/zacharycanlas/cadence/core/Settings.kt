package io.github.zacharycanlas.cadence.core

/** Typed key-value storage, so SharedPreferences in the app and a map in tests stand behind the same rules. */
interface SettingsStorage {
    fun getInt(key: String, default: Int): Int
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getString(key: String, default: String): String
    fun putInt(key: String, value: Int)
    fun putBoolean(key: String, value: Boolean)
    fun putString(key: String, value: String)
}

data class Settings(
    val bpm: Int,
    val volume: Int,
    val click: Boolean,
    val vibrate: Boolean,
    val voice: String,
    /** Beats between accents. 0 turns the accent off. */
    val accentEvery: Int,
    val rampOn: Boolean,
    val rampTo: Int,
    val rampMinutes: Int,
)

val DEFAULT_SETTINGS = Settings(
    bpm = DEFAULT_BPM,
    volume = 70,
    click = true,
    vibrate = false,
    voice = DEFAULT_VOICE,
    accentEvery = 0,
    rampOn = false,
    rampTo = 180,
    rampMinutes = 10,
)

/** Coerces anything stored (an old version, a hostile value) into a valid shape. */
fun normalize(settings: Settings): Settings {
    val normalized = settings.copy(
        bpm = clampBpm(settings.bpm),
        volume = settings.volume.coerceIn(0, 100),
        voice = if (settings.voice in VOICE_IDS) settings.voice else DEFAULT_VOICE,
        accentEvery = normalizeAccent(settings.accentEvery),
        rampTo = clampBpm(settings.rampTo),
        rampMinutes = clampMinutes(settings.rampMinutes),
    )
    // Silence on both channels would look like a broken app, so keep the click.
    return if (!normalized.click && !normalized.vibrate) normalized.copy(click = true) else normalized
}

/** Each key reads independently: one that throws, or holds the wrong type, falls back to its default alone. */
fun loadSettings(storage: SettingsStorage): Settings {
    fun <T> read(get: () -> T, default: T): T = try {
        get()
    } catch (_: RuntimeException) {
        default
    }
    val d = DEFAULT_SETTINGS
    return normalize(
        Settings(
            bpm = read({ storage.getInt("bpm", d.bpm) }, d.bpm),
            volume = read({ storage.getInt("volume", d.volume) }, d.volume),
            click = read({ storage.getBoolean("click", d.click) }, d.click),
            vibrate = read({ storage.getBoolean("vibrate", d.vibrate) }, d.vibrate),
            voice = read({ storage.getString("voice", d.voice) }, d.voice),
            accentEvery = read({ storage.getInt("accentEvery", d.accentEvery) }, d.accentEvery),
            rampOn = read({ storage.getBoolean("rampOn", d.rampOn) }, d.rampOn),
            rampTo = read({ storage.getInt("rampTo", d.rampTo) }, d.rampTo),
            rampMinutes = read({ storage.getInt("rampMinutes", d.rampMinutes) }, d.rampMinutes),
        ),
    )
}

/** Returns false when the store refuses the write; the app carries on with the settings in memory. */
fun saveSettings(storage: SettingsStorage, settings: Settings): Boolean = try {
    val s = normalize(settings)
    storage.putInt("bpm", s.bpm)
    storage.putInt("volume", s.volume)
    storage.putBoolean("click", s.click)
    storage.putBoolean("vibrate", s.vibrate)
    storage.putString("voice", s.voice)
    storage.putInt("accentEvery", s.accentEvery)
    storage.putBoolean("rampOn", s.rampOn)
    storage.putInt("rampTo", s.rampTo)
    storage.putInt("rampMinutes", s.rampMinutes)
    true
} catch (_: RuntimeException) {
    false
}

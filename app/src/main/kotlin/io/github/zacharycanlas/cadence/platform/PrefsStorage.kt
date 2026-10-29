package io.github.zacharycanlas.cadence.platform

import android.content.Context
import io.github.zacharycanlas.cadence.core.SettingsStorage

/** SharedPreferences throws ClassCastException for a key read as the wrong type; loadSettings turns that into the default. */
class PrefsStorage(context: Context) : SettingsStorage {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun getString(key: String, default: String): String = prefs.getString(key, default) ?: default

    override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
}

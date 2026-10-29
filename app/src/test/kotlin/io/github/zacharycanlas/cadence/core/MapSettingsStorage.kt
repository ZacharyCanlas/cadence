package io.github.zacharycanlas.cadence.core

/**
 * Behaves as SharedPreferences does: reading a key through the wrong type
 * throws ClassCastException rather than converting.
 */
class MapSettingsStorage(val values: MutableMap<String, Any> = mutableMapOf()) : SettingsStorage {
    override fun getInt(key: String, default: Int): Int = (values[key] ?: return default) as Int
    override fun getBoolean(key: String, default: Boolean): Boolean = (values[key] ?: return default) as Boolean
    override fun getString(key: String, default: String): String = (values[key] ?: return default) as String

    override fun putInt(key: String, value: Int) {
        values[key] = value
    }

    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value
    }

    override fun putString(key: String, value: String) {
        values[key] = value
    }
}

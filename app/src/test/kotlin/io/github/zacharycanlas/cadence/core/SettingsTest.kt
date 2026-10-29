package io.github.zacharycanlas.cadence.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    @Test
    fun `defaults survive an empty store`() {
        assertEquals(DEFAULT_SETTINGS, loadSettings(MapSettingsStorage()))
    }

    @Test
    fun `a saved round trip returns what went in`() {
        val storage = MapSettingsStorage()
        val settings = Settings(
            bpm = 165,
            volume = 40,
            click = false,
            vibrate = true,
            voice = "woodblock",
            accentEvery = 3,
            rampOn = true,
            rampTo = 185,
            rampMinutes = 20,
        )
        assertTrue(saveSettings(storage, settings))
        assertEquals(settings, loadSettings(storage))
    }

    @Test
    fun `out-of-range values are clamped rather than trusted`() {
        val settings = normalize(DEFAULT_SETTINGS.copy(bpm = 400, volume = -20, rampMinutes = 999))
        assertEquals(220, settings.bpm)
        assertEquals(0, settings.volume)
        assertEquals(60, settings.rampMinutes)
    }

    @Test
    fun `an unknown sound falls back rather than leaving the app silent`() {
        assertEquals(DEFAULT_SETTINGS.voice, normalize(DEFAULT_SETTINGS.copy(voice = "trombone")).voice)
        assertEquals("kit", normalize(DEFAULT_SETTINGS.copy(voice = "kit")).voice)
    }

    @Test
    fun `an accent pattern outside the offered set is treated as off`() {
        assertEquals(0, normalize(DEFAULT_SETTINGS.copy(accentEvery = 5)).accentEvery)
        assertEquals(3, normalize(DEFAULT_SETTINGS.copy(accentEvery = 3)).accentEvery)
    }

    @Test
    fun `both output channels can never be off at once`() {
        assertTrue(normalize(DEFAULT_SETTINGS.copy(click = false, vibrate = false)).click)
    }

    @Test
    fun `hostile stored values are clamped on load`() {
        val storage = MapSettingsStorage(
            mutableMapOf("bpm" to Int.MAX_VALUE, "volume" to Int.MIN_VALUE, "voice" to "", "click" to false),
        )
        val settings = loadSettings(storage)
        assertEquals(220, settings.bpm)
        assertEquals(0, settings.volume)
        assertEquals(DEFAULT_SETTINGS.voice, settings.voice)
        assertTrue("vibrate defaults off, so the click is forced back on", settings.click)
    }

    @Test
    fun `a key stored with the wrong type reads as its default`() {
        val storage = MapSettingsStorage(
            mutableMapOf("bpm" to "175", "click" to "yes", "voice" to 3, "rampMinutes" to 20),
        )
        val settings = loadSettings(storage)
        assertEquals(DEFAULT_SETTINGS.bpm, settings.bpm)
        assertEquals(DEFAULT_SETTINGS.click, settings.click)
        assertEquals(DEFAULT_SETTINGS.voice, settings.voice)
        assertEquals("a well-typed key beside them still loads", 20, settings.rampMinutes)
    }

    @Test
    fun `a storage that throws does not take the app down`() {
        val hostile = object : SettingsStorage {
            override fun getInt(key: String, default: Int): Int = throw IllegalStateException("storage disabled")
            override fun getBoolean(key: String, default: Boolean): Boolean = throw IllegalStateException("storage disabled")
            override fun getString(key: String, default: String): String = throw IllegalStateException("storage disabled")
            override fun putInt(key: String, value: Int) = throw IllegalStateException("storage disabled")
            override fun putBoolean(key: String, value: Boolean) = throw IllegalStateException("storage disabled")
            override fun putString(key: String, value: String) = throw IllegalStateException("storage disabled")
        }
        assertEquals(DEFAULT_SETTINGS, loadSettings(hostile))
        assertFalse(saveSettings(hostile, DEFAULT_SETTINGS))
    }
}

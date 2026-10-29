package io.github.zacharycanlas.cadence.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TapTempoTest {
    /** Tap at a steady interval and return the last reading. */
    private fun tapSteady(tempo: TapTempo, intervalMs: Long, count: Int, startAt: Long = 1000): TapReading {
        var reading = TapReading(bpm = null, taps = 0)
        for (i in 0 until count) reading = tempo.tap(startAt + i * intervalMs)
        return reading
    }

    @Test
    fun `no reading until enough taps have landed`() {
        val tempo = TapTempo()
        assertNull(tempo.tap(1000).bpm)
        assertNull(tempo.tap(1333).bpm)
        assertNotNull(tapSteady(TapTempo(), 333, MIN_TAPS).bpm)
    }

    @Test
    fun `a steady tap reads back as the tempo tapped`() {
        assertEquals(180, tapSteady(TapTempo(), 333, 6).bpm)
    }

    @Test
    fun `the reading is clamped into the runnable range`() {
        assertEquals(220, tapSteady(TapTempo(), 50, 6).bpm)
        assertEquals(100, tapSteady(TapTempo(), 2000, 6).bpm)
    }

    @Test
    fun `a long pause starts a fresh measurement`() {
        val tempo = TapTempo()
        tapSteady(tempo, 500, 4)
        val afterPause = tempo.tap(1000 + 3 * 500 + TAP_TIMEOUT_MS + 1)
        assertNull("the stale intervals should have been dropped", afterPause.bpm)
        assertEquals(1, afterPause.taps)
    }

    @Test
    fun `only the most recent taps count, so a change is followed`() {
        val tempo = TapTempo(window = 4)
        var at = 1000L
        repeat(6) {
            tempo.tap(at)
            at += 500
        }
        repeat(6) {
            tempo.tap(at)
            at += 333
        }
        assertEquals("the older slow taps should have aged out", 180, tempo.tap(at).bpm)
    }

    @Test
    fun `reset clears the run of taps`() {
        val tempo = TapTempo()
        tapSteady(tempo, 333, 5)
        tempo.reset()
        assertEquals(0, tempo.taps)
        assertNull(tempo.tap(9999).bpm)
    }
}

package io.github.zacharycanlas.cadence.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RampTest {
    @Test
    fun `the ramp starts where you started`() {
        assertEquals(168, rampBpmAt(168, 180, 10, 0.0))
        assertEquals(168, rampBpmAt(168, 180, 10, -5.0))
    }

    @Test
    fun `the ramp finishes on the target and stays there`() {
        assertEquals(180, rampBpmAt(168, 180, 10, 600.0))
        assertEquals(180, rampBpmAt(168, 180, 10, 6000.0))
    }

    @Test
    fun `the ramp climbs evenly across its span`() {
        assertEquals(170, rampBpmAt(160, 180, 10, 300.0))
        assertEquals(165, rampBpmAt(160, 180, 10, 150.0))
    }

    @Test
    fun `a ramp can go down as well as up`() {
        assertEquals(175, rampBpmAt(185, 165, 10, 300.0))
        assertEquals(165, rampBpmAt(185, 165, 10, 600.0))
    }

    @Test
    fun `progress runs from nothing to complete and then holds`() {
        assertEquals(0.0, rampProgress(10, 0.0), 0.0)
        assertEquals(0.5, rampProgress(10, 300.0), 0.0)
        assertEquals(1.0, rampProgress(10, 600.0), 0.0)
        assertEquals(1.0, rampProgress(10, 900.0), 0.0)
        assertFalse(rampDone(10, 599.0))
        assertTrue(rampDone(10, 600.0))
    }

    @Test
    fun `a nonsense duration is clamped instead of dividing by zero`() {
        assertEquals(1, clampMinutes(0))
        assertEquals(1, clampMinutes(-4))
        assertEquals(60, clampMinutes(500))
        assertEquals("a zero-minute ramp runs over one minute", 175, rampBpmAt(170, 180, 0, 30.0))
    }
}

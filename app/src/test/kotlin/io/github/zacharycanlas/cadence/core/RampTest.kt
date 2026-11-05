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

    @Test
    fun `the phase reads held first, then holding once done or flat, then the direction`() {
        assertEquals(RampPhase.Held, rampPhase(held = true, from = 160, to = 180, minutes = 10, elapsedSeconds = 60.0))
        assertEquals(RampPhase.Climbing, rampPhase(held = false, from = 160, to = 180, minutes = 10, elapsedSeconds = 60.0))
        assertEquals(RampPhase.Easing, rampPhase(held = false, from = 180, to = 160, minutes = 10, elapsedSeconds = 60.0))
        assertEquals(RampPhase.Holding, rampPhase(held = false, from = 160, to = 180, minutes = 10, elapsedSeconds = 600.0))
        assertEquals("a ramp to where it starts has nothing to climb", RampPhase.Holding, rampPhase(held = false, from = 180, to = 180, minutes = 10, elapsedSeconds = 0.0))
    }
}

package io.github.zacharycanlas.cadence.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CadenceTest {
    private data class RampSettings(val rampTo: Int, val rampMinutes: Int)

    /** One step of the session clock, as the controller drives it. */
    private fun tick(cadence: Cadence, settings: RampSettings, elapsedSeconds: Double): Int {
        val target = cadence.rampTarget(settings.rampTo, settings.rampMinutes, elapsedSeconds)
        if (target != cadence.current) cadence.sound(target)
        return cadence.current
    }

    /** A whole run, ticked every half second. */
    private fun run(cadence: Cadence, settings: RampSettings, seconds: Double): Int {
        cadence.beginRun()
        var elapsed = 0.0
        while (elapsed <= seconds) {
            tick(cadence, settings, elapsed)
            elapsed += 0.5
        }
        return cadence.current
    }

    @Test
    fun `a ramp moves what is sounding without touching what was chosen`() {
        val cadence = Cadence(150)

        val ended = run(cadence, RampSettings(180, 10), 600.0)

        assertEquals("the ramp reached its target", 180, ended)
        assertEquals("and that is what is sounding", 180, cadence.current)
        assertEquals("but the cadence you chose is untouched", 150, cadence.chosen)
    }

    @Test
    fun `the ramp still climbs on the run after the one that finished it`() {
        // The defect this split exists for: saving each ramp step as the chosen
        // cadence leaves the next run climbing from the target to the target.
        val storage = MapSettingsStorage()
        saveSettings(storage, loadSettings(storage).copy(bpm = 150, rampOn = true, rampTo = 180, rampMinutes = 10))

        val first = loadSettings(storage)
        val firstRun = Cadence(first.bpm)
        assertEquals("the first run climbed", 180, run(firstRun, RampSettings(first.rampTo, first.rampMinutes), 600.0))
        saveSettings(storage, first.copy(bpm = firstRun.chosen))

        val second = loadSettings(storage)
        assertEquals("the saved cadence is still the one you chose", 150, second.bpm)

        val ramp = RampSettings(second.rampTo, second.rampMinutes)
        val secondRun = Cadence(second.bpm)
        secondRun.beginRun()
        assertEquals("the second run starts where the first did", 150, secondRun.current)
        assertEquals("and is halfway up at half time", 165, tick(secondRun, ramp, 300.0))
        assertEquals("and reaches the target again", 180, run(secondRun, ramp, 600.0))
    }

    @Test
    fun `a second run in the same session starts from your cadence, not the last target`() {
        val cadence = Cadence(150)

        run(cadence, RampSettings(180, 10), 600.0)
        assertEquals("the first run finished at the target", 180, cadence.current)

        cadence.beginRun()
        assertEquals("the second run opens at your cadence", 150, cadence.current)
        assertEquals("and climbs from there, not from 180", 150, cadence.rampFrom)
    }

    @Test
    fun `a deliberate change is the one thing that moves the chosen cadence`() {
        val cadence = Cadence(150)

        cadence.choose(168)

        assertEquals(168, cadence.chosen)
        assertEquals(168, cadence.current)
        assertEquals(168, cadence.rampFrom)
    }

    @Test
    fun `a change made mid-ramp climbs on from there, not from where the run began`() {
        val cadence = Cadence(150)

        cadence.beginRun()
        tick(cadence, RampSettings(190, 10), 300.0)
        assertEquals("halfway up", 170, cadence.current)

        cadence.choose(160)
        assertEquals("the ramp is rebased on the new choice", 160, cadence.rampFrom)
        assertEquals("and the new choice is what would be saved", 160, cadence.chosen)
    }

    @Test
    fun `ending a run puts the display back to the cadence you chose`() {
        val cadence = Cadence(150)

        run(cadence, RampSettings(180, 10), 600.0)
        assertEquals(180, cadence.current)

        cadence.endRun()
        assertEquals("the readout returns to your cadence", 150, cadence.current)
        assertEquals("which never moved", 150, cadence.chosen)
    }

    @Test
    fun `turning the ramp on mid-run climbs from where you actually are`() {
        val cadence = Cadence(150)

        cadence.beginRun()
        cadence.sound(162)
        cadence.rebaseRamp()

        assertEquals("the climb starts from the current cadence", 162, cadence.rampFrom)
        assertEquals("without rewriting what you chose", 150, cadence.chosen)
    }

    @Test
    fun `a ramp switched on mid-run climbs from that moment, not from the start of the run`() {
        val cadence = Cadence(165)
        cadence.beginRun()
        cadence.rebaseRamp(atSeconds = 15 * 60.0)

        assertEquals("no jump at the switch", 165, cadence.rampTarget(180, 10, 15 * 60.0))
        assertEquals("halfway five minutes later", 173, cadence.rampTarget(180, 10, 20 * 60.0))
        assertEquals("there ten minutes later", 180, cadence.rampTarget(180, 10, 25 * 60.0))

        cadence.beginRun()
        assertEquals("a new run climbs from its own start", 0.0, cadence.rampStartSeconds, 0.0)
    }

    @Test
    fun `a cadence out of range is clamped rather than trusted`() {
        val cadence = Cadence(9999)
        assertEquals(220, cadence.chosen)
        assertEquals(220, cadence.current)

        cadence.choose(10)
        assertEquals(100, cadence.chosen)

        cadence.sound(9999)
        assertEquals("the ramp cannot push past the range either", 220, cadence.current)

        cadence.hold(10)
        assertEquals("nor can a hold", 100, cadence.current)
    }

    @Test
    fun `hold sets what is sounding and leaves the choice and the climb alone`() {
        val cadence = Cadence(150)
        val ramp = RampSettings(180, 10)
        cadence.beginRun()
        tick(cadence, ramp, 300.0)

        cadence.hold(172)

        assertEquals(172, cadence.current)
        assertTrue(cadence.rampHeld)
        assertEquals(150, cadence.chosen)
        assertEquals(150, cadence.rampFrom)
        for (elapsed in listOf(301.0, 450.0, 600.0, 6000.0)) {
            assertEquals("held at every later elapsed time", 172, cadence.rampTarget(ramp.rampTo, ramp.rampMinutes, elapsed))
        }
    }

    @Test
    fun `a new run and a rebased ramp each clear the hold`() {
        val cadence = Cadence(150)
        cadence.beginRun()
        cadence.hold(160)
        cadence.beginRun()
        assertFalse("beginRun clears it", cadence.rampHeld)

        cadence.hold(165)
        cadence.rebaseRamp()
        assertFalse("rebaseRamp clears it", cadence.rampHeld)
        assertEquals("and climbs from where you are", 165, cadence.rampFrom)
    }

    @Test
    fun `a hold mid-ramp is forgotten at stop, and the next run climbs from your cadence`() {
        // A lock-screen -1 twenty minutes into a ramp means "slower now", not
        // "my cadence is 169". Saving it would start the next run near the target.
        val cadence = Cadence(150)
        val ramp = RampSettings(180, 30)
        cadence.beginRun()
        tick(cadence, ramp, 20 * 60.0)
        cadence.hold(cadence.current - 1)

        cadence.endRun()
        assertEquals(150, cadence.current)
        assertEquals(150, cadence.chosen)

        assertEquals("the next run opens at 150", 150, cadence.beginRun())
        assertEquals("and climbs again", 165, tick(cadence, ramp, 15 * 60.0))
    }

    @Test
    fun `choose never sets the hold`() {
        val cadence = Cadence(150)
        cadence.beginRun()
        cadence.choose(170)
        assertFalse(cadence.rampHeld)
    }
}

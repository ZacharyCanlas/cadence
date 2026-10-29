package io.github.zacharycanlas.cadence.run

import android.media.AudioManager
import android.media.AudioTrack
import io.github.zacharycanlas.cadence.core.CommittedBeat
import io.github.zacharycanlas.cadence.core.MapSettingsStorage
import io.github.zacharycanlas.cadence.core.saveSettings
import io.github.zacharycanlas.cadence.core.DEFAULT_SETTINGS
import io.github.zacharycanlas.cadence.core.Settings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class MetronomeControllerTest {
    private val rate = 48_000
    private val bufferNanos = 20_000_000L

    private inner class Rig(
        settings: Settings = DEFAULT_SETTINGS,
        hapticsAvailable: Boolean = true,
        mode: Int = AudioManager.MODE_NORMAL,
    ) {
        val events = mutableListOf<String>()
        val output = FakeOutput(rate, events = events)
        val haptics = FakeHaptics(hapticsAvailable).also { it.clock = { output.now } }
        val host = FakeHost(events).also { it.mode = mode }
        val storage = MapSettingsStorage().also { saveSettings(it, settings) }
        val scope = TestScope()
        val controller = MetronomeController(output, haptics, storage, host, scope, { output.now }, Random(5))

        /** Runs `seconds` of buffers, with the 500 ms tick firing in step. */
        fun play(seconds: Double) {
            val ticks = (seconds * 2).toInt()
            repeat(ticks) {
                output.step(25)
                scope.advanceTimeBy(500)
                scope.runCurrent()
            }
        }

        /** Runs buffers and drains after each one, recording when each step was counted. */
        fun playDraining(buffers: Int, counted: MutableList<Long>) {
            repeat(buffers) {
                output.step(1)
                val before = controller.state.value.steps
                controller.drain()
                repeat(controller.state.value.steps - before) { counted += output.now }
            }
        }
    }

    @Test
    fun `start configures the output, then starts the host, and stop undoes both`() {
        val rig = Rig(DEFAULT_SETTINGS.copy(bpm = 150, rampOn = true, rampTo = 180, rampMinutes = 1))
        rig.controller.start()
        assertEquals(listOf("output.open", "output.startLoop", "host.start"), rig.events)
        assertTrue(rig.controller.state.value.running)

        rig.play(30.0)
        assertTrue("the ramp moved what is sounding", rig.controller.state.value.currentBpm > 150)

        rig.events.clear()
        rig.controller.stop()
        assertEquals(listOf("output.stopLoop", "output.close", "host.stop"), rig.events)
        assertFalse(rig.controller.state.value.running)
        assertEquals("stop returns the display to the chosen cadence", 150, rig.controller.state.value.currentBpm)
    }

    @Test
    fun `a ramp tick moves the cadence and never saves it`() {
        val rig = Rig(DEFAULT_SETTINGS.copy(bpm = 150, rampOn = true, rampTo = 180, rampMinutes = 1))
        rig.controller.start()
        rig.play(30.0)
        assertEquals(165, rig.controller.state.value.currentBpm)
        rig.play(31.0)
        assertEquals(180, rig.controller.state.value.currentBpm)
        assertEquals("storage still holds the chosen cadence", 150, rig.storage.values["bpm"])
        assertEquals(150, rig.controller.settings.value.bpm)
    }

    @Test
    fun `a manual change outside a ramp is a new choice and is saved`() {
        val rig = Rig(DEFAULT_SETTINGS.copy(bpm = 170))
        rig.controller.setCadence(172)
        assertEquals(172, rig.storage.values["bpm"])

        rig.controller.start()
        rig.controller.nudge(-1)
        assertEquals("the notification's Slower takes the same path", 171, rig.storage.values["bpm"])
        assertEquals(171, rig.controller.state.value.currentBpm)
        rig.controller.stop()
        assertEquals(171, rig.controller.state.value.currentBpm)
    }

    @Test
    fun `a manual change during a running ramp holds without saving`() {
        val rig = Rig(DEFAULT_SETTINGS.copy(bpm = 150, rampOn = true, rampTo = 180, rampMinutes = 30))
        rig.controller.start()
        rig.play(10.0)
        rig.controller.nudge(-1)
        val held = rig.controller.state.value.currentBpm
        assertTrue(rig.controller.state.value.rampHeld)
        rig.play(120.0)
        assertEquals("the ramp no longer moves it", held, rig.controller.state.value.currentBpm)
        assertEquals("and storage keeps the chosen cadence", 150, rig.storage.values["bpm"])

        rig.controller.stop()
        assertEquals(150, rig.controller.state.value.currentBpm)
        rig.controller.start()
        assertFalse("the next run climbs again", rig.controller.state.value.rampHeld)
        assertEquals(150, rig.controller.state.value.currentBpm)
    }

    @Test
    fun `a call silences the beat and the end of the call resumes it`() {
        val rig = Rig(DEFAULT_SETTINGS.copy(bpm = 180, rampOn = true, rampTo = 200, rampMinutes = 10))
        rig.controller.start()
        rig.play(10.0)
        rig.controller.drain()
        val stepsBefore = rig.controller.state.value.steps
        val clockBefore = rig.controller.state.value.elapsedSeconds
        val bpmBefore = rig.controller.state.value.currentBpm
        val armedBefore = rig.haptics.armed.size

        rig.controller.onModeChanged(AudioManager.MODE_IN_CALL)
        assertTrue(rig.controller.state.value.silenced)
        rig.play(60.0)
        rig.controller.drain()
        val tail = rig.controller.state.value.steps - stepsBefore
        assertTrue("at most the beats already written before the call are counted, not $tail", tail <= 1)
        assertEquals("the run clock paused", clockBefore, rig.controller.state.value.elapsedSeconds, 0.05)
        assertEquals("and so did the ramp", bpmBefore, rig.controller.state.value.currentBpm)

        rig.controller.onModeChanged(AudioManager.MODE_NORMAL)
        assertFalse(rig.controller.state.value.silenced)
        rig.play(10.0)
        rig.controller.drain()
        assertTrue("counting again", rig.controller.state.value.steps - stepsBefore - tail >= 28)
        assertEquals("the clock picks up where it was", clockBefore + 10.0, rig.controller.state.value.elapsedSeconds, 0.05)
        assertEquals("vibrate is off, so nothing is armed", armedBefore, rig.haptics.armed.size)
    }

    @Test
    fun `a run started during a call starts silent`() {
        val rig = Rig(mode = AudioManager.MODE_IN_CALL)
        rig.controller.start()
        rig.play(5.0)
        rig.controller.drain()
        assertTrue(rig.controller.state.value.silenced)
        assertEquals(0, rig.controller.state.value.steps)
        assertEquals(0.0, rig.controller.state.value.elapsedSeconds, 0.0)
    }

    @Test
    fun `a mode this code has never seen counts as a call`() {
        val rig = Rig()
        rig.controller.start()
        rig.controller.onModeChanged(99)
        assertTrue(rig.controller.state.value.silenced)
        rig.play(2.0)
        rig.controller.drain()
        assertTrue(rig.controller.state.value.steps <= 1)
    }

    @Test
    fun `disconnecting the earbuds stops the run, and is ignored while stopped`() {
        val rig = Rig()
        rig.controller.onBecomingNoisy()
        assertNull(rig.controller.state.value.stopReason)
        assertTrue(rig.events.isEmpty())

        rig.controller.start()
        rig.controller.onBecomingNoisy()
        assertFalse(rig.controller.state.value.running)
        assertEquals(StopReason.Noisy, rig.controller.state.value.stopReason)
        assertTrue(rig.events.contains("host.stop"))
    }

    @Test
    fun `only the stops that explain themselves set a reason, and anything after clears it`() {
        val rig = Rig()
        rig.controller.start()
        rig.controller.stop()
        assertNull("a Stop tap explains nothing", rig.controller.state.value.stopReason)

        rig.controller.start()
        rig.controller.onBecomingNoisy()
        assertEquals(StopReason.Noisy, rig.controller.state.value.stopReason)
        rig.controller.start()
        assertNull("Start clears it", rig.controller.state.value.stopReason)

        rig.controller.fail()
        assertEquals(StopReason.OutputFailed, rig.controller.state.value.stopReason)
        rig.controller.consumeStopReason()
        assertNull("showing the snackbar clears it", rig.controller.state.value.stopReason)

        rig.controller.start()
        rig.controller.onBecomingNoisy()
        rig.controller.start()
        rig.controller.stop()
        assertNull("noisy stop, Start, then Stop: nothing stale", rig.controller.state.value.stopReason)
    }

    private fun clockCase(stamps: FakeOutput.Stamps, deadObjectAfter: Int? = null) {
        val rig = Rig(DEFAULT_SETTINGS.copy(bpm = 180, vibrate = true))
        rig.output.stamps = stamps
        rig.controller.start()
        val counted = mutableListOf<Long>()
        rig.playDraining(100, counted)
        if (deadObjectAfter != null) rig.output.writeResults.addLast(AudioTrack.ERROR_DEAD_OBJECT)
        val base = rig.output.trackBase()
        rig.playDraining(300, counted)
        val label = "$stamps, recreated=${deadObjectAfter != null}"
        if (deadObjectAfter != null) assertTrue("$label: recreated", rig.output.trackBase() != base)

        val beatFrames = (0 until counted.size).map { k -> Math.round(0.08 * rate + k * rate / 3.0) }
        val onCurrentTrack = beatFrames.indices.filter { beatFrames[it] >= rig.output.trackBase() }
        assertTrue("$label: beats on the current track", onCurrentTrack.size > 10)
        for (k in onCurrentTrack) {
            val heard = rig.output.soundsAt(beatFrames[k])
            val lag = counted[k] - heard
            assertTrue("$label: step $k counted ${lag / 1e6} ms after it sounded", lag in 0..bufferNanos)
        }
        // Every beat is audible and vibrate is on, so the k-th haptic armed is beat k's.
        for (k in onCurrentTrack) {
            val armed = rig.haptics.armed[k]
            val fires = armed.at + armed.delayMillis * 1_000_000
            val off = abs(fires - rig.output.soundsAt(beatFrames[k]))
            assertTrue("$label: beat $k's haptic fires ${off / 1e6} ms off its beat", off <= bufferNanos)
        }
    }

    @Test
    fun `steps and haptics follow the playback clock with a timestamp from the start`() = clockCase(FakeOutput.Stamps.ALWAYS)

    @Test
    fun `steps and haptics follow the playback head until a timestamp arrives`() = clockCase(FakeOutput.Stamps.AFTER_FIRST_SECOND)

    @Test
    fun `steps and haptics follow the playback head on a route with no timestamps`() = clockCase(FakeOutput.Stamps.NEVER)

    @Test
    fun `steps and haptics follow a recreated track whose timestamps restart at 0`() =
        clockCase(FakeOutput.Stamps.ALWAYS, deadObjectAfter = 100)

    @Test
    fun `an output that fails to open leaves the run stopped, with a reason`() {
        val rig = Rig()
        rig.output.openResults.addLast(false)
        rig.controller.start()
        assertFalse(rig.controller.state.value.running)
        assertEquals(StopReason.StartFailed, rig.controller.state.value.stopReason)
        assertFalse(rig.events.contains("host.start"))
    }

    @Test
    fun `one dead object recreates the track and the beat carries on, on the grid`() {
        val rig = Rig(DEFAULT_SETTINGS.copy(bpm = 180))
        rig.controller.start()
        rig.output.step(50)
        rig.output.writeResults.addLast(AudioTrack.ERROR_DEAD_OBJECT)
        assertTrue("the loop carries on", rig.output.step(200))
        assertEquals(2, rig.output.opens)
        assertTrue(rig.controller.state.value.running)

        val counted = mutableListOf<Long>()
        rig.playDraining(100, counted)
        assertTrue("beats keep sounding", counted.size >= 5)
    }

    @Test
    fun `a second dead object within a second stops the run`() {
        val rig = Rig()
        rig.controller.start()
        rig.output.step(10)
        rig.output.writeResults.addLast(AudioTrack.ERROR_DEAD_OBJECT)
        rig.output.writeResults.addLast(AudioTrack.ERROR_DEAD_OBJECT)
        assertFalse("the loop ends", rig.output.step(5))
        rig.scope.runCurrent()
        assertFalse(rig.controller.state.value.running)
        assertEquals(StopReason.OutputFailed, rig.controller.state.value.stopReason)
    }

    @Test
    fun `dead objects far apart are each recovered`() {
        val rig = Rig()
        rig.controller.start()
        rig.output.writeResults.addLast(AudioTrack.ERROR_DEAD_OBJECT)
        rig.output.step(100)
        rig.output.writeResults.addLast(AudioTrack.ERROR_DEAD_OBJECT)
        assertTrue(rig.output.step(10))
        rig.scope.runCurrent()
        assertTrue(rig.controller.state.value.running)
        assertEquals(3, rig.output.opens)
    }

    @Test
    fun `any other write error stops the run`() {
        val rig = Rig()
        rig.controller.start()
        rig.output.writeResults.addLast(AudioTrack.ERROR_INVALID_OPERATION)
        assertFalse(rig.output.step(1))
        rig.scope.runCurrent()
        assertFalse(rig.controller.state.value.running)
        assertEquals(StopReason.OutputFailed, rig.controller.state.value.stopReason)
    }

    @Test
    fun `a recreate that cannot reopen stops the run`() {
        val rig = Rig()
        rig.controller.start()
        rig.output.writeResults.addLast(AudioTrack.ERROR_DEAD_OBJECT)
        rig.output.openResults.addLast(false)
        assertFalse(rig.output.step(1))
        rig.scope.runCurrent()
        assertEquals(StopReason.OutputFailed, rig.controller.state.value.stopReason)
    }

    @Test
    fun `a host that cannot hold the foreground stops the output`() {
        val rig = Rig()
        rig.controller.start()
        rig.controller.fail()
        assertTrue(rig.events.contains("output.stopLoop"))
        assertFalse(rig.controller.state.value.running)
        assertEquals(StopReason.OutputFailed, rig.controller.state.value.stopReason)
    }

    @Test
    fun `beats cross from the render thread without being lost or doubled`() {
        val rig = Rig()
        rig.controller.start()
        // One buffer, before the first beat, so the clock holds a timestamp to extrapolate from.
        rig.output.step(1)
        val total = 10_000
        val renderThread = Thread {
            for (count in 0 until total) {
                rig.controller.onBeatWritten(CommittedBeat(frame = count.toLong(), count = count, accent = false, silent = count % 7 == 0))
            }
        }
        // A thousand seconds on: every beat is presented as soon as it is drained.
        val later = rig.output.now + 1_000_000_000_000L
        renderThread.start()
        while (renderThread.isAlive) rig.controller.drain(now = later)
        renderThread.join()
        rig.controller.drain(now = later)
        assertEquals((0 until total).count { it % 7 != 0 }, rig.controller.state.value.steps)
    }

    @Test
    fun `picking a voice or an accent plays one beat, only while stopped with the click on`() {
        val rig = Rig()
        rig.controller.chooseVoice("cowbell")
        rig.controller.chooseAccent(3)
        assertEquals(2, rig.output.plays.size)

        rig.controller.start()
        rig.controller.chooseVoice("kick")
        assertEquals("not during a run", 2, rig.output.plays.size)
        rig.controller.stop()

        rig.controller.setVibrate(true)
        rig.controller.setClick(false)
        rig.controller.chooseVoice("beep")
        assertEquals("not with the click off", 2, rig.output.plays.size)
        assertEquals("beep", rig.storage.values["voice"])
    }

    @Test
    fun `vibrate only arms a haptic per beat, flagging the accents`() {
        val rig = Rig(DEFAULT_SETTINGS.copy(click = false, vibrate = true, accentEvery = 2))
        rig.controller.start()
        rig.play(3.0)
        assertTrue(rig.haptics.armed.size >= 8)
        assertTrue(rig.haptics.armed.any { it.accent } && rig.haptics.armed.any { !it.accent })
        rig.controller.stop()
        assertEquals(1, rig.haptics.cancels)
    }

    @Test
    fun `with no vibration motor the click stays on whatever was stored`() {
        val rig = Rig(DEFAULT_SETTINGS.copy(click = false, vibrate = true), hapticsAvailable = false)
        rig.controller.start()
        rig.output.step(100)
        assertTrue(rig.haptics.armed.isEmpty())
        assertTrue("the beat is heard", rig.output.audibleWrites > 0)
    }
}

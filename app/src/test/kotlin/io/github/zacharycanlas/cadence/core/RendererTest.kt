package io.github.zacharycanlas.cadence.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.random.Random

class RendererTest {
    private val rate = 48_000

    private val defaults = RenderSettings(bpm = 180, voice = "beep", accentEvery = 0, click = true, silenced = false)

    /** Drives a renderer in fixed-size buffers, keeping every sample and beat it writes. */
    private inner class Run(settings: RenderSettings = defaults, private val bufferFrames: Int = 4800, seed: Int = 1) {
        val renderer = Renderer(rate, VoiceKit(rate, Noise.seeded(rate, Random(seed))), settings)
        val beats = mutableListOf<CommittedBeat>()
        private val samples = ArrayList<Float>()
        private val buffer = FloatArray(bufferFrames)
        var keepSamples = true

        fun advance(seconds: Double) = advanceFrames((seconds * rate).roundToLong())

        fun advanceFrames(total: Long) {
            var left = total
            while (left > 0) {
                val n = minOf(left, bufferFrames.toLong()).toInt()
                val from = renderer.writeHead
                renderer.render(buffer, n) {
                    check(it.frame in from until from + n) { "beat ${it.count} at ${it.frame}, outside the buffer at $from" }
                    beats.add(it)
                }
                if (keepSamples) for (i in 0 until n) samples.add(buffer[i])
                left -= n
            }
        }

        fun change(transform: RenderSettings.() -> RenderSettings) {
            renderer.settings = renderer.settings.transform()
        }

        fun output(): FloatArray = samples.toFloatArray()
    }

    private fun gaps(beats: List<CommittedBeat>) = beats.zipWithNext { a, b -> b.frame - a.frame }

    private fun periodFrames(bpm: Int) = 60.0 / bpm * rate

    @Test
    fun `the first beat comes 80 ms after start`() {
        val run = Run()
        run.advance(0.5)
        assertEquals((0.08 * rate).roundToLong(), run.beats.first().frame)
    }

    @Test
    fun `a tempo change inside the lead keeps the first beat at 80 ms`() {
        val run = Run(bufferFrames = 480)
        run.advance(0.04)
        run.change { copy(bpm = 120) }
        run.advance(1.0)
        assertEquals((0.08 * rate).roundToLong(), run.beats[0].frame)
        assertEquals(periodFrames(120), (run.beats[1].frame - run.beats[0].frame).toDouble(), 1.0)
    }

    @Test
    fun `beat spacing stays exact over an hour`() {
        val run = Run(defaults.copy(click = false))
        run.keepSamples = false
        run.advance(3600.2)

        val lead = (0.08 * rate).roundToLong()
        assertEquals(180 * 60 + 1, run.beats.size)
        run.beats.forEachIndexed { k, beat ->
            val expected = lead + k * periodFrames(180)
            assertTrue("beat $k is ${beat.frame - expected} frames off its grid slot", abs(beat.frame - expected) <= 0.5)
        }
        assertEquals("an hour of beats spans exactly an hour", 3600L * rate, run.beats.last().frame - run.beats.first().frame)
    }

    @Test
    fun `the accent follows the running count`() {
        val run = Run(defaults.copy(accentEvery = 3))
        run.advance(4.0)
        run.beats.forEach { assertEquals("beat ${it.count}", it.count % 3 == 0, it.accent) }
        assertTrue(run.beats.count { it.accent } > 3)
    }

    @Test
    fun `a hit spanning a buffer boundary renders the same samples as in one buffer`() {
        val seconds = 3.0
        val reference = Run(defaults.copy(voice = "rimshot"), bufferFrames = 4800).also { it.advance(seconds) }.output()
        for (size in listOf(1, 64, 333)) {
            val split = Run(defaults.copy(voice = "rimshot"), bufferFrames = size).also { it.advance(seconds) }.output()
            assertArrayEquals("buffer of $size frames", reference, split, 0f)
        }
    }

    @Test
    fun `voice, accent and click changes land on the next unwritten beat`() {
        val run = Run(defaults.copy(voice = "beep"))
        run.advance(1.0)
        val changeAt = run.renderer.writeHead
        run.change { copy(voice = "kick", accentEvery = 2) }
        run.advance(1.0)

        val next = run.beats.first { it.frame >= changeAt }
        assertEquals("the accent rule applies from the next beat", next.count % 2 == 0, next.accent)
        val kit = VoiceKit(rate, Noise.seeded(rate, Random(9)))
        val expected = FloatArray(kit.maxFrames).also { kit.render("kick", next.accent, it) }
        val out = run.output()
        val heard = out.copyOfRange(next.frame.toInt(), next.frame.toInt() + 2000)
        assertArrayEquals("the next beat is the new voice", expected.copyOf(2000), heard, 1e-6f)

        run.change { copy(click = false) }
        val clickOffAt = run.renderer.writeHead
        run.advance(1.0)
        val silentFrom = run.beats.first { it.frame >= clickOffAt }.frame.toInt() + kit.maxFrames
        assertTrue("with the click off nothing new sounds", run.output().drop(silentFrom).all { it == 0f })
    }

    @Test
    fun `vibrate only emits beats over silent output`() {
        val run = Run(defaults.copy(click = false))
        run.advance(3.0)
        assertEquals(9, run.beats.size)
        assertTrue(run.beats.none { it.silent })
        assertTrue(run.output().all { it == 0f })
    }

    /** Applies each (bpm, wait) after waiting, then runs on for two seconds. */
    private fun sweep(start: Int, changes: List<Pair<Int, Double>>, bufferFrames: Int): List<CommittedBeat> {
        val run = Run(defaults.copy(bpm = start, click = false), bufferFrames)
        run.keepSamples = false
        for ((bpm, wait) in changes) {
            run.advance(wait)
            run.change { copy(bpm = bpm) }
        }
        run.advance(2.0)
        return run.beats
    }

    @Test
    fun `a tempo change never lands a beat on top of one already sounded`() {
        val drops = listOf(204 to 1.04, 188 to 1.03, 172 to 1.07, 156 to 1.02, 140 to 1.05, 124 to 1.06, 180 to 1.01, 164 to 1.04)
        for (size in listOf(1, 64, 4800)) {
            val floor = periodFrames(220) - 1
            gaps(sweep(220, drops, size)).forEach {
                assertTrue("buffer $size: a gap of $it frames, under the fastest period", it >= floor)
            }
        }
    }

    @Test
    fun `a tempo change never swallows a beat either`() {
        // 120 to 132 at 1.04 s with a one-frame buffer is the case where
        // re-anchoring at the write head alone drops a beat.
        val rises = listOf(132 to 1.04, 147 to 1.03, 159 to 1.07, 168 to 1.02, 181 to 1.05, 196 to 1.06, 207 to 1.01, 220 to 1.04)
        for (size in listOf(1, 64, 4800)) {
            val ceiling = periodFrames(120) + 1
            gaps(sweep(120, rises, size)).forEach {
                assertTrue("buffer $size: a gap of $it frames, over the slowest period", it <= ceiling)
            }
        }
    }

    @Test
    fun `the gap across a tempo change is the new period on a drop and between the two on a rise`() {
        for ((from, to) in listOf(180 to 150, 150 to 180, 120 to 220, 220 to 100)) {
            for (step in 0 until 40) {
                val run = Run(defaults.copy(bpm = from, click = false), bufferFrames = 480)
                run.keepSamples = false
                run.advanceFrames((1.0 * rate).toLong() + step * 397L)
                val before = run.beats.size
                run.change { copy(bpm = to) }
                run.advance(1.0)
                val gap = run.beats[before].frame - run.beats[before - 1].frame
                val newPeriod = periodFrames(to)
                val oldPeriod = periodFrames(from)
                val label = "$from to $to, step $step: gap $gap"
                if (to < from) {
                    assertTrue(label, abs(gap - newPeriod) <= 1)
                } else {
                    assertTrue(label, gap >= newPeriod - 1 && gap <= oldPeriod + 1)
                }
                gaps(run.beats.drop(before)).forEach { assertTrue("$label, then $it", abs(it - newPeriod) <= 1) }
            }
        }
    }

    @Test
    fun `every fourth beat heard is the accented one across tempo changes`() {
        val run = Run(defaults.copy(accentEvery = 4, click = false))
        run.keepSamples = false
        run.advance(3.0)
        for (bpm in listOf(190, 200, 210)) {
            run.change { copy(bpm = bpm) }
            run.advance(3.0)
        }
        assertEquals("numbered 0..N with no holes", run.beats.indices.toList(), run.beats.map { it.count })
        run.beats.forEachIndexed { position, beat -> assertEquals("beat $position heard", position % 4 == 0, beat.accent) }
    }

    @Test
    fun `a tempo change takes effect within a buffer`() {
        val run = Run(defaults.copy(bpm = 120, click = false))
        run.keepSamples = false
        run.advance(3.0)
        val changeAt = run.renderer.writeHead
        run.change { copy(bpm = 200) }
        run.advance(3.0)

        gaps(run.beats.takeLast(6)).forEach { assertTrue("spaced $it", abs(it - periodFrames(200)) <= 1) }
        val firstAfter = run.beats.first { it.frame >= changeAt }
        val secondAfter = run.beats[run.beats.indexOf(firstAfter) + 1]
        assertTrue(
            "the new spacing starts within one buffer of the change",
            secondAfter.frame - changeAt <= 4800 + periodFrames(200),
        )
        assertTrue(abs(secondAfter.frame - firstAfter.frame - periodFrames(200)) <= 1)
    }

    @Test
    fun `beats inside a call are counted for the accent but carry no sound`() {
        val run = Run(defaults.copy(accentEvery = 4, voice = "beep"))
        run.advance(2.0)
        val silenceFrom = run.renderer.writeHead
        run.change { copy(silenced = true) }
        run.advance(3.0)
        val silenceTo = run.renderer.writeHead
        run.change { copy(silenced = false) }
        run.advance(2.0)

        val inside = run.beats.filter { it.frame in silenceFrom until silenceTo }
        assertTrue(inside.isNotEmpty())
        assertTrue("flagged silent", inside.all { it.silent })
        assertTrue("and silent", run.output().copyOfRange(silenceFrom.toInt(), silenceTo.toInt()).all { it == 0f })

        val firstAfter = run.beats.first { it.frame >= silenceTo }
        assertEquals("the count ran on through the call", inside.last().count + 1, firstAfter.count)
        assertEquals("so the accent is where the grid puts it", firstAfter.count % 4 == 0, firstAfter.accent)
        val lead = 0.08 * rate
        assertTrue("silence never moves the anchor", abs(firstAfter.frame - (lead + firstAfter.count * periodFrames(180))) <= 0.5)
        assertTrue("and the beat sounds again", run.output().copyOfRange(firstAfter.frame.toInt(), firstAfter.frame.toInt() + 500).any { it != 0f })
    }

    @Test
    fun `the run clock stops for a call and picks up where it left off`() {
        val run = Run(defaults.copy(click = false))
        run.keepSamples = false
        run.advance(60.0)
        val before = run.renderer.elapsedSeconds
        run.change { copy(silenced = true) }
        run.advance(600.0)
        assertEquals("frozen through ten minutes of call", before, run.renderer.elapsedSeconds, 0.0)
        run.change { copy(silenced = false) }
        run.advance(30.0)
        assertEquals("only the audible frames count", 90.0, run.renderer.elapsedSeconds, 1e-9)
    }
}

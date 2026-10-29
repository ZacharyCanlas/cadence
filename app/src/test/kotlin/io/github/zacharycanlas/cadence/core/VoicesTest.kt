package io.github.zacharycanlas.cadence.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

class VoicesTest {
    private val rate = 48_000

    private fun kit(seed: Int = 7) = VoiceKit(rate, Noise.seeded(rate, Random(seed)))

    private fun hit(voice: String, accent: Boolean, kit: VoiceKit = kit()): FloatArray {
        val out = FloatArray(kit.maxFrames)
        val n = kit.render(voice, accent, out)
        return out.copyOf(n)
    }

    private fun peak(samples: FloatArray) = samples.maxOf { abs(it) }

    private fun zeroCrossings(samples: FloatArray) =
        (1 until samples.size).count { (samples[it - 1] < 0) != (samples[it] < 0) }

    @Test
    fun `every voice starts at its first frame and is audible`() {
        for (voice in VOICE_IDS) for (accent in listOf(false, true)) {
            val samples = hit(voice, accent)
            val label = "$voice accent=$accent"
            val onset = samples.indexOfFirst { abs(it) > 1e-4 }
            assertTrue("$label starts at frame $onset", onset in 0..2)
            assertTrue("$label is audible", peak(samples) > 0.05)
            assertTrue("$label fits a hit buffer", samples.size <= kit().maxFrames)
        }
    }

    @Test
    fun `every voice is both re-pitched and lifted on the accent`() {
        for (voice in VOICE_IDS) {
            val plain = hit(voice, false)
            val accented = hit(voice, true)
            assertNotEquals("$voice: the accent moves the pitch", zeroCrossings(plain), zeroCrossings(accented))
            assertTrue("$voice: the accent rises in level", peak(accented) > peak(plain))
        }
    }

    @Test
    fun `the kit plays a kick on the accent and a hi-hat between`() {
        assertArrayEquals(hit("kick", false, kit(3)), hit("kit", true, kit(3)), 0f)
        assertArrayEquals(hit("hihat", false, kit(3)), hit("kit", false, kit(3)), 0f)
    }

    @Test
    fun `an unknown voice id falls back to the click`() {
        assertEquals("click", findVoice("nonsense"))
        for (id in VOICE_IDS) assertEquals(id, findVoice(id))
        assertArrayEquals(hit("click", false), hit("nonsense", false), 0f)
    }

    /** Steady-state gain of `filter` for a sine at `frequency`, in dB. */
    private fun measuredGainDb(filter: Biquad, frequency: Double): Double {
        val n = rate / 2
        var sumSquares = 0.0
        var counted = 0
        for (i in 0 until n) {
            val y = filter.process(sin(2 * PI * frequency * i / rate))
            if (i >= n / 2) {
                sumSquares += y * y
                counted += 1
            }
        }
        return 20 * log10(sqrt(sumSquares / counted) * sqrt(2.0))
    }

    /** The bilinear transform maps the analog prototype exactly, at Ω = tan(ω/2) / tan(ω0/2). */
    private fun warped(frequency: Double, center: Double) = tan(PI * frequency / rate) / tan(PI * center / rate)

    @Test
    fun `the highpass reads its Q in decibels, as Web Audio does`() {
        // At its cutoff the prototype's gain equals its linear Q, so Q 1 dB is +1 dB there.
        // A Q taken as linear would read 0 dB.
        val qLinear = 10.0.pow(1.0 / 20)
        val omega = warped(2200.0, 2200.0)
        val expected = 20 * log10(omega * omega / sqrt((1 - omega * omega).pow(2) + (omega / qLinear).pow(2)))
        assertEquals(expected, measuredGainDb(Biquad.highpass(2200.0, rate), 2200.0), 0.1)
    }

    @Test
    fun `the bandpass reads its Q as a plain ratio, as Web Audio does`() {
        // An octave above the centre, a Q of 2 is down about 10.2 dB. Taken as
        // decibels it would be a Q of 1.26 and only about 6.8 dB down.
        val center = 1800.0
        val omega = warped(2 * center, center)
        val q = 2.0
        val expected = 20 * log10((omega / q) / sqrt((1 - omega * omega).pow(2) + (omega / q).pow(2)))
        assertEquals(expected, measuredGainDb(Biquad.bandpass(center, rate, q), 2 * center), 0.1)
    }
}

package io.github.zacharycanlas.cadence.core

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Voice ids, in display order. Labels and hints live in strings.xml. */
val VOICE_IDS: List<String> = listOf("click", "woodblock", "beep", "cowbell", "rimshot", "kick", "hihat", "kit")

const val DEFAULT_VOICE = "click"

fun findVoice(id: String): String = if (id in VOICE_IDS) id else DEFAULT_VOICE

/** The longest hit: the kick's 0.22 s envelope plus its 0.02 s stop tail. */
const val MAX_HIT_SECONDS = 0.24

/** Accent beats are pitched up rather than just made louder, so they cut through music. */
private const val ACCENT_PITCH = 1.5
private const val ACCENT_GAIN = 1.35

/** Where envelopes decay to and then hold until their source stops. */
private const val ENVELOPE_FLOOR = 0.0001

/** 1 / 1.17898: the peak of a 2048-partial square series, which Web Audio scales to 1. */
private const val SQUARE_NORMALIZATION = 0.84819

/**
 * One second of white noise and the source of each hit's start offset into it.
 * A random offset stops repeated hits sounding like an identical loop.
 */
class Noise(val buffer: FloatArray, private val nextOffsetSeconds: () -> Double) {
    fun nextOffset(): Double = nextOffsetSeconds()

    companion object {
        fun seeded(rate: Int, random: Random): Noise =
            Noise(FloatArray(rate) { random.nextFloat() * 2 - 1 }) { random.nextDouble() * 0.5 }
    }
}

/**
 * Biquad with the Web Audio spec's coefficients: highpass Q is in decibels,
 * bandpass Q is linear. Mixing the two up changes the timbre audibly.
 */
class Biquad private constructor(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) {
    private val b0 = b0 / a0
    private val b1 = b1 / a0
    private val b2 = b2 / a0
    private val a1 = a1 / a0
    private val a2 = a2 / a0
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = x
        y2 = y1
        y1 = y
        return y
    }

    companion object {
        /** Web Audio's default Q, in dB for a highpass. */
        const val DEFAULT_Q = 1.0

        fun highpass(frequency: Double, rate: Int, qDb: Double = DEFAULT_Q): Biquad {
            val w0 = 2 * PI * frequency / rate
            val alpha = sin(w0) / (2 * 10.0.pow(qDb / 20))
            val c = cos(w0)
            return Biquad((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha)
        }

        fun bandpass(frequency: Double, rate: Int, q: Double): Biquad {
            val w0 = 2 * PI * frequency / rate
            val alpha = sin(w0) / (2 * q)
            val c = cos(w0)
            return Biquad(alpha, 0.0, -alpha, 1 + alpha, -2 * c, 1 - alpha)
        }
    }
}

private enum class Wave { SINE, TRIANGLE, SQUARE }

/**
 * The eight voices, synthesised per hit at the output rate. Every hit is
 * rendered from its onset at index 0, so where it lands is the caller's
 * business. Used from one thread only, since each noise hit draws its offset
 * from [Noise] as it renders.
 */
class VoiceKit(private val rate: Int, private val noise: Noise) {
    /** Frames a hit buffer must hold. */
    val maxFrames: Int = ceil(MAX_HIT_SECONDS * rate).toInt() + 1

    private fun frames(seconds: Double): Int = minOf(maxFrames, ceil(seconds * rate).toInt())

    private fun pitch(base: Double, accent: Boolean) = if (accent) base * ACCENT_PITCH else base
    private fun level(base: Double, accent: Boolean) = if (accent) base * ACCENT_GAIN else base

    /** Instant attack, exponential tail to the floor, then held at the floor. */
    private fun envelope(t: Double, peak: Double, seconds: Double): Double =
        if (t < seconds) peak * (ENVELOPE_FLOOR / peak).pow(t / seconds) else ENVELOPE_FLOOR

    /**
     * Clears `out` and renders one hit of `voiceId` into it from index 0.
     * Returns the frames written. `out` holds at least [maxFrames].
     */
    fun render(voiceId: String, accent: Boolean, out: FloatArray): Int {
        out.fill(0f, 0, maxFrames)
        return when (findVoice(voiceId)) {
            "click" -> tone(out, Wave.SQUARE, pitch(1250.0, accent), pitch(850.0, accent), 0.02, level(0.5, accent), 0.045)
            "woodblock" -> maxOf(
                tone(out, Wave.TRIANGLE, pitch(1050.0, accent), pitch(760.0, accent), 0.03, level(0.75, accent), 0.07),
                noiseHit(out, Biquad.bandpass(pitch(1800.0, accent), rate, 2.0), 0.03, level(0.3, accent), 0.02),
            )
            "beep" -> tone(out, Wave.SINE, pitch(880.0, accent), pitch(880.0, accent), 0.0, level(0.6, accent), 0.09)
            "cowbell" -> cowbell(out, accent)
            "rimshot" -> maxOf(
                tone(out, Wave.TRIANGLE, pitch(420.0, accent), pitch(220.0, accent), 0.02, level(0.7, accent), 0.04),
                noiseHit(out, Biquad.highpass(2200.0, rate), 0.04, level(0.35, accent), 0.03),
            )
            "kick" -> tone(
                out, Wave.SINE,
                if (accent) 190.0 else 160.0, if (accent) 55.0 else 48.0,
                0.09, level(0.95, accent), 0.22,
            )
            "hihat" -> noiseHit(
                out, Biquad.highpass(if (accent) 7000.0 else 8500.0, rate),
                0.1, level(0.35, accent), if (accent) 0.09 else 0.04,
            )
            // Kick on the accent, hi-hat between, both unaccented.
            else -> render(if (accent) "kick" else "hihat", accent = false, out)
        }
    }

    /**
     * An oscillator from `from` sweeping exponentially to `to` over `sweep`
     * seconds, under an envelope of `length`, stopping at `length` + 0.02.
     */
    private fun tone(out: FloatArray, wave: Wave, from: Double, to: Double, sweep: Double, peak: Double, length: Double): Int {
        val n = frames(length + 0.02)
        val sweeps = sweep > 0 && to != from
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / rate
            val f = if (sweeps && t < sweep) from * (to / from).pow(t / sweep) else if (sweeps) to else from
            val dt = f / rate
            val sample = when (wave) {
                Wave.SINE -> sin(2 * PI * phase)
                Wave.SQUARE -> oddSeries(phase, dt, square = true)
                Wave.TRIANGLE -> oddSeries(phase, dt, square = false)
            }
            out[i] += (sample * envelope(t, peak, length)).toFloat()
            phase += dt
            if (phase >= 1) phase -= 1
        }
        return n
    }

    /** Two detuned squares through a bandpass, the classic drum-machine recipe. */
    private fun cowbell(out: FloatArray, accent: Boolean): Int {
        val n = frames(0.18)
        val band = Biquad.bandpass(pitch(2640.0, accent), rate, 1.2)
        val steps = doubleArrayOf(pitch(587.3, accent) / rate, pitch(845.0, accent) / rate)
        val phases = doubleArrayOf(0.0, 0.0)
        val peak = level(0.4, accent)
        for (i in 0 until n) {
            var x = 0.0
            for (k in 0..1) {
                x += oddSeries(phases[k], steps[k], square = true)
                phases[k] += steps[k]
                if (phases[k] >= 1) phases[k] -= 1
            }
            out[i] += (band.process(x) * envelope(i.toDouble() / rate, peak, 0.16)).toFloat()
        }
        return n
    }

    /** `duration` seconds of noise from a random offset, through `filter`, under an envelope. */
    private fun noiseHit(out: FloatArray, filter: Biquad, duration: Double, peak: Double, seconds: Double): Int {
        val n = frames(duration)
        val start = (noise.nextOffset() * rate).roundToInt()
        val buffer = noise.buffer
        for (i in 0 until n) {
            val x = buffer[(start + i) % buffer.size].toDouble()
            out[i] += (filter.process(x) * envelope(i.toDouble() / rate, peak, seconds)).toFloat()
        }
        return n
    }

    /**
     * Web Audio's square or triangle: the odd-partial Fourier series, cut off
     * the way Chrome's band-limited tables are. A fundamental picks a table
     * from 36 ranges, three per octave, each holding 2^(-range/3) of the
     * partials, and blends it with the next range's. That keeps every partial
     * below Nyquist and drops a few more besides, so each voice keeps the
     * timbre of the Web Audio graph it was voiced on. The square is
     * normalised so its Gibbs overshoot peaks at 1.
     * `phase` in cycles, `dt` the fundamental's phase step per frame.
     */
    private fun oddSeries(phase: Double, dt: Double, square: Boolean): Double {
        val pitchRange = (1 + 3 * log2(dt * tableSize)).coerceIn(0.0, (TABLE_RANGES - 1).toDouble())
        val range = pitchRange.toInt()
        val blend = pitchRange - range
        val morePartials = partialsInRange(range)
        val fewerPartials = partialsInRange(minOf(range + 1, TABLE_RANGES - 1))

        val theta = 2 * PI * phase
        // sin((k + 2)θ) = 2cos(2θ)·sin(kθ) − sin((k − 2)θ), so each partial costs no trig call.
        val step = 2 * cos(2 * theta)
        var previous = -sin(theta)
        var current = -previous
        var sum = 0.0
        var sumOfFewer = 0.0
        var k = 1
        while (k <= morePartials) {
            sum += current * if (square) {
                4 / (PI * k)
            } else {
                (if (k % 4 == 1) 8.0 else -8.0) / (PI * PI * k * k)
            }
            if (k <= fewerPartials) sumOfFewer = sum
            val next = step * current - previous
            previous = current
            current = next
            k += 2
        }
        val blended = (1 - blend) * sum + blend * sumOfFewer
        return if (square) SQUARE_NORMALIZATION * blended else blended
    }

    private fun partialsInRange(range: Int): Int = (tableSize / 2 * 2.0.pow(-range / 3.0)).toInt()

    /** Chrome's periodic-wave table length for the output rate. */
    private val tableSize: Int = when {
        rate <= 24_000 -> 2048
        rate <= 88_200 -> 4096
        else -> 16384
    }
}

private const val TABLE_RANGES = 36

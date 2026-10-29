package io.github.zacharycanlas.cadence.core

import kotlin.math.floor
import kotlin.math.max

/** Lead-in before the first beat, so the very first hit is never clipped. */
const val START_LEAD_SECONDS = 0.08

/** What the render thread reads at the start of each buffer. */
data class RenderSettings(
    val bpm: Int,
    val voice: String,
    val accentEvery: Int,
    val click: Boolean,
    /** A call is active: write silence, keep the grid, stop the run clock. */
    val silenced: Boolean,
)

fun interface BeatSink {
    fun onBeat(beat: CommittedBeat)
}

/**
 * Writes the beat into a PCM stream. The frames written are the clock: a beat
 * is a frame index in the stream, so a late writer can only underrun, never
 * misplace a beat.
 *
 * Every field but [settings] belongs to the render thread. The controller
 * replaces [settings] whole and the render thread reads it once per buffer,
 * so a change lands on the next beat not yet written.
 */
class Renderer(private val rate: Int, private val voices: VoiceKit, initial: RenderSettings) {
    @Volatile
    var settings: RenderSettings = initial

    private var bpm = clampBpm(initial.bpm)

    /** Where beat 0 of the current spacing starts, in frames. Beats are derived from it, never accumulated. */
    private var anchor = START_LEAD_SECONDS * rate
    private var nextIndex = 0
    private var nextCount = 0

    /** Start of the latest beat already mixed, exact; null until the first. */
    private var lastMixed: Double? = null

    /** Frames written so far. */
    var writeHead: Long = 0
        private set

    /** Frames written while not silenced: the run clock, which a call pauses. */
    var activeFrames: Long = 0
        private set

    val elapsedSeconds: Double get() = activeFrames.toDouble() / rate

    private class Hit(val samples: FloatArray) {
        var length = 0
        /** Frame in the stream where the hit starts. */
        var start = 0L
    }

    /** At most two hits overlap: the longest is 0.24 s and the shortest period 0.27 s. */
    private val pool = ArrayDeque(List(4) { Hit(FloatArray(voices.maxFrames)) })
    private val active = ArrayList<Hit>(4)

    private fun period(bpm: Int) = beatPeriod(bpm) * rate

    /** Fills `out[0, frames)` with the next `frames` of the stream and reports each beat it starts. */
    fun render(out: FloatArray, frames: Int, sink: BeatSink) {
        val s = settings
        val end = writeHead + frames
        if (s.bpm != bpm) changeTempo(clampBpm(s.bpm))
        out.fill(0f, 0, frames)

        while (true) {
            val start = anchor + nextIndex * period(bpm)
            val frame = floor(start + 0.5).toLong()
            if (frame >= end) break
            val count = nextCount
            nextCount += 1
            nextIndex += 1
            lastMixed = start
            val accent = s.accentEvery > 0 && count % s.accentEvery == 0
            if (s.click && !s.silenced) startHit(s.voice, accent, frame)
            sink.onBeat(CommittedBeat(frame = frame, count = count, accent = accent, silent = s.silenced))
        }

        if (s.silenced) {
            active.forEach(pool::addLast)
            active.clear()
        } else {
            mixActive(out, frames)
            activeFrames += frames
        }
        writeHead = end
    }

    /**
     * The first beat at the new spacing goes one new period after the last
     * beat mixed, or at the write head if that has already passed. On a drop
     * the gap is exactly the new period; on a rise it lies between the new
     * and the old period, because the old grid's next beat was not yet mixed.
     * So no beat lands on one already sounded, and a rise never skips a slot.
     */
    private fun changeTempo(next: Int) {
        val last = lastMixed
        if (last != null) {
            anchor = max(last + period(next), writeHead.toDouble())
            nextIndex = 0
        }
        bpm = next
    }

    private fun startHit(voice: String, accent: Boolean, frame: Long) {
        val hit = pool.removeFirstOrNull() ?: Hit(FloatArray(voices.maxFrames))
        hit.length = voices.render(voice, accent, hit.samples)
        hit.start = frame
        active.add(hit)
    }

    /** Mixes every active hit's frames inside this buffer; a hit near the end carries its tail into the next. */
    private fun mixActive(out: FloatArray, frames: Int) {
        val iterator = active.iterator()
        while (iterator.hasNext()) {
            val hit = iterator.next()
            val from = max(0L, writeHead - hit.start).toInt()
            val offset = max(0L, hit.start - writeHead).toInt()
            val count = minOf(hit.length - from, frames - offset)
            for (i in 0 until count) out[offset + i] += hit.samples[from + i]
            if (hit.start + hit.length <= writeHead + frames) {
                iterator.remove()
                pool.addLast(hit)
            }
        }
    }
}

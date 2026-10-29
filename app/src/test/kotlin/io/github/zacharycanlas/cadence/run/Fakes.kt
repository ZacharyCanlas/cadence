package io.github.zacharycanlas.cadence.run

/**
 * A track whose writer runs `latencyFrames` ahead of what is heard, so the
 * moment each stream frame sounds is known exactly. Time is simulated:
 * a write blocks, advancing [now], once the track's buffer is full.
 */
class FakeOutput(
    override val sampleRate: Int = 48_000,
    private val latencyFrames: Long = 4800,
    private val events: MutableList<String> = mutableListOf(),
) : AudioOutput {
    enum class Stamps { ALWAYS, AFTER_FIRST_SECOND, NEVER }

    var stamps = Stamps.ALWAYS
    var now = 1_000_000_000L
    val openResults = ArrayDeque<Boolean>()
    val writeResults = ArrayDeque<Int>()
    var opens = 0
        private set
    val plays = mutableListOf<Int>()
    var volume = -1f
        private set
    var loop: (() -> Boolean)? = null
        private set

    /** Successful writes carrying any non-zero sample. */
    var audibleWrites = 0
        private set

    /** Stream frames handed to write, failed writes included: what the renderer has advanced through. */
    private var offered = 0L
    private var trackBase = 0L
    private var trackStart = 0L
    private var trackWritten = 0L
    private var open = false

    private fun trackPresented(at: Long): Long =
        minOf(trackWritten, maxOf(0L, (at - trackStart) * sampleRate / 1_000_000_000L))

    /** When the listener hears `streamFrame` on the current track. */
    fun soundsAt(streamFrame: Long): Long = trackStart + (streamFrame - trackBase) * 1_000_000_000L / sampleRate

    fun trackBase(): Long = trackBase

    override fun open(): Boolean {
        events += "output.open"
        opens += 1
        if (!(openResults.removeFirstOrNull() ?: true)) return false
        open = true
        trackBase = offered
        trackStart = now
        trackWritten = 0
        return true
    }

    override fun write(buffer: FloatArray, frames: Int): Int {
        check(open) { "write to a closed track" }
        offered += frames
        val result = writeResults.removeFirstOrNull() ?: frames
        if (result < 0) return result
        if ((0 until frames).any { buffer[it] != 0f }) audibleWrites += 1
        trackWritten += frames
        val blockedUntil = trackStart + maxOf(0L, trackWritten - latencyFrames) * 1_000_000_000L / sampleRate
        now = maxOf(now, blockedUntil)
        return result
    }

    override fun close() {
        events += "output.close"
        open = false
    }

    override fun setVolume(volume: Float) {
        this.volume = volume
    }

    override fun timestamp(): OutputTimestamp? {
        if (!open) return null
        return when (stamps) {
            Stamps.NEVER -> null
            Stamps.AFTER_FIRST_SECOND -> if (now - trackStart < 1_000_000_000L) null else OutputTimestamp(trackPresented(now), now)
            Stamps.ALWAYS -> OutputTimestamp(trackPresented(now), now)
        }
    }

    override fun playbackHeadPosition(): Long = trackPresented(now)

    override fun startLoop(step: () -> Boolean) {
        events += "output.startLoop"
        loop = step
    }

    override fun stopLoop() {
        events += "output.stopLoop"
        loop = null
        close()
    }

    override fun playOnce(samples: FloatArray, frames: Int) {
        plays += frames
    }

    /** Runs the render loop `buffers` times; false once the loop has ended itself. */
    fun step(buffers: Int): Boolean {
        repeat(buffers) {
            val step = loop ?: return false
            if (!step()) {
                loop = null
                return false
            }
        }
        return true
    }
}

class FakeHaptics(override val available: Boolean = true) : Haptics {
    data class Armed(val at: Long, val delayMillis: Long, val accent: Boolean)

    val armed = mutableListOf<Armed>()
    var cancels = 0
    var clock: () -> Long = { 0L }

    override fun schedule(delayMillis: Long, accent: Boolean) {
        armed += Armed(clock(), delayMillis, accent)
    }

    override fun cancelAll() {
        cancels += 1
    }
}

class FakeHost(private val events: MutableList<String>) : RunHost {
    var mode = 0

    override fun startRun() {
        events += "host.start"
    }

    override fun stopRun() {
        events += "host.stop"
    }

    override fun audioMode(): Int = mode
}

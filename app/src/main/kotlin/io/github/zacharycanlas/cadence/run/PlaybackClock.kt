package io.github.zacharycanlas.cadence.run

/**
 * Maps now to the stream frame being heard, shared by haptics, the beat flash
 * and the step count. Extrapolates from the track's latest timestamp, and
 * falls back to the playback head while there is none: as the clock settles
 * at start, after a route change, or on a route that never provides one.
 */
class PlaybackClock(private val output: AudioOutput, private val rate: Int) {
    /** Replaced whole, so a reader never pairs one track's offset with another's timestamp. */
    private class State(val frameOffset: Long, val stamp: OutputTimestamp?, val queriedAt: Long)

    @Volatile
    private var state = State(frameOffset = 0, stamp = null, queriedAt = 0)

    /** A new track begins at stream frame `frameOffset`; its positions restart at 0. */
    fun reset(frameOffset: Long) {
        state = State(frameOffset, stamp = null, queriedAt = 0)
    }

    /** Render thread, after each write: query until a timestamp arrives, then about every 10 s. */
    fun refresh(now: Long) {
        val current = state
        if (current.stamp != null && now - current.queriedAt < REQUERY_NANOS) return
        state = State(current.frameOffset, output.timestamp(), now)
    }

    fun presentedFrame(now: Long): Long {
        val current = state
        val stamp = current.stamp ?: return current.frameOffset + output.playbackHeadPosition()
        return current.frameOffset + stamp.framePosition + (now - stamp.nanoTime) * rate / 1_000_000_000L
    }

    private companion object {
        const val REQUERY_NANOS = 10_000_000_000L
    }
}

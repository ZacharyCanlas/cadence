package io.github.zacharycanlas.cadence.core

/**
 * A beat the renderer has written into the stream. `silent` beats fell inside
 * a call: they keep the accent phase but were neither heard nor felt.
 */
data class CommittedBeat(val frame: Long, val count: Int, val accent: Boolean, val silent: Boolean)

/**
 * Holds written beats until the playback clock reaches them. Touched only on
 * the main thread.
 */
class BeatFeed {
    private val queue = ArrayDeque<CommittedBeat>()

    /** Beats written but not yet presented. */
    val pending: Int get() = queue.size

    fun enqueue(beat: CommittedBeat) {
        queue.addLast(beat)
    }

    /** Retires every beat presented by `frame` and returns the last audible one, which is the one to flash. */
    fun drainPast(frame: Long): CommittedBeat? {
        var last: CommittedBeat? = null
        while (queue.isNotEmpty() && queue.first().frame <= frame) {
            val beat = queue.removeFirst()
            if (!beat.silent) last = beat
        }
        return last
    }

    fun reset() {
        queue.clear()
    }
}

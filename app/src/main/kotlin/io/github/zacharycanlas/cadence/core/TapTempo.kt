package io.github.zacharycanlas.cadence.core

/** Taps further apart than this start a fresh measurement. */
const val TAP_TIMEOUT_MS = 2500L

/** Intervals kept in the rolling average. Enough to settle, short enough to follow a change. */
const val TAP_WINDOW = 6

/** Two taps give a jittery reading, so hold off until there are three. */
const val MIN_TAPS = 3

/** `bpm` stays null until the reading settles. */
data class TapReading(val bpm: Int?, val taps: Int)

/** Works out a cadence from the runner tapping along to their stride. Time is passed in, in milliseconds. */
class TapTempo(private val window: Int = TAP_WINDOW, private val timeoutMs: Long = TAP_TIMEOUT_MS) {
    private var lastTap: Long? = null
    private val intervals = ArrayDeque<Long>()

    val taps: Int get() = if (lastTap == null) 0 else intervals.size + 1

    fun tap(now: Long): TapReading {
        lastTap?.let { previous ->
            val gap = now - previous
            if (gap > timeoutMs || gap <= 0) {
                intervals.clear()
            } else {
                intervals.addLast(gap)
                if (intervals.size > window) intervals.removeFirst()
            }
        }
        lastTap = now

        if (intervals.size < MIN_TAPS - 1) return TapReading(bpm = null, taps = intervals.size + 1)
        return TapReading(bpm = clampBpm(60_000.0 / intervals.average()), taps = intervals.size + 1)
    }

    fun reset() {
        lastTap = null
        intervals.clear()
    }
}

package io.github.zacharycanlas.cadence.core

/**
 * The cadence you chose, and the cadence currently sounding.
 *
 * `chosen` changes only through [choose] and is the only part worth
 * persisting. `current` is what the metronome plays this second; the ramp and
 * a mid-ramp [hold] move it, and nobody should remember it afterwards. A ramp
 * that wrote its steps back into `chosen` would start the next run at its own
 * target. Outside a run the two are the same.
 */
class Cadence(startingBpm: Int) {
    var chosen: Int = clampBpm(startingBpm)
        private set
    var current: Int = chosen
        private set

    /** Where the running ramp climbs from. */
    var rampFrom: Int = chosen
        private set

    /** Set by [hold]; cleared only by [beginRun] and [rebaseRamp]. */
    var rampHeld: Boolean = false
        private set

    /** A deliberate change outside a ramp: a new choice, and where a ramp would climb from. */
    fun choose(value: Int): Int {
        chosen = clampBpm(value)
        current = chosen
        rampFrom = chosen
        return chosen
    }

    /** The ramp moving on its own. */
    fun sound(value: Int): Int {
        current = clampBpm(value)
        return current
    }

    /** A manual change during a running ramp: ends the climb for this run without touching your choice. */
    fun hold(value: Int): Int {
        current = clampBpm(value)
        rampHeld = true
        return current
    }

    fun beginRun(): Int {
        current = chosen
        rampFrom = chosen
        rampHeld = false
        return current
    }

    fun endRun(): Int {
        current = chosen
        return current
    }

    /** The ramp switched on mid-run: climb from where you actually are. */
    fun rebaseRamp(): Int {
        rampFrom = current
        rampHeld = false
        return rampFrom
    }

    /** Where the ramp should have reached by now, derived from elapsed time rather than accumulated. */
    fun rampTarget(target: Int, minutes: Int, elapsedSeconds: Double): Int =
        if (rampHeld) current else rampBpmAt(rampFrom, target, minutes, elapsedSeconds)
}

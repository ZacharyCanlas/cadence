package io.github.zacharycanlas.cadence.core

import kotlin.math.min

const val MIN_RAMP_MINUTES = 1
const val MAX_RAMP_MINUTES = 60

fun clampMinutes(value: Int): Int = value.coerceIn(MIN_RAMP_MINUTES, MAX_RAMP_MINUTES)

/** Cadence at a point in the ramp, rounded to a whole step per minute. */
fun rampBpmAt(from: Int, to: Int, minutes: Int, elapsedSeconds: Double): Int {
    val start = clampBpm(from)
    val target = clampBpm(to)
    val span = clampMinutes(minutes) * 60.0
    if (elapsedSeconds <= 0) return start
    if (elapsedSeconds >= span) return target
    return clampBpm(start + (target - start) * (elapsedSeconds / span))
}

/** Fraction of the ramp completed, 0 to 1. */
fun rampProgress(minutes: Int, elapsedSeconds: Double): Double {
    if (elapsedSeconds <= 0) return 0.0
    return min(1.0, elapsedSeconds / (clampMinutes(minutes) * 60.0))
}

fun rampDone(minutes: Int, elapsedSeconds: Double): Boolean = rampProgress(minutes, elapsedSeconds) >= 1

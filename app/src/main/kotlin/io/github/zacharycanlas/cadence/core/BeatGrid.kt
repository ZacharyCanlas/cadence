package io.github.zacharycanlas.cadence.core

import kotlin.math.roundToInt

/** Covers a walking warm-up through to fast strides. */
const val MIN_BPM = 100
const val MAX_BPM = 220
const val DEFAULT_BPM = 180

fun clampBpm(value: Int): Int = value.coerceIn(MIN_BPM, MAX_BPM)

fun clampBpm(value: Double): Int = if (value.isFinite()) clampBpm(value.roundToInt()) else DEFAULT_BPM

/** Seconds between beats. */
fun beatPeriod(bpm: Int): Double = 60.0 / bpm

/**
 * Every beat is derived from the anchor rather than accumulated, so float
 * error cannot pile up over a long run.
 */
fun beatTime(anchor: Double, bpm: Int, index: Int): Double = anchor + index * beatPeriod(bpm)

package io.github.zacharycanlas.cadence.core

import java.util.Locale

/** `m:ss`, with minutes running on past the hour ("75:12"). */
fun formatClock(seconds: Double): String {
    val whole = maxOf(0, seconds.toInt())
    return "%d:%02d".format(Locale.US, whole / 60, whole % 60)
}

package io.github.zacharycanlas.cadence.run

/** A frame position the output reports and the System.nanoTime at which it was presented. */
data class OutputTimestamp(val framePosition: Long, val nanoTime: Long)

/**
 * One audio track at a time, at the device's native rate. Never requests
 * audio focus. Everything but [startLoop] and [stopLoop] is called from the
 * render thread while a loop runs.
 */
interface AudioOutput {
    val sampleRate: Int

    /** Creates and starts a track. False when construction or play() fails. */
    fun open(): Boolean

    /** Blocking write. Returns the frames written or a negative AudioTrack error code. */
    fun write(buffer: FloatArray, frames: Int): Int

    fun close()

    /** Linear, 0 to 1. Kept across tracks. */
    fun setVolume(volume: Float)

    /** The current track's latest timestamp, or null while it has none. */
    fun timestamp(): OutputTimestamp?

    /** Frames the current track has presented. */
    fun playbackHeadPosition(): Long

    /** Runs `step` on a dedicated urgent-audio thread until it returns false. */
    fun startLoop(step: () -> Boolean)

    /** Unblocks and joins the loop thread, then closes the track. Called after the loop's run is marked over. */
    fun stopLoop()

    /** Plays one rendered hit while no run holds the output. */
    fun playOnce(samples: FloatArray, frames: Int)
}

interface Haptics {
    val available: Boolean

    /** Thread-safe: armed from the render thread. */
    fun schedule(delayMillis: Long, accent: Boolean)

    fun cancelAll()
}

/** The Android side of a run: the foreground service, and the phone's audio mode. */
interface RunHost {
    fun startRun()
    fun stopRun()

    /** AudioManager.getMode(). */
    fun audioMode(): Int
}

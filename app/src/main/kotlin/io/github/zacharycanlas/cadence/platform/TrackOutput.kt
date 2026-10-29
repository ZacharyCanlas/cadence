package io.github.zacharycanlas.cadence.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import io.github.zacharycanlas.cadence.run.AudioOutput
import io.github.zacharycanlas.cadence.run.OutputTimestamp

/**
 * The beat's AudioTrack. It never requests audio focus, so the system mixes
 * it with whatever else is playing and nothing ducks or pauses: Android's fade
 * and duck rules act only on apps that asked for focus. USAGE_MEDIA puts it on
 * the music stream, so the volume keys move it with the music.
 */
class TrackOutput(context: Context) : AudioOutput {
    override val sampleRate: Int = context.getSystemService(AudioManager::class.java)
        .getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48_000

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val format = AudioFormat.Builder()
        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
        .setSampleRate(sampleRate)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .build()

    /** Guards the track's lifecycle, which the render thread (recreating) and the main thread (stopping) both touch. */
    private val lock = Any()

    @Volatile
    private var track: AudioTrack? = null

    @Volatile
    private var volume = 1f
    private var thread: Thread? = null
    private val stamp = AudioTimestamp()

    private fun build(): AudioTrack {
        val minimum = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        return AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_NONE)
            .setBufferSizeInBytes(maxOf(minimum, sampleRate / 10 * Float.SIZE_BYTES))
            .build()
    }

    override fun open(): Boolean = synchronized(lock) {
        closeLocked()
        try {
            val created = build()
            if (created.state != AudioTrack.STATE_INITIALIZED) {
                created.release()
                return false
            }
            created.setVolume(volume)
            created.play()
            track = created
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not open the audio output", e)
            false
        }
    }

    override fun write(buffer: FloatArray, frames: Int): Int {
        val current = track ?: return AudioTrack.ERROR_INVALID_OPERATION
        return current.write(buffer, 0, frames, AudioTrack.WRITE_BLOCKING)
    }

    override fun close() = synchronized(lock) { closeLocked() }

    private fun closeLocked() {
        track?.let {
            Log.i(TAG, "Track closed after ${it.underrunCount} underruns")
            it.pause()
            it.flush()
            it.release()
        }
        track = null
    }

    override fun setVolume(volume: Float) {
        this.volume = volume
        track?.setVolume(volume)
    }

    /** Render thread only: [stamp] is reused. */
    override fun timestamp(): OutputTimestamp? {
        val current = track ?: return null
        return if (current.getTimestamp(stamp)) OutputTimestamp(stamp.framePosition, stamp.nanoTime) else null
    }

    /** The head position is an unsigned 32-bit count. */
    override fun playbackHeadPosition(): Long = (track?.playbackHeadPosition ?: 0).toLong() and 0xffff_ffffL

    override fun startLoop(step: () -> Boolean) {
        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            while (step()) Unit
        }, "cadence-render").also(Thread::start)
    }

    /**
     * Pausing and flushing empties the track, so a write already in flight,
     * and the one after it, return at once instead of blocking on a paused
     * track. The loop then sees its run is over and ends.
     */
    override fun stopLoop() {
        synchronized(lock) {
            track?.pause()
            track?.flush()
        }
        thread?.join(STOP_JOIN_MILLIS)
        thread = null
        close()
    }

    override fun playOnce(samples: FloatArray, frames: Int) {
        Thread({
            val once = try {
                build()
            } catch (e: RuntimeException) {
                Log.w(TAG, "Could not open the preview output", e)
                return@Thread
            }
            try {
                once.setVolume(volume)
                once.play()
                once.write(samples, 0, frames, AudioTrack.WRITE_BLOCKING)
                // Silence behind the hit pushes it through the track's buffer before release.
                val tail = FloatArray(sampleRate / 5)
                once.write(tail, 0, tail.size, AudioTrack.WRITE_BLOCKING)
            } finally {
                once.release()
            }
        }, "cadence-preview").start()
    }

    private companion object {
        const val TAG = "Cadence"
        const val STOP_JOIN_MILLIS = 1000L
    }
}

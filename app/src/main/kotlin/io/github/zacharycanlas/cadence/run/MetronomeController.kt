package io.github.zacharycanlas.cadence.run

import android.media.AudioManager
import android.media.AudioTrack
import io.github.zacharycanlas.cadence.core.BeatFeed
import io.github.zacharycanlas.cadence.core.BeatSink
import io.github.zacharycanlas.cadence.core.Cadence
import io.github.zacharycanlas.cadence.core.CommittedBeat
import io.github.zacharycanlas.cadence.core.Noise
import io.github.zacharycanlas.cadence.core.RenderSettings
import io.github.zacharycanlas.cadence.core.Renderer
import io.github.zacharycanlas.cadence.core.Settings
import io.github.zacharycanlas.cadence.core.SettingsStorage
import io.github.zacharycanlas.cadence.core.VoiceKit
import io.github.zacharycanlas.cadence.core.clampBpm
import io.github.zacharycanlas.cadence.core.findVoice
import io.github.zacharycanlas.cadence.core.loadSettings
import io.github.zacharycanlas.cadence.core.normalize
import io.github.zacharycanlas.cadence.core.saveSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random

enum class StopReason { Noisy, StartFailed, OutputFailed }

data class RunState(
    val running: Boolean,
    val currentBpm: Int,
    /** The run clock: audible time only, so a call pauses it. */
    val elapsedSeconds: Double,
    val steps: Int,
    val silenced: Boolean,
    val rampHeld: Boolean,
    /** Where the running ramp climbs from, and the run-clock second it started climbing. */
    val rampFrom: Int,
    val rampStartSeconds: Double,
    /** Why the last run ended on its own. Cleared by Start, a Stop and [MetronomeController.consumeStopReason]. */
    val stopReason: StopReason?,
)

/**
 * The process-wide metronome. Public functions run on the main thread, and so
 * do the feed, the cadence, the settings and every flow. The render thread
 * reaches the controller only through [onBeatWritten] and the write loop.
 */
class MetronomeController(
    private val output: AudioOutput,
    private val haptics: Haptics,
    private val storage: SettingsStorage,
    private val host: RunHost,
    private val scope: CoroutineScope,
    private val nanoTime: () -> Long = System::nanoTime,
    private val seeds: Random = Random.Default,
) {
    private val rate = output.sampleRate

    /** Settings apply at buffer boundaries, so a change waits at most this long to be written. */
    private val chunkFrames = rate / 50

    private val _settings = MutableStateFlow(loadSettings(storage))
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val cadence = Cadence(_settings.value.bpm)
    private val feed = BeatFeed()
    private val clock = PlaybackClock(output, rate)
    private val written = ConcurrentLinkedQueue<CommittedBeat>()
    private val previewKit = VoiceKit(rate, Noise.seeded(rate, Random(seeds.nextInt())))

    private val _state = MutableStateFlow(
        RunState(
            running = false, currentBpm = cadence.current, elapsedSeconds = 0.0, steps = 0,
            silenced = false, rampHeld = false, rampFrom = cadence.rampFrom, rampStartSeconds = 0.0, stopReason = null,
        ),
    )
    val state: StateFlow<RunState> = _state.asStateFlow()

    private inner class Run(val renderer: Renderer) {
        @Volatile
        var active = true
        val buffer = FloatArray(chunkFrames)

        /** Render thread only. */
        var lastDeadObject: Long? = null
        val sink = BeatSink(::onBeatWritten)
    }

    private var run: Run? = null

    /** The run clock when the last run ended, shown until the next Start. */
    private var finalElapsed = 0.0
    private var ticker: Job? = null
    private var silenced = false

    @Volatile
    private var vibrating = false

    /** False on a phone with no vibration motor. */
    val canVibrate: Boolean get() = haptics.available

    init {
        output.setVolume(_settings.value.volume / 100f)
    }

    fun start() {
        if (run != null) return
        val s = _settings.value
        cadence.beginRun()
        feed.reset()
        written.clear()
        // Fails closed: any mode but normal, including one this code has never seen, is a call.
        silenced = host.audioMode() != AudioManager.MODE_NORMAL
        val renderer = Renderer(rate, VoiceKit(rate, Noise.seeded(rate, Random(seeds.nextInt()))), renderSettings(s))
        if (!output.open()) {
            publish(stopReason = StopReason.StartFailed)
            return
        }
        clock.reset(0)
        val next = Run(renderer)
        run = next
        vibrating = s.vibrate && haptics.available
        output.startLoop { step(next) }
        host.startRun()
        ticker = scope.launch {
            while (true) {
                delay(TICK_MILLIS)
                tick()
            }
        }
        publish(stopReason = null)
    }

    /** The Stop button and the notification's Stop. */
    fun stop() = end(reason = null)

    fun toggle() = if (run == null) start() else stop()

    /** Earbuds or a headset disconnected. */
    fun onBecomingNoisy() {
        if (run != null) end(StopReason.Noisy)
    }

    /** The host could not keep the run in the foreground. */
    fun fail() {
        if (run != null) end(StopReason.OutputFailed)
    }

    /** The snackbar for [RunState.stopReason] was shown. */
    fun consumeStopReason() = publish(stopReason = null)

    fun onModeChanged(mode: Int) {
        silenced = mode != AudioManager.MODE_NORMAL
        pushRenderSettings()
        publish()
    }

    /**
     * Every manual cadence change: slider, ±1, preset, tap, notification.
     * During a running ramp it holds, leaving the chosen cadence and storage
     * alone; otherwise it is a new choice and is saved.
     */
    fun setCadence(value: Int) {
        val s = _settings.value
        val v = clampBpm(value)
        val holds = run != null && s.rampOn
        // Re-tapping the lit preset, or ±1 at the end of the range, changes nothing and must not take over
        // a ramp; outside one, a value equal to what is sounding is still saved when it differs from the choice.
        if (v == cadence.current && (holds || v == cadence.chosen)) return
        if (holds) {
            cadence.hold(value)
        } else {
            cadence.choose(value)
            update(s.copy(bpm = cadence.chosen))
        }
        pushRenderSettings()
        publish()
    }

    fun nudge(delta: Int) = setCadence(cadence.current + delta)

    fun chooseVoice(id: String) {
        update(_settings.value.copy(voice = findVoice(id)))
        if (run == null && _settings.value.click) preview(accent = false)
    }

    fun chooseAccent(every: Int) {
        update(_settings.value.copy(accentEvery = every))
        if (run == null && _settings.value.click) preview(accent = every > 0)
    }

    fun setClick(on: Boolean) = update(_settings.value.copy(click = on))

    fun setVibrate(on: Boolean) = update(_settings.value.copy(vibrate = on))

    /** 0 to 100, as the slider reads. */
    fun setVolume(value: Int) {
        update(_settings.value.copy(volume = value))
        output.setVolume(_settings.value.volume / 100f)
    }

    fun setRampOn(on: Boolean) {
        update(_settings.value.copy(rampOn = on))
        if (on) cadence.rebaseRamp(run?.renderer?.elapsedSeconds ?: 0.0)
        publish()
    }

    fun setRampTo(bpm: Int) = update(_settings.value.copy(rampTo = bpm))

    fun setRampMinutes(minutes: Int) = update(_settings.value.copy(rampMinutes = minutes))

    /**
     * Retires every beat the listener has now heard and returns the last
     * audible one, to flash. Called on each Compose frame, and by the tick so
     * the step count stays right while nothing is drawn.
     */
    fun drain(now: Long = nanoTime()): CommittedBeat? {
        if (run == null) return null
        while (true) feed.enqueue(written.poll() ?: break)
        val flashed = feed.drainPast(clock.presentedFrame(now))
        if (feed.steps != _state.value.steps) publish()
        return flashed
    }

    /** Render thread: queue the beat for the feed and arm its haptic, measured from the playback clock. */
    internal fun onBeatWritten(beat: CommittedBeat) {
        written.add(beat)
        if (vibrating && !beat.silent) {
            val delay = (beat.frame - clock.presentedFrame(nanoTime())) * 1000 / rate
            haptics.schedule(maxOf(0L, delay), beat.accent)
        }
    }

    private fun step(r: Run): Boolean {
        if (!r.active) return false
        r.renderer.render(r.buffer, chunkFrames, r.sink)
        val result = output.write(r.buffer, chunkFrames)
        // A write that returns after Stop belongs to a finished run and must not touch the next run's track.
        if (!r.active) return false
        if (result >= 0) {
            clock.refresh(nanoTime())
            return r.active
        }
        if (result == AudioTrack.ERROR_DEAD_OBJECT && recreate(r)) return r.active
        scope.launch { if (run === r) end(StopReason.OutputFailed) }
        return false
    }

    /**
     * A dead track is recreated once and the run carries on from the same
     * write head, losing at most the buffer that failed. A second within a
     * second is a broken route, and retrying it would spin.
     */
    private fun recreate(r: Run): Boolean {
        val now = nanoTime()
        val previous = r.lastDeadObject
        r.lastDeadObject = now
        if (previous != null && now - previous < 1_000_000_000L) return false
        output.close()
        if (!output.open()) return false
        clock.reset(r.renderer.writeHead)
        return true
    }

    private fun tick() {
        val r = run ?: return
        drain()
        val s = _settings.value
        if (s.rampOn) {
            val target = cadence.rampTarget(s.rampTo, s.rampMinutes, r.renderer.elapsedSeconds)
            if (target != cadence.current) {
                cadence.sound(target)
                pushRenderSettings()
            }
        }
        publish()
    }

    private fun end(reason: StopReason?) {
        val r = run ?: return
        drain()
        finalElapsed = r.renderer.elapsedSeconds
        r.active = false
        run = null
        ticker?.cancel()
        ticker = null
        vibrating = false
        output.stopLoop()
        haptics.cancelAll()
        written.clear()
        host.stopRun()
        cadence.endRun()
        publish(stopReason = reason)
    }

    private fun preview(accent: Boolean) {
        val hit = FloatArray(previewKit.maxFrames)
        val frames = previewKit.render(_settings.value.voice, accent, hit)
        output.playOnce(hit, frames)
    }

    private fun update(next: Settings) {
        val normalized = normalize(next)
        _settings.value = normalized
        saveSettings(storage, normalized)
        vibrating = run != null && normalized.vibrate && haptics.available
        pushRenderSettings()
    }

    /** "Click and vibrate never both off" holds for what the phone can do, not just what is stored. */
    private fun renderSettings(s: Settings) = RenderSettings(
        bpm = cadence.current,
        voice = s.voice,
        accentEvery = s.accentEvery,
        click = s.click || !(s.vibrate && haptics.available),
        silenced = silenced,
    )

    private fun pushRenderSettings() {
        run?.renderer?.settings = renderSettings(_settings.value)
    }

    private fun publish(stopReason: StopReason? = _state.value.stopReason) {
        val r = run
        _state.value = RunState(
            running = r != null,
            currentBpm = cadence.current,
            elapsedSeconds = r?.renderer?.elapsedSeconds ?: finalElapsed,
            steps = feed.steps,
            silenced = r != null && silenced,
            rampHeld = cadence.rampHeld,
            rampFrom = cadence.rampFrom,
            rampStartSeconds = cadence.rampStartSeconds,
            stopReason = stopReason,
        )
    }

    private companion object {
        const val TICK_MILLIS = 500L
    }
}

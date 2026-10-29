package io.github.zacharycanlas.cadence.run

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.IBinder
import android.util.Log
import io.github.zacharycanlas.cadence.CadenceApp
import io.github.zacharycanlas.cadence.MainActivity
import io.github.zacharycanlas.cadence.R
import io.github.zacharycanlas.cadence.core.Settings
import io.github.zacharycanlas.cadence.core.rampDone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Holds a run in the foreground for as long as it lasts, with its ongoing
 * notification, the earbud-disconnect receiver and the call watcher. Started,
 * never bound; the controller owns the run itself.
 */
class RunService : Service() {
    private val controller get() = (application as CadenceApp).controller
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }

    private var scope: CoroutineScope? = null

    /** The user dismissed the notification this run; Android 14 allows it, and it stays dismissed. */
    private var dismissed = false

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) controller.onBecomingNoisy()
        }
    }

    private val modeListener = AudioManager.OnModeChangedListener { mode -> controller.onModeChanged(mode) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SLOWER -> controller.nudge(-1)
            ACTION_FASTER -> controller.nudge(1)
            ACTION_STOP -> controller.stop()
            ACTION_DISMISSED -> dismissed = true
            else -> begin()
        }
        // A notification action can arrive after the run ended; there is nothing to hold then.
        if (!controller.state.value.running) stopSelf()
        return START_NOT_STICKY
    }

    private fun begin() {
        if (scope != null) return
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        try {
            startForeground(NOTIFICATION_ID, build(Content.from(controller, this)), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } catch (e: RuntimeException) {
            Log.w("Cadence", "Could not hold the run in the foreground", e)
            controller.fail()
            return
        }
        registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), RECEIVER_NOT_EXPORTED)
        audioManager.addOnModeChangedListener(mainExecutor, modeListener)
        // The mode may have changed between Start and the listener being registered.
        controller.onModeChanged(audioManager.mode)

        scope = MainScope().also { scope ->
            scope.launch {
                var posted: Content? = null
                combine(controller.state, controller.settings) { _, _ -> Content.from(controller, this@RunService) }
                    .collect { content ->
                        if (!controller.state.value.running) {
                            stopSelf()
                            return@collect
                        }
                        if (dismissed || content.sameAs(posted)) return@collect
                        notifications.notify(NOTIFICATION_ID, build(content))
                        posted = content
                    }
            }
        }
    }

    override fun onDestroy() {
        scope?.let {
            it.cancel()
            unregisterReceiver(noisy)
            audioManager.removeOnModeChangedListener(modeListener)
        }
        scope = null
        super.onDestroy()
    }

    /** What the notification shows, in seconds for the chronometer's base. */
    private data class Content(val title: String, val text: String, val chronometerBase: Long?) {
        /**
         * The base is now minus the run clock, two clocks read a moment apart,
         * so it wobbles by a second between ticks. Reposting for that would
         * jog the chronometer; a real jump, across a call or after
         * underruns, is larger.
         */
        fun sameAs(other: Content?): Boolean {
            if (other == null || title != other.title || text != other.text) return false
            val base = chronometerBase
            val otherBase = other.chronometerBase
            if (base == null || otherBase == null) return base == otherBase
            return kotlin.math.abs(base - otherBase) <= 1
        }

        companion object {
            fun from(controller: MetronomeController, context: Context): Content {
                val state = controller.state.value
                val settings: Settings = controller.settings.value
                val text = when {
                    state.silenced -> context.getString(R.string.status_paused_for_call)
                    !settings.rampOn -> context.getString(R.string.status_running)
                    state.rampHeld -> context.getString(R.string.status_ramp_held, state.currentBpm)
                    rampDone(settings.rampMinutes, state.elapsedSeconds - state.rampStartSeconds) -> context.getString(R.string.status_ramp_holding, settings.rampTo)
                    else -> context.getString(R.string.status_ramp_climbing, settings.rampTo)
                }
                val base = if (state.silenced) null else (System.currentTimeMillis() - (state.elapsedSeconds * 1000).toLong()) / 1000
                val title = context.resources.getQuantityString(R.plurals.notification_title, state.currentBpm, state.currentBpm)
                return Content(title, text, base)
            }
        }
    }

    private fun build(content: Content): Notification {
        fun action(icon: Int, title: Int, action: String) = Notification.Action.Builder(
            Icon.createWithResource(this, icon),
            getString(title),
            PendingIntent.getService(this, action.hashCode(), Intent(this, RunService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE),
        ).setAuthenticationRequired(false).build()

        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_WORKOUT)
            .apply {
                if (content.chronometerBase != null) {
                    setUsesChronometer(true)
                    setShowWhen(true)
                    setWhen(content.chronometerBase * 1000)
                } else {
                    setUsesChronometer(false)
                    setShowWhen(false)
                }
            }
            .setContentIntent(
                PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
            )
            .setDeleteIntent(
                PendingIntent.getService(this, 1, Intent(this, RunService::class.java).setAction(ACTION_DISMISSED), PendingIntent.FLAG_IMMUTABLE),
            )
            .addAction(action(R.drawable.ic_slower, R.string.action_slower, ACTION_SLOWER))
            .addAction(action(R.drawable.ic_faster, R.string.action_faster, ACTION_FASTER))
            .addAction(action(R.drawable.ic_stop, R.string.action_stop, ACTION_STOP))
            .build()
    }

    companion object {
        private const val CHANNEL = "run"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_SLOWER = "io.github.zacharycanlas.cadence.SLOWER"
        private const val ACTION_FASTER = "io.github.zacharycanlas.cadence.FASTER"
        private const val ACTION_STOP = "io.github.zacharycanlas.cadence.STOP"
        private const val ACTION_DISMISSED = "io.github.zacharycanlas.cadence.DISMISSED"
    }
}

/** Starts and stops [RunService]. A start the system refuses fails the run instead of leaving it unheld. */
class ServiceHost(private val context: Context, private val onFailure: () -> Unit) : RunHost {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val main = android.os.Handler(context.mainLooper)

    override fun startRun() {
        try {
            context.startForegroundService(Intent(context, RunService::class.java))
        } catch (e: RuntimeException) {
            Log.w("Cadence", "Could not start the run service", e)
            main.post(onFailure)
        }
    }

    /**
     * The service stops itself once the run is over. Stopping it from here
     * could land before it has called startForeground, which Android
     * punishes by killing the app.
     */
    override fun stopRun() = Unit

    override fun audioMode(): Int = audioManager.mode
}

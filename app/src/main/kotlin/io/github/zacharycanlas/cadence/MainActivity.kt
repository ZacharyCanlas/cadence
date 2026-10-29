package io.github.zacharycanlas.cadence

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.zacharycanlas.cadence.core.TapTempo
import io.github.zacharycanlas.cadence.run.MetronomeController
import io.github.zacharycanlas.cadence.run.StopReason
import io.github.zacharycanlas.cadence.ui.CadenceScreen
import io.github.zacharycanlas.cadence.ui.CadenceTheme
import io.github.zacharycanlas.cadence.ui.ScreenActions
import io.github.zacharycanlas.cadence.ui.ScreenExtras
import io.github.zacharycanlas.cadence.ui.TapHint
import kotlinx.coroutines.isActive

/** How long the beat stays lit: a held colour, so it reads the same with animations off. */
private const val FLASH_NANOS = 120_000_000L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The cobalt band sits under the status bar in both themes, so its icons are light in both.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val controller = (application as CadenceApp).controller
        setContent { CadenceTheme { RunScreen(controller) } }
    }
}

@Composable
private fun RunScreen(controller: MetronomeController) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val state by controller.state.collectAsStateWithLifecycle()
    val settings by controller.settings.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val tapTempo = remember { TapTempo() }
    var tapHint by remember { mutableStateOf<TapHint>(TapHint.Idle) }
    var flash by remember { mutableStateOf<Int?>(null) }
    var announcement by remember { mutableStateOf("") }
    var notificationsDenied by remember { mutableStateOf(false) }

    fun permitted() = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    LifecycleResumeEffect(Unit) {
        notificationsDenied = !permitted()
        onPauseOrDispose { }
    }

    // The run starts whatever the answer: notifications only add Stop to the lock screen.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsDenied = !granted
    }

    // Each frame drains what the listener has now heard and lights the last beat for a moment.
    LaunchedEffect(state.running) {
        if (!state.running) {
            flash = null
            return@LaunchedEffect
        }
        var litAt = 0L
        while (isActive) {
            val now = withFrameNanos { it }
            val beat = controller.drain()
            if (beat != null) {
                val every = controller.settings.value.accentEvery
                flash = if (every > 0) beat.count % every else 0
                litAt = now
            } else if (flash != null && now - litAt > FLASH_NANOS) {
                flash = null
            }
        }
    }

    var announced by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(state.running) {
        if (announced != null && announced != state.running) {
            announcement = if (state.running) {
                resources.getString(R.string.announce_started, state.currentBpm)
            } else {
                resources.getString(R.string.announce_stopped)
            }
        }
        announced = state.running
    }

    // Consumed as soon as it is taken for display, so a recreated Activity never shows it twice.
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.stopReason) {
        val reason = state.stopReason ?: return@LaunchedEffect
        message = resources.getString(
            when (reason) {
                StopReason.Noisy -> R.string.stopped_noisy
                StopReason.StartFailed -> R.string.start_failed
                StopReason.OutputFailed -> R.string.stopped_failed
            },
        )
        controller.consumeStopReason()
    }
    LaunchedEffect(message) {
        message?.let { snackbars.showSnackbar(it) }
        message = null
    }

    val view = LocalView.current
    SideEffect { view.keepScreenOn = state.running }

    val actions = remember(controller) {
        ScreenActions(
            toggleRun = {
                if (!controller.state.value.running && !permitted()) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                controller.toggle()
            },
            setCadence = controller::setCadence,
            nudge = controller::nudge,
            tap = {
                val reading = tapTempo.tap(SystemClock.uptimeMillis())
                tapHint = reading.bpm?.let(TapHint::Reading) ?: TapHint.Counting(reading.taps)
                reading.bpm?.also(controller::setCadence)
            },
            chooseVoice = controller::chooseVoice,
            chooseAccent = controller::chooseAccent,
            setClick = controller::setClick,
            setVibrate = controller::setVibrate,
            setVolume = controller::setVolume,
            setRampOn = controller::setRampOn,
            setRampTo = controller::setRampTo,
            setRampMinutes = controller::setRampMinutes,
            openNotificationSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            },
        )
    }

    CadenceScreen(
        state = state,
        settings = settings,
        extras = ScreenExtras(
            flashPosition = flash,
            canVibrate = controller.canVibrate,
            notificationsDenied = notificationsDenied,
            tapHint = tapHint,
        ),
        actions = actions,
        snackbars = snackbars,
        announcement = announcement,
    )
}

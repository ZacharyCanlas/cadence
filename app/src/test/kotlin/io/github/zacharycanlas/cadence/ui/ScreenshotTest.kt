package io.github.zacharycanlas.cadence.ui

import androidx.compose.material3.SnackbarHostState
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.NightMode
import io.github.zacharycanlas.cadence.core.DEFAULT_SETTINGS
import io.github.zacharycanlas.cadence.run.RunState
import org.junit.Rule
import org.junit.Test

/**
 * The design review's evidence: the run screen mid-ramp in light, dark and
 * the two large font scales, as the first viewport and as the whole scroll.
 * Rendered on the JVM by layoutlib, so no emulator is needed. The state is
 * demonstration data: a run 4 minutes into a 150 to 180 ramp.
 */
class ScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_7)

    private val settings = DEFAULT_SETTINGS.copy(bpm = 150, accentEvery = 4, rampOn = true, rampTo = 180, rampMinutes = 10, vibrate = true)
    private val state = RunState(
        running = true, currentBpm = 162, elapsedSeconds = 4 * 60 + 12.0, steps = 652,
        silenced = false, rampHeld = false, rampFrom = 150, stopReason = null,
    )

    private val actions = ScreenActions({}, {}, {}, { null }, {}, {}, {}, {}, {}, {}, {}, {}, {})

    private fun shoot(name: String, night: Boolean, fontScale: Float) {
        val device = DeviceConfig.PIXEL_7.copy(
            nightMode = if (night) NightMode.NIGHT else NightMode.NOTNIGHT,
            fontScale = fontScale,
        )
        // The whole scroll is shot on a frame tall enough to hold it: a scroll
        // rendering mode measures the scrolling column with infinite height.
        val tall = device.copy(screenHeight = (device.screenHeight * fontScale * 2.6f).toInt())
        for ((suffix, frame) in listOf("viewport" to device, "full" to tall)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = frame, renderingMode = SessionParams.RenderingMode.NORMAL)
            paparazzi.snapshot("$name-$suffix") {
                CadenceTheme {
                    CadenceScreen(
                        state = state,
                        settings = settings,
                        extras = ScreenExtras(flashPosition = 0, canVibrate = true, notificationsDenied = false, tapHint = TapHint.Idle),
                        actions = actions,
                        snackbars = SnackbarHostState(),
                        announcement = "",
                    )
                }
            }
        }
    }

    @Test
    fun light() = shoot("light", night = false, fontScale = 1f)

    @Test
    fun dark() = shoot("dark", night = true, fontScale = 1f)

    @Test
    fun fontScale130() = shoot("font-1.3", night = false, fontScale = 1.3f)

    @Test
    fun fontScale200() = shoot("font-2.0", night = false, fontScale = 2f)
}

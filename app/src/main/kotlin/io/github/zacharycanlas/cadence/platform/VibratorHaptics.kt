package io.github.zacharycanlas.cadence.platform

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import io.github.zacharycanlas.cadence.run.Haptics

/**
 * USAGE_ALARM because it is on both of the system's allowlists a run needs:
 * haptics from a background process, and haptics under Battery Saver, which
 * drops USAGE_MEDIA. It follows the phone's alarm vibration setting.
 */
class VibratorHaptics(context: Context) : Haptics {
    private val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
    override val available: Boolean = vibrator.hasVibrator()

    private val handler = Handler(HandlerThread("cadence-haptics").apply { start() }.looper)
    private val attributes = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
    private val tap = VibrationEffect.createOneShot(TAP_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE)
    private val accentTap = VibrationEffect.createOneShot(ACCENT_TAP_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE)

    override fun schedule(delayMillis: Long, accent: Boolean) {
        handler.postDelayed({ vibrator.vibrate(if (accent) accentTap else tap, attributes) }, delayMillis)
    }

    override fun cancelAll() {
        handler.removeCallbacksAndMessages(null)
        vibrator.cancel()
    }

    private companion object {
        const val TAP_MILLIS = 45L
        const val ACCENT_TAP_MILLIS = 90L
    }
}

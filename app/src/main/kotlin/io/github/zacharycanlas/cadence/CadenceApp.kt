package io.github.zacharycanlas.cadence

import android.app.Application
import io.github.zacharycanlas.cadence.platform.PrefsStorage
import io.github.zacharycanlas.cadence.platform.TrackOutput
import io.github.zacharycanlas.cadence.platform.VibratorHaptics
import io.github.zacharycanlas.cadence.run.MetronomeController
import io.github.zacharycanlas.cadence.run.ServiceHost
import kotlinx.coroutines.MainScope

/** Owns the one controller, so a run outlives the Activity: closing the app from recents keeps it going. */
class CadenceApp : Application() {
    lateinit var controller: MetronomeController
        private set

    override fun onCreate() {
        super.onCreate()
        controller = MetronomeController(
            output = TrackOutput(this),
            haptics = VibratorHaptics(this),
            storage = PrefsStorage(this),
            host = ServiceHost(this) { controller.fail() },
            scope = MainScope(),
        )
    }
}

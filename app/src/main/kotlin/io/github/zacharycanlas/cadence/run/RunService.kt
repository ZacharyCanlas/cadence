package io.github.zacharycanlas.cadence.run

import android.app.Service
import android.content.Intent
import android.os.IBinder

class RunService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}

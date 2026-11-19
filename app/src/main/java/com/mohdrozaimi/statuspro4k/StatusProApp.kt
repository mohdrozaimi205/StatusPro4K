package com.mohdrozaimi.statuspro4k

import android.app.Application
import java.io.File

class StatusProApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Clean half-finished renders left behind if the app was killed mid-export.
        cacheDir.listFiles { f: File -> f.name.startsWith("render_") }?.forEach { it.delete() }
    }
}

package dev.uint.qrserv

import android.app.Application
import dev.uint.qrserv.data.Preferences

class QRServApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Preferences.init(this)
    }
}

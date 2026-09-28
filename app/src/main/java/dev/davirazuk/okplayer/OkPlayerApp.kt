package dev.davirazuk.okplayer

import android.app.Application
import dev.davirazuk.okplayer.data.Preferences

class OkPlayerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Preferences.init(this)
    }
}

package dev.ltechnologies.bloomboard

import android.app.Application
import dev.ltechnologies.bloomboard.data.AppPreferences
import dev.ltechnologies.bloomboard.hid.HidController
import dev.ltechnologies.bloomboard.tv.TvRemoteController

class BloomboardApp : Application() {
    lateinit var preferences: AppPreferences
        private set
    lateinit var hid: HidController
        private set
    lateinit var tv: TvRemoteController
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
        hid = HidController(this)
        tv = TvRemoteController(this, preferences)
    }
}

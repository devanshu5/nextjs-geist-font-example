package com.statussaver.app

import android.app.Application
import com.google.android.gms.ads.MobileAds

class StatusSaverApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this)
    }
}

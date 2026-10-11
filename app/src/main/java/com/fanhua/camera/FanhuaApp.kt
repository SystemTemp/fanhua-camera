package com.fanhua.camera

import android.app.Application
import com.fanhua.camera.data.LocaleManager
import com.fanhua.camera.data.UpdateManager

class FanhuaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        LocaleManager.applyStored(this)
        UpdateManager(this).cleanup()
    }
}
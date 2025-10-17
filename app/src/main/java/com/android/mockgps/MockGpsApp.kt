package com.android.mockgps

import android.app.Application
import com.android.mockgps.service.VibratorService
import com.android.mockgps.storage.StorageManager

class MockGpsApp : Application() {
    companion object {
        lateinit var shared: MockGpsApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        shared = this
        StorageManager.initialise(this)
        VibratorService.initialise(this)
    }

}
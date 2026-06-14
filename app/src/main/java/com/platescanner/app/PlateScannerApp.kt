package com.platescanner.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Application class. Hilt-enabled so [MainActivity] and any [dagger.hilt.android.lifecycle.HiltViewModel]
 * can be injected. Timber is planted in debug only.
 */
@HiltAndroidApp
class PlateScannerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}

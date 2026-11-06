
package com.nidoham.thrisa

import android.app.Application
import android.util.Log
import com.nidoham.thrisa.domain.LegacySettingsCleanup

class ThrisaApp : Application() {

    override fun onCreate() {
        super.onCreate()

        initializeApp()
    }

    private fun initializeApp() {
        LegacySettingsCleanup.clearUnsupportedSettings(this)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Thrisa initialized successfully")
        }
    }

    private companion object {
        const val TAG = "ThrisaApp"
    }
}

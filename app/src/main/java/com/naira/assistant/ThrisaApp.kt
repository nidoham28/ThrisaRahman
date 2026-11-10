
package com.naira.assistant

import android.app.Application
import android.util.Log
import com.naira.assistant.domain.LegacySettingsCleanup

class ThrisaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeApp()
    }

    private fun initializeApp() {
        runCatching {
            LegacySettingsCleanup.clearUnsupportedSettings(this)
        }.onFailure { error ->
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Settings cleanup failed", error)
            }
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Application initialized")
        }
    }

    private companion object {
        const val TAG = "ThrisaApp"
    }
}

package com.nidoham.thrisa.domain

import android.content.Context
import androidx.core.content.edit

/** Stores speech preferences locally on this device. */
class SpeechSettingsStorage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getEngine(): String = prefs.getString(KEY_ENGINE, SYSTEM_ENGINE) ?: SYSTEM_ENGINE

    fun setEngine(engine: String) {
        prefs.edit { putString(KEY_ENGINE, engine) }
    }

    private companion object {
        const val PREFS_NAME = "speech_settings"
        const val KEY_ENGINE = "tts_engine"
        const val SYSTEM_ENGINE = "SYSTEM"
    }
}

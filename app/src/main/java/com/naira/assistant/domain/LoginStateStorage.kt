package com.naira.assistant.domain

import android.content.Context
import androidx.core.content.edit

/** Last server-confirmed sign-in state, used only when the server cannot be reached. */
class LoginStateStorage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLastKnownLoggedIn(): Boolean? =
        if (prefs.contains(KEY_LOGGED_IN)) prefs.getBoolean(KEY_LOGGED_IN, false) else null

    fun saveLoggedIn(loggedIn: Boolean) {
        prefs.edit { putBoolean(KEY_LOGGED_IN, loggedIn) }
    }

    private companion object {
        const val PREFS_NAME = "auth_state"
        const val KEY_LOGGED_IN = "last_server_confirmed_logged_in"
    }
}

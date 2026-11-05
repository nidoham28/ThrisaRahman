package com.nidoham.thrisa.domain

import android.content.Context
import androidx.core.content.edit

/** Stores the assistant's single relationship setting locally on this device. */
class RoleplaySettingsStorage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getRelationType(): RelationType {
        val raw = prefs.getString(KEY_RELATION_TYPE, RelationType.DEFAULT.id)
        return RelationType.fromId(raw)
    }

    fun setRelationType(type: RelationType) {
        prefs.edit { putString(KEY_RELATION_TYPE, type.id) }
    }

    private companion object {
        const val PREFS_NAME = "roleplay_settings"
        const val KEY_RELATION_TYPE = "assistant_relation_type"
    }
}

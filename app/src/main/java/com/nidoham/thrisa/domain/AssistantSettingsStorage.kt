package com.nidoham.thrisa.domain

import android.content.Context

class AssistantSettingsStorage(context: Context) {
    fun getName(): String = name
    fun setName(value: String) { name = value }

    fun getLanguage(): AssistantLanguage = language
    fun setLanguage(value: AssistantLanguage) { language = value }

    fun getRelationship(): RelationType = relationship
    fun setRelationship(value: RelationType) { relationship = value }

    fun getVoice(): AssistantVoice = voice
    fun setVoice(value: AssistantVoice) { voice = value }

    private companion object {
        @Volatile
        var name: String = "Assistant"

        @Volatile
        var language: AssistantLanguage = AssistantLanguage.BANGLA

        @Volatile
        var relationship: RelationType = RelationType.STRANGER

        @Volatile
        var voice: AssistantVoice = AssistantVoice.FEMALE
    }
}

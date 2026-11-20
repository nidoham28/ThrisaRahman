package com.naira.assistant.core.sync.model

import com.google.firebase.database.IgnoreExtraProperties
import com.naira.assistant.domain.AssistantProvider

@IgnoreExtraProperties
data class FirebaseProviderKeyRecord(
    val provider: String = "",
    val apiKey: String = "",
    val updatedAt: Long = 0,
) {
    fun toEntry(): Pair<AssistantProvider, String>? {
        val resolvedProvider = AssistantProvider.entries.firstOrNull {
            it.name.equals(provider, ignoreCase = true)
        } ?: return null
        val resolvedKey = apiKey.trim().takeIf { it.isNotEmpty() } ?: return null
        return resolvedProvider to resolvedKey
    }

    companion object {
        fun from(provider: AssistantProvider, apiKey: String): FirebaseProviderKeyRecord =
            FirebaseProviderKeyRecord(
                provider = provider.name,
                apiKey = apiKey.trim(),
                updatedAt = System.currentTimeMillis(),
            )
    }
}

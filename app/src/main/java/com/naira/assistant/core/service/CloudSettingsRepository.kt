package com.naira.assistant.core.service

import android.content.Context
import com.naira.assistant.core.sound.SpeechEngine
import com.naira.assistant.domain.AccountSettingsStorage
import com.naira.assistant.domain.AssistantLanguage
import com.naira.assistant.domain.AssistantSettingsStorage
import com.naira.assistant.domain.AssistantVoice
import com.naira.assistant.domain.Gender
import com.naira.assistant.domain.RelationType
import com.naira.assistant.domain.SpeechSettingsStorage
import com.naira.assistant.domain.UserSettingsStorage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

internal data class CloudSettings(
    val language: AssistantLanguage,
    val speechEngine: SpeechEngine,
    val theme: String = "system",
    val fontSize: String = "medium",
    val storedHistory: Boolean = true,
    val storedMemories: Boolean = true,
    val voiceOutputEnabled: Boolean = true,
    val username: String = "",
    val displayName: String = "",
    val phone: String = "",
    val gender: Gender = Gender.PREFER_NOT_TO_SAY,
    val assistantName: String = "Assistant",
    val assistantLanguage: AssistantLanguage = AssistantLanguage.BANGLA,
    val relationship: RelationType = RelationType.DEFAULT,
    val assistantVoice: AssistantVoice = AssistantVoice.FEMALE,
)

internal data class CloudSettingsPatch(
    val language: AssistantLanguage? = null,
    val speechEngine: SpeechEngine? = null,
    val theme: String? = null,
    val fontSize: String? = null,
    val storedHistory: Boolean? = null,
    val storedMemories: Boolean? = null,
    val voiceOutputEnabled: Boolean? = null,
    val username: String? = null,
    val displayName: String? = null,
    val phone: String? = null,
    val gender: Gender? = null,
    val assistantName: String? = null,
    val assistantLanguage: AssistantLanguage? = null,
    val relationship: RelationType? = null,
    val assistantVoice: AssistantVoice? = null,
) {
    val isEmpty: Boolean
        get() = language == null && speechEngine == null && theme == null && fontSize == null &&
            storedHistory == null && storedMemories == null && voiceOutputEnabled == null &&
            username == null && displayName == null && phone == null && gender == null &&
            assistantName == null && assistantLanguage == null && relationship == null &&
            assistantVoice == null

    fun merge(override: CloudSettingsPatch): CloudSettingsPatch = CloudSettingsPatch(
        language = override.language ?: language,
        speechEngine = override.speechEngine ?: speechEngine,
        theme = override.theme ?: theme,
        fontSize = override.fontSize ?: fontSize,
        storedHistory = override.storedHistory ?: storedHistory,
        storedMemories = override.storedMemories ?: storedMemories,
        voiceOutputEnabled = override.voiceOutputEnabled ?: voiceOutputEnabled,
        username = override.username ?: username,
        displayName = override.displayName ?: displayName,
        phone = override.phone ?: phone,
        gender = override.gender ?: gender,
        assistantName = override.assistantName ?: assistantName,
        assistantLanguage = override.assistantLanguage ?: assistantLanguage,
        relationship = override.relationship ?: relationship,
        assistantVoice = override.assistantVoice ?: assistantVoice,
    )
}

internal sealed interface CloudSettingsSyncResult {
    data class Synced(val settings: CloudSettings) : CloudSettingsSyncResult
    data object SignInRequired : CloudSettingsSyncResult
    data class Failed(val message: String) : CloudSettingsSyncResult
}

internal interface CloudSettingsLocalStore {
    fun pendingPatch(userId: String): CloudSettingsPatch
    fun markPending(userId: String, patch: CloudSettingsPatch)
    fun applyRemote(userId: String, settings: CloudSettings)
    fun clearPending(userId: String, patch: CloudSettingsPatch)
}

internal interface CloudSettingsRemoteStore {
    suspend fun fetch(userId: String): CloudSettings?
    suspend fun update(userId: String, patch: CloudSettingsPatch)
}

internal class CloudSettingsRepository(
    private val local: CloudSettingsLocalStore,
    private val remote: CloudSettingsRemoteStore,
    private val currentUserId: () -> String?,
) {
    private val syncMutex = Mutex()

    fun recordChange(patch: CloudSettingsPatch) {
        currentUserId()?.let { userId -> local.markPending(userId, patch) }
    }

    suspend fun synchronize(): CloudSettingsSyncResult = syncMutex.withLock {
        val userId = currentUserId() ?: return@withLock CloudSettingsSyncResult.SignInRequired

        try {
            val current = remote.fetch(userId)
                ?: return@withLock CloudSettingsSyncResult.Failed(MISSING_SETTINGS_MESSAGE)
            val patch = local.pendingPatch(userId)

            if (!patch.isEmpty) remote.update(userId, patch)

            val synced = if (patch.isEmpty) current else {
                remote.fetch(userId)
                    ?: return@withLock CloudSettingsSyncResult.Failed(MISSING_SETTINGS_MESSAGE)
            }
            if (!patch.matches(synced)) {
                return@withLock CloudSettingsSyncResult.Failed(SAVE_FAILED_MESSAGE)
            }
            local.applyRemote(userId, synced)
            local.clearPending(userId, patch)
            CloudSettingsSyncResult.Synced(synced)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = when (e) {
                is HttpRequestException -> "Can't reach the settings server. Changes aren't saved yet; retry when connected."
                else -> "Couldn't sync settings. Changes aren't saved yet; retry."
            }
            CloudSettingsSyncResult.Failed(message)
        }
    }

    private companion object {
        const val MISSING_SETTINGS_MESSAGE =
            "Cloud settings aren't set up for this account yet."
        const val SAVE_FAILED_MESSAGE =
            "The server didn't confirm your settings. Changes aren't saved yet; retry."
    }
}

private fun CloudSettingsPatch.matches(settings: CloudSettings): Boolean =
    (language == null || language == settings.language) &&
        (speechEngine == null || speechEngine == settings.speechEngine) &&
        (theme == null || theme == settings.theme) &&
        (fontSize == null || fontSize == settings.fontSize) &&
        (storedHistory == null || storedHistory == settings.storedHistory) &&
        (storedMemories == null || storedMemories == settings.storedMemories) &&
        (voiceOutputEnabled == null || voiceOutputEnabled == settings.voiceOutputEnabled) &&
        (username == null || username == settings.username) &&
        (displayName == null || displayName == settings.displayName) &&
        (phone == null || phone == settings.phone) &&
        (gender == null || gender == settings.gender) &&
        (assistantName == null || assistantName == settings.assistantName) &&
        (assistantLanguage == null || assistantLanguage == settings.assistantLanguage) &&
        (relationship == null || relationship == settings.relationship) &&
        (assistantVoice == null || assistantVoice == settings.assistantVoice)

internal class InMemoryCloudSettingsLocalStore(context: Context) : CloudSettingsLocalStore {
    private val accountStorage = AccountSettingsStorage(context.applicationContext)
    private val speechStorage = SpeechSettingsStorage(context.applicationContext)
    private val assistantStorage = AssistantSettingsStorage(context.applicationContext)
    private val userSettingsStorage = UserSettingsStorage()
    private val pending = mutableMapOf<String, CloudSettingsPatch>()
    private val lock = Any()

    override fun pendingPatch(userId: String): CloudSettingsPatch =
        synchronized(lock) { pending[userId] ?: CloudSettingsPatch() }

    override fun markPending(userId: String, patch: CloudSettingsPatch) {
        synchronized(lock) {
            pending[userId] = pendingPatch(userId).merge(patch)
        }
    }

    override fun applyRemote(userId: String, settings: CloudSettings) {
        val pending = pendingPatch(userId)
        if (pending.language == null) accountStorage.setLanguage(settings.language)
        if (pending.speechEngine == null) speechStorage.setEngine(settings.speechEngine.name)
        if (pending.theme == null) userSettingsStorage.setTheme(settings.theme)
        if (pending.fontSize == null) userSettingsStorage.setFontSize(settings.fontSize)
        if (pending.storedHistory == null) userSettingsStorage.setStoredHistory(settings.storedHistory)
        if (pending.storedMemories == null) userSettingsStorage.setStoredMemories(settings.storedMemories)
        if (pending.voiceOutputEnabled == null) {
            userSettingsStorage.setVoiceOutputEnabled(settings.voiceOutputEnabled)
        }
        if (pending.username == null) accountStorage.setUserName(settings.username)
        if (pending.displayName == null) accountStorage.setDisplayName(settings.displayName)
        if (pending.phone == null) accountStorage.setPhoneNumber(settings.phone)
        if (pending.gender == null) accountStorage.setGender(settings.gender)
        if (pending.assistantName == null) assistantStorage.setName(settings.assistantName)
        if (pending.assistantLanguage == null) assistantStorage.setLanguage(settings.assistantLanguage)
        if (pending.relationship == null) assistantStorage.setRelationship(settings.relationship)
        if (pending.assistantVoice == null) assistantStorage.setVoice(settings.assistantVoice)
    }

    override fun clearPending(userId: String, patch: CloudSettingsPatch) {
        synchronized(lock) {
            val current = pendingPatch(userId)
            val remaining = current.copy(
                language = current.language.takeUnless { it == patch.language },
                speechEngine = current.speechEngine.takeUnless { it == patch.speechEngine },
                theme = current.theme.takeUnless { it == patch.theme },
                fontSize = current.fontSize.takeUnless { it == patch.fontSize },
                storedHistory = current.storedHistory.takeUnless { it == patch.storedHistory },
                storedMemories = current.storedMemories.takeUnless { it == patch.storedMemories },
                voiceOutputEnabled = current.voiceOutputEnabled.takeUnless { it == patch.voiceOutputEnabled },
                username = current.username.takeUnless { it == patch.username },
                displayName = current.displayName.takeUnless { it == patch.displayName },
                phone = current.phone.takeUnless { it == patch.phone },
                gender = current.gender.takeUnless { it == patch.gender },
                assistantName = current.assistantName.takeUnless { it == patch.assistantName },
                assistantLanguage = current.assistantLanguage.takeUnless { it == patch.assistantLanguage },
                relationship = current.relationship.takeUnless { it == patch.relationship },
                assistantVoice = current.assistantVoice.takeUnless { it == patch.assistantVoice },
            )
            if (remaining.isEmpty) pending.remove(userId) else pending[userId] = remaining
        }
    }
}

internal class SupabaseCloudSettingsRemoteStore(
    private val client: SupabaseClient = Supabase.client,
) : CloudSettingsRemoteStore {
    override suspend fun fetch(userId: String): CloudSettings? {
        val settings = client.from("user_settings")
            .select(columns = Columns.list(
                "uid", "speech_engine", "theme", "font_size", "language",
                "stored_history", "stored_memories", "voice_output_enabled",
            )) {
                filter { eq("uid", userId) }
            }
            .decodeList<RemoteSettingsRow>()
        val profile = client.from("user_profiles")
            .select(columns = Columns.list("username", "display_name", "phone", "gender")) {
                filter { eq("id", userId) }
            }
            .decodeList<RemoteProfileRow>()
        val assistant = client.from("user_assistant")
            .select(columns = Columns.list("name", "language", "relationship", "voice_type")) {
                filter { eq("uid", userId) }
            }
            .decodeList<RemoteAssistantRow>()
        check(settings.size <= 1 && profile.size <= 1 && assistant.size <= 1) {
            "A settings table returned multiple rows for one account"
        }
        val settingsRow = settings.singleOrNull() ?: return null
        val profileRow = profile.singleOrNull()
            ?: error("The account profile isn't available.")
        val assistantRow = assistant.singleOrNull()
            ?: error("The assistant profile isn't available.")
        return settingsRow.toCloudSettings(profileRow, assistantRow)
    }

    override suspend fun update(userId: String, patch: CloudSettingsPatch) {
        if (patch.hasSettingsFields()) {
            client.from("user_settings").update({
                patch.language?.let { set("language", it.language.id) }
                patch.speechEngine?.let { set("speech_engine", it.toDatabaseValue()) }
                patch.theme?.let { set("theme", it) }
                patch.fontSize?.let { set("font_size", it) }
                patch.storedHistory?.let { set("stored_history", it) }
                patch.storedMemories?.let { set("stored_memories", it) }
                patch.voiceOutputEnabled?.let { set("voice_output_enabled", it) }
            }) {
                filter { eq("uid", userId) }
            }
        }
        if (patch.hasProfileFields()) {
            client.from("user_profiles").update({
                patch.username?.let { set("username", it) }
                patch.displayName?.let { set("display_name", it) }
                patch.phone?.let { set("phone", it.ifBlank { null }) }
                patch.gender?.let { set("gender", it.id) }
            }) {
                filter { eq("id", userId) }
            }
        }
        if (patch.hasAssistantFields()) {
            client.from("user_assistant").update({
                patch.assistantName?.let { set("name", it) }
                patch.assistantLanguage?.let { set("language", it.language.id) }
                patch.relationship?.let { set("relationship", it.id) }
                patch.assistantVoice?.let { set("voice_type", it.name.lowercase()) }
            }) {
                filter { eq("uid", userId) }
            }
        }
    }

    private fun RemoteSettingsRow.toCloudSettings(
        profile: RemoteProfileRow,
        assistant: RemoteAssistantRow,
    ) = CloudSettings(
        language = language.toAssistantLanguage(),
        speechEngine = speechEngine.toSpeechEngine(),
        theme = theme,
        fontSize = fontSize,
        storedHistory = storedHistory,
        storedMemories = storedMemories,
        voiceOutputEnabled = voiceOutputEnabled,
        username = profile.username.orEmpty(),
        displayName = profile.displayName.orEmpty(),
        phone = profile.phone.orEmpty(),
        gender = Gender.fromId(profile.gender),
        assistantName = assistant.name,
        assistantLanguage = assistant.language.toAssistantLanguage(),
        relationship = RelationType.fromId(assistant.relationship),
        assistantVoice = AssistantVoice.fromStorageValue(assistant.voiceType.uppercase()),
    )

    private fun AssistantLanguage.toDatabaseValue(): String = language.id

    private fun SpeechEngine.toDatabaseValue(): String = when (this) {
        SpeechEngine.SYSTEM -> "system"
        SpeechEngine.ADVANCED -> "gemini"
    }

    private fun String.toAssistantLanguage(): AssistantLanguage =
        AssistantLanguage.fromDatabaseId(this)

    private fun String.toSpeechEngine(): SpeechEngine = when (lowercase()) {
        "system" -> SpeechEngine.SYSTEM
        "gemini" -> SpeechEngine.ADVANCED
        else -> error("The settings service returned an unsupported speech engine")
    }

    private fun CloudSettingsPatch.hasSettingsFields() =
        language != null || speechEngine != null || theme != null || fontSize != null ||
            storedHistory != null || storedMemories != null || voiceOutputEnabled != null

    private fun CloudSettingsPatch.hasProfileFields() =
        username != null || displayName != null || phone != null || gender != null

    private fun CloudSettingsPatch.hasAssistantFields() =
        assistantName != null || assistantLanguage != null || relationship != null ||
            assistantVoice != null

    @Serializable
    @JsonIgnoreUnknownKeys
    private data class RemoteSettingsRow(
        @SerialName("language") val language: String,
        @SerialName("speech_engine") val speechEngine: String,
        @SerialName("theme") val theme: String,
        @SerialName("font_size") val fontSize: String,
        @SerialName("stored_history") val storedHistory: Boolean,
        @SerialName("stored_memories") val storedMemories: Boolean,
        @SerialName("voice_output_enabled") val voiceOutputEnabled: Boolean,
    )

    @Serializable
    @JsonIgnoreUnknownKeys
    private data class RemoteProfileRow(
        @SerialName("username") val username: String? = null,
        @SerialName("display_name") val displayName: String? = null,
        @SerialName("phone") val phone: String? = null,
        @SerialName("gender") val gender: String? = null,
    )

    @Serializable
    @JsonIgnoreUnknownKeys
    private data class RemoteAssistantRow(
        @SerialName("name") val name: String,
        @SerialName("language") val language: String,
        @SerialName("relationship") val relationship: String,
        @SerialName("voice_type") val voiceType: String,
    )
}

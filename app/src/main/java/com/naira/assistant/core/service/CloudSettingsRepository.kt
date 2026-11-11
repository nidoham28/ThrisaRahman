package com.naira.assistant.core.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
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
import kotlinx.serialization.json.Json

private const val TAG = "CloudSettings"

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

/**
 * Distinguishes "the server-side rows for this account don't exist yet" from
 * other sync failures. The backend is expected to seed one row in each of
 * `user_settings`, `user_profiles`, and `user_assistant` on signup; if any is
 * missing, we surface a clearer user message instead of the generic
 * "Couldn't sync settings".
 */
private class CloudSettingsNotSetUpException(message: String) : Exception(message)

internal class CloudSettingsRepository(
    private val local: CloudSettingsLocalStore,
    private val remote: CloudSettingsRemoteStore,
    private val currentUserId: () -> String?,
) {
    // One process-wide lock, shared by every repository instance (MainScreen
    // and SettingsScreen each construct their own). Two concurrent synchronize()
    // calls against the same remote rows would interleave fetch/update/apply
    // and race each other's pending state.
    private companion object {
        val syncMutex = Mutex()

        const val MISSING_SETTINGS_MESSAGE =
            "Cloud settings aren't set up for this account yet."
        const val SAVE_FAILED_MESSAGE =
            "The server didn't confirm your settings. Changes aren't saved yet; retry."
    }

    /**
     * Queues [patch] to be pushed to the cloud on the next [synchronize].
     *
     * **Silent no-op when signed out.** The caller is expected to have already
     * persisted the change locally (via `AccountSettingsStorage` etc.), so
     * local state stays correct — but the change will not reach the cloud
     * until the user signs back in and `recordChange` + `synchronize` are
     * called again. If you need to warn the user about unsynced changes at
     * sign-out time, check [hasPendingChanges] first.
     */
    fun recordChange(patch: CloudSettingsPatch) {
        currentUserId()?.let { userId -> local.markPending(userId, patch) }
    }

    /** Whether the given account has unsynced local changes waiting. */
    fun hasPendingChanges(): Boolean {
        val userId = currentUserId() ?: return false
        return !local.pendingPatch(userId).isEmpty
    }

    /**
     * Synchronizes local pending changes with the cloud.
     *
     * ### Contract
     * - If [CloudSettingsSyncResult.Synced] is returned, its `settings` reflect
     *   server state **at the time of the second fetch**. If the user made
     *   further changes mid-sync, those remain pending and are NOT reflected
     *   in the returned `settings` — callers that want the freshest state
     *   should re-read from local storage (which preserves pending changes)
     *   rather than trust the returned snapshot blindly.
     * - If `Failed`, the pending patch is **preserved** so the next call
     *   retries the same changes.
     * - Concurrency-safe: serialised via [syncMutex].
     */
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

            // Lenient verification. Strict equality produced false positives
            // whenever the server normalised a string (trim/case) even though
            // the write succeeded — causing every retry to look like a failure.
            // We still check (to catch silent RLS rejections returning 0 rows),
            // but with trim + case-insensitive comparison for strings.
            if (!patch.isEmpty && !patch.matchesLeniently(synced)) {
                Log.w(
                    TAG,
                    "Server didn't reflect patch after update — possible RLS rejection or normalisation. " +
                            "patch=$patch synced=$synced",
                )
                return@withLock CloudSettingsSyncResult.Failed(SAVE_FAILED_MESSAGE)
            }

            local.applyRemote(userId, synced)
            local.clearPending(userId, patch)
            CloudSettingsSyncResult.Synced(synced)
        } catch (e: CancellationException) {
            throw e
        } catch (e: CloudSettingsNotSetUpException) {
            // Distinct from a generic sync failure: the user's backend rows
            // for this account haven't been seeded yet.
            Log.w(TAG, "Cloud settings not fully set up: ${e.message}")
            CloudSettingsSyncResult.Failed(MISSING_SETTINGS_MESSAGE)
        } catch (e: Exception) {
            // Log the raw exception (server messages, serialization errors,
            // schema mismatches) for diagnostics. The user-facing message is
            // intentionally generic.
            Log.w(TAG, "Cloud settings sync failed", e)
            val message = when (e) {
                is HttpRequestException ->
                    "Can't reach the settings server. Changes aren't saved yet; retry when connected."
                else ->
                    "Couldn't sync settings. Changes aren't saved yet; retry."
            }
            CloudSettingsSyncResult.Failed(message)
        }
    }
}

/**
 * Lenient variant of [CloudSettingsPatch.matches].
 *
 * - **Enums / booleans**: strict equality — the server stores and round-trips
 *   these intact, so a mismatch is a real failure (RLS rejection, wrong user,
 *   schema bug) and warrants the [CloudSettingsRepository.SAVE_FAILED_MESSAGE]
 *   path.
 * - **Strings**: trim + case-insensitive comparison. A server-side trigger may
 *   trim whitespace or normalise casing on insert/update; strict comparison
 *   would flag these as failures even though the write succeeded, producing a
 *   "couldn't save" toast on every retry while the data was actually saved.
 */
private fun CloudSettingsPatch.matchesLeniently(settings: CloudSettings): Boolean =
    (language == null || language == settings.language) &&
            (speechEngine == null || speechEngine == settings.speechEngine) &&
            (theme == null || theme.trim().equals(settings.theme.trim(), ignoreCase = true)) &&
            (fontSize == null || fontSize.trim().equals(settings.fontSize.trim(), ignoreCase = true)) &&
            (storedHistory == null || storedHistory == settings.storedHistory) &&
            (storedMemories == null || storedMemories == settings.storedMemories) &&
            (voiceOutputEnabled == null || voiceOutputEnabled == settings.voiceOutputEnabled) &&
            (username == null || username.trim().equals(settings.username.trim(), ignoreCase = true)) &&
            (displayName == null || displayName.trim().equals(settings.displayName.trim(), ignoreCase = true)) &&
            (phone == null || phone.trim().equals(settings.phone.trim(), ignoreCase = true)) &&
            (gender == null || gender == settings.gender) &&
            (assistantName == null || assistantName.trim().equals(settings.assistantName.trim(), ignoreCase = true)) &&
            (assistantLanguage == null || assistantLanguage == settings.assistantLanguage) &&
            (relationship == null || relationship == settings.relationship) &&
            (assistantVoice == null || assistantVoice == settings.assistantVoice)

/**
 * Process-wide store of pending (not yet confirmed by the server) settings
 * patches, backing every [CloudSettingsRepository] instance in the app.
 *
 * ### Why shared
 * MainScreen and SettingsScreen each construct their own repository. When the
 * pending map lived per-instance, a change queued in Settings whose upload
 * failed (offline, server hiccup) was invisible to MainScreen's next sync:
 * that sync saw an empty patch, downloaded the stale server values and
 * [applyRemote] silently reverted the user's change. Sharing one map makes
 * "retry on the next sync" work no matter which screen runs it.
 *
 * ### Why persisted
 * The patch carries the user's intent values, so mirroring it to
 * SharedPreferences lets an interrupted sync (offline change, process death)
 * upload on the next launch instead of being lost. Once the server confirms
 * the patch (see [clearPending]), the persisted copy is removed.
 */
private object CloudPendingPatchStore {
    private const val PREFS_NAME = "cloud_settings_pending"
    private const val KEY_PREFIX = "pending_"

    private val json = Json { ignoreUnknownKeys = true }
    private val pending = mutableMapOf<String, CloudSettingsPatch>()
    private val lock = Any()

    @Volatile
    private var prefs: SharedPreferences? = null

    // Storage wrappers are stateless in-memory singletons; context is only
    // needed to satisfy their constructors.
    private lateinit var accountStorage: AccountSettingsStorage
    private lateinit var speechStorage: SpeechSettingsStorage
    private lateinit var assistantStorage: AssistantSettingsStorage
    private val userSettingsStorage = UserSettingsStorage()

    /** Idempotent; the first call wins. Restores previously persisted patches. */
    fun init(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            accountStorage = AccountSettingsStorage(appContext)
            speechStorage = SpeechSettingsStorage(appContext)
            assistantStorage = AssistantSettingsStorage(appContext)
            if (prefs == null) {
                val p = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                restorePersisted(p)
                prefs = p
            }
        }
    }

    private fun restorePersisted(p: SharedPreferences) {
        for ((key, value) in p.all) {
            if (!key.startsWith(KEY_PREFIX) || value !is String) continue
            val userId = key.removePrefix(KEY_PREFIX)
            runCatching { json.decodeFromString<PendingPatchDto>(value).toPatch() }
                .getOrNull()
                ?.takeIf { !it.isEmpty }
                ?.let { pending[userId] = it }
        }
    }

    fun pendingPatch(userId: String): CloudSettingsPatch =
        synchronized(lock) { pending[userId] ?: CloudSettingsPatch() }

    fun markPending(userId: String, patch: CloudSettingsPatch) {
        synchronized(lock) {
            // Direct map access avoids re-entering the lock via pendingPatch().
            pending[userId] = (pending[userId] ?: CloudSettingsPatch()).merge(patch)
            persist(userId)
        }
    }

    /**
     * Applies [settings] to local storage, **except** for fields that are
     * still pending sync — the user's not-yet-synced intent must not be
     * overwritten by the server's older value.
     *
     * Holds [lock] for the whole method so a concurrent [markPending] on
     * another thread can't interleave between the pending checks and the
     * storage writes.
     */
    fun applyRemote(userId: String, settings: CloudSettings) {
        synchronized(lock) {
            val patch = pending[userId] ?: CloudSettingsPatch()
            if (patch.language == null) accountStorage.setLanguage(settings.language)
            if (patch.speechEngine == null) speechStorage.setEngine(settings.speechEngine.name)
            if (patch.theme == null) userSettingsStorage.setTheme(settings.theme)
            if (patch.fontSize == null) userSettingsStorage.setFontSize(settings.fontSize)
            if (patch.storedHistory == null) userSettingsStorage.setStoredHistory(settings.storedHistory)
            if (patch.storedMemories == null) userSettingsStorage.setStoredMemories(settings.storedMemories)
            if (patch.voiceOutputEnabled == null) {
                userSettingsStorage.setVoiceOutputEnabled(settings.voiceOutputEnabled)
            }
            if (patch.username == null) accountStorage.setUserName(settings.username)
            if (patch.displayName == null) accountStorage.setDisplayName(settings.displayName)
            if (patch.phone == null) accountStorage.setPhoneNumber(settings.phone)
            if (patch.gender == null) accountStorage.setGender(settings.gender)
            if (patch.assistantName == null) assistantStorage.setName(settings.assistantName)
            if (patch.assistantLanguage == null) assistantStorage.setLanguage(settings.assistantLanguage)
            if (patch.relationship == null) assistantStorage.setRelationship(settings.relationship)
            if (patch.assistantVoice == null) assistantStorage.setVoice(settings.assistantVoice)
        }
    }

    /**
     * Removes the fields captured in [patch] from pending state.
     *
     * Fields **not** in [patch] are preserved — they are changes the user made
     * mid-sync (after [CloudSettingsRepository.synchronize] took its snapshot)
     * and must be retried on the next call.
     */
    fun clearPending(userId: String, patch: CloudSettingsPatch) {
        synchronized(lock) {
            val current = pending[userId] ?: return@synchronized
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
            persist(userId)
        }
    }

    /** Best-effort mirror of [pending] to SharedPreferences. */
    private fun persist(userId: String) {
        val p = prefs ?: return
        val patch = pending[userId]
        p.edit {
            if (patch == null || patch.isEmpty) {
                remove(KEY_PREFIX + userId)
            } else {
                putString(KEY_PREFIX + userId, json.encodeToString(patch.toDto()))
            }
        }
    }
}

/**
 * Serialization shape of [CloudSettingsPatch] for persistence. Enums travel as
 * their [enum name][Enum.name]; unknown names degrade to `null` (field
 * dropped) rather than failing the restore.
 */
@Serializable
private data class PendingPatchDto(
    val language: String? = null,
    val speechEngine: String? = null,
    val theme: String? = null,
    val fontSize: String? = null,
    val storedHistory: Boolean? = null,
    val storedMemories: Boolean? = null,
    val voiceOutputEnabled: Boolean? = null,
    val username: String? = null,
    val displayName: String? = null,
    val phone: String? = null,
    val gender: String? = null,
    val assistantName: String? = null,
    val assistantLanguage: String? = null,
    val relationship: String? = null,
    val assistantVoice: String? = null,
)

private inline fun <reified E : Enum<E>> String?.toEnumOrNull(): E? =
    this?.let { value -> enumValues<E>().firstOrNull { it.name == value } }

private fun PendingPatchDto.toPatch() = CloudSettingsPatch(
    language = language.toEnumOrNull(),
    speechEngine = speechEngine.toEnumOrNull(),
    theme = theme,
    fontSize = fontSize,
    storedHistory = storedHistory,
    storedMemories = storedMemories,
    voiceOutputEnabled = voiceOutputEnabled,
    username = username,
    displayName = displayName,
    phone = phone,
    gender = gender.toEnumOrNull(),
    assistantName = assistantName,
    assistantLanguage = assistantLanguage.toEnumOrNull(),
    relationship = relationship.toEnumOrNull(),
    assistantVoice = assistantVoice.toEnumOrNull(),
)

private fun CloudSettingsPatch.toDto() = PendingPatchDto(
    language = language?.name,
    speechEngine = speechEngine?.name,
    theme = theme,
    fontSize = fontSize,
    storedHistory = storedHistory,
    storedMemories = storedMemories,
    voiceOutputEnabled = voiceOutputEnabled,
    username = username,
    displayName = displayName,
    phone = phone,
    gender = gender?.name,
    assistantName = assistantName,
    assistantLanguage = assistantLanguage?.name,
    relationship = relationship?.name,
    assistantVoice = assistantVoice?.name,
)

/**
 * [CloudSettingsLocalStore] backed by the process-wide [CloudPendingPatchStore].
 *
 * Previously named `InMemoryCloudSettingsLocalStore` and instantiated
 * per-screen; each instance kept its own pending map, so a failed upload
 * queued in one screen was invisible to the other (see
 * [CloudPendingPatchStore] for the failure mode this caused).
 */
internal class SharedCloudSettingsLocalStore(context: Context) : CloudSettingsLocalStore {
    init {
        CloudPendingPatchStore.init(context)
    }

    override fun pendingPatch(userId: String): CloudSettingsPatch =
        CloudPendingPatchStore.pendingPatch(userId)

    override fun markPending(userId: String, patch: CloudSettingsPatch) =
        CloudPendingPatchStore.markPending(userId, patch)

    override fun applyRemote(userId: String, settings: CloudSettings) =
        CloudPendingPatchStore.applyRemote(userId, settings)

    override fun clearPending(userId: String, patch: CloudSettingsPatch) =
        CloudPendingPatchStore.clearPending(userId, patch)
}

internal class SupabaseCloudSettingsRemoteStore(
    private val client: SupabaseClient = Supabase.client,
) : CloudSettingsRemoteStore {

    /**
     * Fetches the full [CloudSettings] for [userId].
     *
     * - Returns `null` when the `user_settings` row is missing (i.e. the
     *   account has no cloud settings yet).
     * - Throws [CloudSettingsNotSetUpException] when `user_profiles` or
     *   `user_assistant` rows are missing — the backend is expected to seed
     *   all three on signup.
     *
     * Row fields are nullable with fallbacks on purpose: a NULL or unexpected
     * value in one column must degrade that single setting to its default,
     * not abort the whole sync. [fetch] runs before every upload, so a throw
     * here used to break download *and* upload until the row was fixed.
     */
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

        if (settings.size > 1 || profile.size > 1 || assistant.size > 1) {
            throw CloudSettingsNotSetUpException(
                "A settings table returned multiple rows for one account",
            )
        }

        val settingsRow = settings.singleOrNull() ?: return null
        val profileRow = profile.singleOrNull()
            ?: throw CloudSettingsNotSetUpException("user_profiles row missing")
        val assistantRow = assistant.singleOrNull()
            ?: throw CloudSettingsNotSetUpException("user_assistant row missing")
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
        theme = theme ?: "system",
        fontSize = fontSize ?: "medium",
        storedHistory = storedHistory ?: true,
        storedMemories = storedMemories ?: true,
        voiceOutputEnabled = voiceOutputEnabled ?: true,
        username = profile.username.orEmpty(),
        displayName = profile.displayName.orEmpty(),
        phone = profile.phone.orEmpty(),
        gender = Gender.fromId(profile.gender),
        assistantName = assistant.name ?: "Assistant",
        assistantLanguage = assistant.language.toAssistantLanguage(),
        relationship = RelationType.fromId(assistant.relationship),
        assistantVoice = AssistantVoice.fromStorageValue(assistant.voiceType),
    )

    private fun SpeechEngine.toDatabaseValue(): String = when (this) {
        SpeechEngine.SYSTEM -> "system"
        SpeechEngine.ADVANCED -> "gemini"
    }

    /** Lenient: NULL or unknown ids fall back to the default language instead of throwing. */
    private fun String?.toAssistantLanguage(): AssistantLanguage =
        AssistantLanguage.fromDatabaseId(this)

    /** Lenient: unknown or NULL values fall back to the system engine instead of throwing. */
    private fun String?.toSpeechEngine(): SpeechEngine = when (this?.trim()?.lowercase()) {
        "gemini", "advanced" -> SpeechEngine.ADVANCED
        else -> SpeechEngine.SYSTEM
    }

    private fun CloudSettingsPatch.hasSettingsFields() =
        language != null || speechEngine != null || theme != null || fontSize != null ||
                storedHistory != null || storedMemories != null || voiceOutputEnabled != null

    private fun CloudSettingsPatch.hasProfileFields() =
        username != null || displayName != null || phone != null || gender != null

    private fun CloudSettingsPatch.hasAssistantFields() =
        assistantName != null || assistantLanguage != null || relationship != null ||
                assistantVoice != null

    /** Nullable fields: a NULL column degrades to that setting's default in [toCloudSettings]. */
    @Serializable
    @JsonIgnoreUnknownKeys
    private data class RemoteSettingsRow(
        @SerialName("language") val language: String? = null,
        @SerialName("speech_engine") val speechEngine: String? = null,
        @SerialName("theme") val theme: String? = null,
        @SerialName("font_size") val fontSize: String? = null,
        @SerialName("stored_history") val storedHistory: Boolean? = null,
        @SerialName("stored_memories") val storedMemories: Boolean? = null,
        @SerialName("voice_output_enabled") val voiceOutputEnabled: Boolean? = null,
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
        @SerialName("name") val name: String? = null,
        @SerialName("language") val language: String? = null,
        @SerialName("relationship") val relationship: String? = null,
        @SerialName("voice_type") val voiceType: String? = null,
    )
}
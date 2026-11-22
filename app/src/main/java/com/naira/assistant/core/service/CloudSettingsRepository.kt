package com.naira.assistant.core.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.naira.assistant.core.sound.SpeechEngine
import com.naira.assistant.domain.settings.AccountSettingsStorage
import com.naira.assistant.domain.settings.ApiKeyStorage
import com.naira.assistant.domain.settings.AsrProvider
import com.naira.assistant.domain.settings.AsrProviderSettingsStorage
import com.naira.assistant.domain.settings.AssistantLanguage
import com.naira.assistant.domain.settings.AssistantProvider
import com.naira.assistant.domain.settings.AssistantSettingsStorage
import com.naira.assistant.domain.settings.AssistantThinkingMode
import com.naira.assistant.domain.settings.Gender
import com.naira.assistant.domain.settings.RelationType
import com.naira.assistant.domain.settings.SpeechSettingsStorage
import com.naira.assistant.domain.settings.UserSettingsStorage
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.naira.assistant.core.sync.FirebasePaths
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "CloudSettings"

internal data class CloudSettings(
    val speechEngine: SpeechEngine,
    val activeProvider: AssistantProvider = AssistantProvider.GEMINI,
    val theme: String = "system",
    val fontSize: String = "medium",
    val storedHistory: Boolean = true,
    val storedMemories: Boolean = true,
    val voiceOutputEnabled: Boolean = true,
    val username: String = "",
    val phone: String = "",
    val gender: Gender = Gender.PREFER_NOT_TO_SAY,
    val assistantName: String = "Assistant",
    val assistantLanguage: AssistantLanguage = AssistantLanguage.BANGLA,
    val relationship: RelationType = RelationType.DEFAULT,
    val assistantThinkingMode: AssistantThinkingMode = AssistantThinkingMode.AUTO,
    val openRouterModelId: String = AssistantProvider.OPENROUTER.defaultModelId,
    val asrProvider: AsrProvider = AsrProvider.SYSTEM,
)

internal data class CloudSettingsPatch(
    val speechEngine: SpeechEngine? = null,
    val activeProvider: AssistantProvider? = null,
    val theme: String? = null,
    val fontSize: String? = null,
    val storedHistory: Boolean? = null,
    val storedMemories: Boolean? = null,
    val voiceOutputEnabled: Boolean? = null,
    val username: String? = null,
    val phone: String? = null,
    val gender: Gender? = null,
    val assistantName: String? = null,
    val assistantLanguage: AssistantLanguage? = null,
    val relationship: RelationType? = null,
    val assistantThinkingMode: AssistantThinkingMode? = null,
    val openRouterModelId: String? = null,
    val asrProvider: AsrProvider? = null,
) {
    val isEmpty: Boolean
        get() = speechEngine == null && theme == null && fontSize == null &&
                storedHistory == null && storedMemories == null && voiceOutputEnabled == null &&
                activeProvider == null &&
                username == null && phone == null && gender == null &&
                assistantName == null && assistantLanguage == null && relationship == null &&
                assistantThinkingMode == null && openRouterModelId == null && asrProvider == null

    fun merge(override: CloudSettingsPatch): CloudSettingsPatch = CloudSettingsPatch(
        speechEngine = override.speechEngine ?: speechEngine,
        activeProvider = override.activeProvider ?: activeProvider,
        theme = override.theme ?: theme,
        fontSize = override.fontSize ?: fontSize,
        storedHistory = override.storedHistory ?: storedHistory,
        storedMemories = override.storedMemories ?: storedMemories,
        voiceOutputEnabled = override.voiceOutputEnabled ?: voiceOutputEnabled,
        username = override.username ?: username,
        phone = override.phone ?: phone,
        gender = override.gender ?: gender,
        assistantName = override.assistantName ?: assistantName,
        assistantLanguage = override.assistantLanguage ?: assistantLanguage,
        relationship = override.relationship ?: relationship,
        assistantThinkingMode = override.assistantThinkingMode ?: assistantThinkingMode,
        openRouterModelId = override.openRouterModelId ?: openRouterModelId,
        asrProvider = override.asrProvider ?: asrProvider,
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
    suspend fun createIfAbsent(userId: String, defaults: CloudSettings): CloudSettings?
    suspend fun update(userId: String, patch: CloudSettingsPatch)
}

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
                ?: remote.createIfAbsent(userId, defaultCloudSettings())
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
        } catch (e: Exception) {
            // Log the raw exception (server messages, serialization errors,
            // schema mismatches) for diagnostics. The user-facing message is
            // intentionally generic.
            Log.w(TAG, "Cloud settings sync failed", e)
            val message = when (e) {
                is java.net.UnknownHostException,
                is java.net.SocketTimeoutException ->
                    "Can't reach the settings server. Changes aren't saved yet; retry when connected."
                else ->
                    "Couldn't sync settings. Changes aren't saved yet; retry."
            }
            CloudSettingsSyncResult.Failed(message)
        }
    }
}

private fun defaultCloudSettings(): CloudSettings = CloudSettings(
    speechEngine = SpeechEngine.SYSTEM,
)

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
    (speechEngine == null || speechEngine == settings.speechEngine) &&
            (activeProvider == null || activeProvider == settings.activeProvider) &&
            (theme == null || theme.trim().equals(settings.theme.trim(), ignoreCase = true)) &&
            (fontSize == null || fontSize.trim().equals(settings.fontSize.trim(), ignoreCase = true)) &&
            (storedHistory == null || storedHistory == settings.storedHistory) &&
            (storedMemories == null || storedMemories == settings.storedMemories) &&
            (voiceOutputEnabled == null || voiceOutputEnabled == settings.voiceOutputEnabled) &&
            (username == null || username.trim().equals(settings.username.trim(), ignoreCase = true)) &&
            (phone == null || phone.trim().equals(settings.phone.trim(), ignoreCase = true)) &&
            (gender == null || gender == settings.gender) &&
            (assistantName == null || assistantName.trim().equals(settings.assistantName.trim(), ignoreCase = true)) &&
            (assistantLanguage == null || assistantLanguage == settings.assistantLanguage) &&
            (relationship == null || relationship == settings.relationship) &&
            (assistantThinkingMode == null || assistantThinkingMode == settings.assistantThinkingMode) &&
            (openRouterModelId == null || openRouterModelId.trim().equals(
                settings.openRouterModelId.trim(),
                ignoreCase = true,
            )) && (asrProvider == null || asrProvider == settings.asrProvider)

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
    private lateinit var apiKeyStorage: ApiKeyStorage
    private lateinit var speechStorage: SpeechSettingsStorage
    private lateinit var assistantStorage: AssistantSettingsStorage
    private lateinit var asrStorage: AsrProviderSettingsStorage
    private val userSettingsStorage = UserSettingsStorage()

    /** Idempotent; the first call wins. Restores previously persisted patches. */
    fun init(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            accountStorage = AccountSettingsStorage(appContext)
            apiKeyStorage = ApiKeyStorage(appContext)
            speechStorage = SpeechSettingsStorage(appContext)
            assistantStorage = AssistantSettingsStorage(appContext)
            asrStorage = AsrProviderSettingsStorage(appContext)
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
            if (patch.speechEngine == null) speechStorage.setEngine(settings.speechEngine.name)
            if (patch.activeProvider == null) {
                apiKeyStorage.saveSelectedProvider(settings.activeProvider, userId)
            }
            if (patch.theme == null) userSettingsStorage.setTheme(settings.theme)
            if (patch.fontSize == null) userSettingsStorage.setFontSize(settings.fontSize)
            if (patch.storedHistory == null) userSettingsStorage.setStoredHistory(settings.storedHistory)
            if (patch.storedMemories == null) userSettingsStorage.setStoredMemories(settings.storedMemories)
            if (patch.voiceOutputEnabled == null) {
                userSettingsStorage.setVoiceOutputEnabled(settings.voiceOutputEnabled)
            }
            if (patch.username == null) accountStorage.setUserName(settings.username)
            if (patch.phone == null) accountStorage.setPhoneNumber(settings.phone)
            if (patch.gender == null) accountStorage.setGender(settings.gender)
            if (patch.assistantName == null) assistantStorage.setName(settings.assistantName)
            if (patch.assistantLanguage == null) assistantStorage.setLanguage(settings.assistantLanguage)
            if (patch.relationship == null) assistantStorage.setRelationship(settings.relationship)
            if (patch.assistantThinkingMode == null) {
                assistantStorage.setThinkingMode(settings.assistantThinkingMode)
            }
            if (patch.openRouterModelId == null) {
                apiKeyStorage.saveOpenRouterModelId(settings.openRouterModelId, userId)
            }
            if (patch.asrProvider == null) asrStorage.setProvider(settings.asrProvider)
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
                speechEngine = current.speechEngine.takeUnless { it == patch.speechEngine },
                activeProvider = current.activeProvider.takeUnless { it == patch.activeProvider },
                theme = current.theme.takeUnless { it == patch.theme },
                fontSize = current.fontSize.takeUnless { it == patch.fontSize },
                storedHistory = current.storedHistory.takeUnless { it == patch.storedHistory },
                storedMemories = current.storedMemories.takeUnless { it == patch.storedMemories },
                voiceOutputEnabled = current.voiceOutputEnabled.takeUnless { it == patch.voiceOutputEnabled },
                username = current.username.takeUnless { it == patch.username },
                phone = current.phone.takeUnless { it == patch.phone },
                gender = current.gender.takeUnless { it == patch.gender },
                assistantName = current.assistantName.takeUnless { it == patch.assistantName },
                assistantLanguage = current.assistantLanguage.takeUnless { it == patch.assistantLanguage },
                relationship = current.relationship.takeUnless { it == patch.relationship },
                assistantThinkingMode = current.assistantThinkingMode.takeUnless {
                    it == patch.assistantThinkingMode
                },
                openRouterModelId = current.openRouterModelId.takeUnless {
                    it == patch.openRouterModelId
                },
                asrProvider = current.asrProvider.takeUnless { it == patch.asrProvider },
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
    val speechEngine: String? = null,
    val activeProvider: String? = null,
    val theme: String? = null,
    val fontSize: String? = null,
    val storedHistory: Boolean? = null,
    val storedMemories: Boolean? = null,
    val voiceOutputEnabled: Boolean? = null,
    val username: String? = null,
    val phone: String? = null,
    val gender: String? = null,
    val assistantName: String? = null,
    val assistantLanguage: String? = null,
    val relationship: String? = null,
    val assistantThinkingMode: String? = null,
    val openRouterModelId: String? = null,
    val asrProvider: String? = null,
)

private inline fun <reified E : Enum<E>> String?.toEnumOrNull(): E? =
    this?.let { value -> enumValues<E>().firstOrNull { it.name == value } }

private fun PendingPatchDto.toPatch() = CloudSettingsPatch(
    speechEngine = speechEngine.toEnumOrNull(),
    activeProvider = activeProvider.toEnumOrNull(),
    theme = theme,
    fontSize = fontSize,
    storedHistory = storedHistory,
    storedMemories = storedMemories,
    voiceOutputEnabled = voiceOutputEnabled,
    username = username,
    phone = phone,
    gender = gender.toEnumOrNull(),
    assistantName = assistantName,
    assistantLanguage = assistantLanguage.toEnumOrNull(),
    relationship = relationship.toEnumOrNull(),
    assistantThinkingMode = assistantThinkingMode.toEnumOrNull(),
    openRouterModelId = openRouterModelId,
    asrProvider = asrProvider.toEnumOrNull(),
)

private fun CloudSettingsPatch.toDto() = PendingPatchDto(
    speechEngine = speechEngine?.name,
    activeProvider = activeProvider?.name,
    theme = theme,
    fontSize = fontSize,
    storedHistory = storedHistory,
    storedMemories = storedMemories,
    voiceOutputEnabled = voiceOutputEnabled,
    username = username,
    phone = phone,
    gender = gender?.name,
    assistantName = assistantName,
    assistantLanguage = assistantLanguage?.name,
    relationship = relationship?.name,
    assistantThinkingMode = assistantThinkingMode?.name,
    openRouterModelId = openRouterModelId,
    asrProvider = asrProvider?.name,
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

internal class FirebaseCloudSettingsRemoteStore(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(
        "https://naira-assistant-default-rtdb.asia-southeast1.firebasedatabase.app",
    ),
) : CloudSettingsRemoteStore {

    override suspend fun fetch(userId: String): CloudSettings? {
        val settings = FirebasePaths.cloudSettings(database, userId).get().await()
        val account = FirebasePaths.user(database, userId).get().await()
        val assistant = FirebasePaths.assistants(database, userId).get().await()
        val oldSettings = FirebasePaths.user(database, userId)
            .child("settings").child("cloud").get().await()
        if (!settings.exists() && !account.exists() && !assistant.exists() && !oldSettings.exists()) return null

        // Prefer the category-specific locations. Values still present in the
        // old combined settings node remain readable during the transition.
        val currentSettingsValues = settings.value as? Map<*, *> ?: emptyMap<Any?, Any?>()
        val legacy = (oldSettings.value as? Map<*, *>)?.let { old ->
            mutableMapOf<Any?, Any?>().apply {
                putAll(old)
                putAll(currentSettingsValues)
            }
        } ?: currentSettingsValues
        val accountValues = account.value as? Map<*, *> ?: emptyMap<Any?, Any?>()
        val assistantValues = assistant.value as? Map<*, *> ?: emptyMap<Any?, Any?>()
        val merged = legacy.toMutableMap()
        merged.putAll(accountValues.filterKeys { it in setOf("username", "phone", "gender") })
        merged["assistantName"] = assistantValues["name"] ?: merged["assistantName"]
        merged["assistantLanguage"] = assistantValues["language"] ?: merged["assistantLanguage"]
        merged["relationship"] = assistantValues["relationship"] ?: merged["relationship"]
        merged["assistantThinkingMode"] = assistantValues["thinkingMode"] ?: merged["assistantThinkingMode"]

        // Move old account and assistant fields out of user_settings atomically.
        val migration = mutableMapOf<String, Any?>()
        fun moveLegacy(key: String, path: String, destination: Map<*, *>, destinationKey: String = key) {
            val oldValue = legacy[key] ?: return
            if (destination[destinationKey] == null) migration[path] = oldValue
            migration["user_settings/$userId/$key"] = null
        }
        moveLegacy("username", "users/$userId/username", accountValues)
        moveLegacy("phone", "users/$userId/phone", accountValues)
        moveLegacy("gender", "users/$userId/gender", accountValues)
        moveLegacy("assistantName", "assistants/$userId/name", assistantValues, "name")
        moveLegacy("assistantLanguage", "assistants/$userId/language", assistantValues, "language")
        moveLegacy("relationship", "assistants/$userId/relationship", assistantValues)
        moveLegacy("assistantThinkingMode", "assistants/$userId/thinkingMode", assistantValues, "thinkingMode")
        val appSettings = setOf(
            "speechEngine", "activeProvider", "theme", "fontSize", "storedHistory",
            "storedMemories", "voiceOutputEnabled", "openRouterModelId", "asrProvider",
        )
        appSettings.forEach { key ->
            val oldValue = legacy[key] ?: return@forEach
            if (currentSettingsValues[key] == null) {
                migration["user_settings/$userId/$key"] = oldValue
            }
        }
        if (oldSettings.exists()) migration["users/$userId/settings/cloud"] = null
        if (migration.isNotEmpty()) database.reference.updateChildren(migration).await()
        return merged.toCloudSettings()
    }

    override suspend fun createIfAbsent(userId: String, defaults: CloudSettings): CloudSettings? =
        suspendCancellableCoroutine { continuation ->
            FirebasePaths.cloudSettings(database, userId).runTransaction(
                object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        if (currentData.value == null) {
                            currentData.value = defaults.toFirebaseMap()
                            return Transaction.success(currentData)
                        }
                        return Transaction.abort()
                    }

                    override fun onComplete(
                        error: DatabaseError?,
                        committed: Boolean,
                        currentData: DataSnapshot?,
                    ) {
                        if (!continuation.isActive) return
                        if (error != null) {
                            continuation.resumeWithException(error.toException())
                        } else {
                            continuation.resume(
                                (currentData?.value as? Map<*, *>)?.toCloudSettings()
                            )
                        }
                    }
                },
                false,
            )
        }

    private fun Map<*, *>.toCloudSettings(): CloudSettings {
        val data = this
        return CloudSettings(
            speechEngine = when ((data["speechEngine"] as? String)?.lowercase()) {
                "gemini", "advanced" -> SpeechEngine.ADVANCED
                else -> SpeechEngine.SYSTEM
            },
            activeProvider = AssistantProvider.fromStorageValue(data["activeProvider"] as? String),
            theme = data["theme"] as? String ?: "system",
            fontSize = data["fontSize"] as? String ?: "medium",
            storedHistory = data["storedHistory"] as? Boolean ?: true,
            storedMemories = data["storedMemories"] as? Boolean ?: true,
            voiceOutputEnabled = data["voiceOutputEnabled"] as? Boolean ?: true,
            username = data["username"] as? String ?: "",
            phone = data["phone"] as? String ?: "",
            gender = Gender.fromId(data["gender"] as? String),
            assistantName = data["assistantName"] as? String ?: "Assistant",
            assistantLanguage = AssistantLanguage.fromDatabaseId(data["assistantLanguage"] as? String),
            relationship = RelationType.fromId(data["relationship"] as? String),
            assistantThinkingMode = (data["assistantThinkingMode"] as? String)
                .toEnumOrNull<AssistantThinkingMode>() ?: AssistantThinkingMode.AUTO,
            openRouterModelId = (data["openRouterModelId"] as? String)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: AssistantProvider.OPENROUTER.defaultModelId,
            asrProvider = (data["asrProvider"] as? String).toEnumOrNull() ?: AsrProvider.SYSTEM,
        )
    }

    private fun CloudSettings.toFirebaseMap(): Map<String, Any> = mapOf(
        "speechEngine" to if (speechEngine == SpeechEngine.ADVANCED) "gemini" else "system",
        "activeProvider" to activeProvider.name.lowercase(),
        "theme" to theme,
        "fontSize" to fontSize,
        "storedHistory" to storedHistory,
        "storedMemories" to storedMemories,
        "voiceOutputEnabled" to voiceOutputEnabled,
        "openRouterModelId" to openRouterModelId,
        "asrProvider" to asrProvider.name,
    )

    override suspend fun update(userId: String, patch: CloudSettingsPatch) {
        val updates = buildMap<String, Any?> {
            fun settings(field: String, value: Any?) {
                put("user_settings/$userId/$field", value)
            }
            patch.speechEngine?.let { settings("speechEngine", if (it == SpeechEngine.ADVANCED) "gemini" else "system") }
            patch.activeProvider?.let { settings("activeProvider", it.name.lowercase()) }
            patch.theme?.let { settings("theme", it) }
            patch.fontSize?.let { settings("fontSize", it) }
            patch.storedHistory?.let { settings("storedHistory", it) }
            patch.storedMemories?.let { settings("storedMemories", it) }
            patch.voiceOutputEnabled?.let { settings("voiceOutputEnabled", it) }
            patch.openRouterModelId?.let { settings("openRouterModelId", it.trim()) }
            patch.asrProvider?.let { settings("asrProvider", it.name) }

            patch.username?.let {
                put("users/$userId/username", it)
                put("user_settings/$userId/username", null)
            }
            patch.phone?.let {
                put("users/$userId/phone", it.ifBlank { "" })
                put("user_settings/$userId/phone", null)
            }
            patch.gender?.let {
                put("users/$userId/gender", it.id)
                put("user_settings/$userId/gender", null)
            }

            patch.assistantName?.let {
                put("assistants/$userId/name", it)
                put("user_settings/$userId/assistantName", null)
            }
            patch.assistantLanguage?.let {
                put("assistants/$userId/language", it.language.id)
                put("user_settings/$userId/assistantLanguage", null)
            }
            patch.relationship?.let {
                put("assistants/$userId/relationship", it.id)
                put("user_settings/$userId/relationship", null)
            }
            patch.assistantThinkingMode?.let {
                put("assistants/$userId/thinkingMode", it.name)
                put("user_settings/$userId/assistantThinkingMode", null)
            }
        }
        if (updates.isEmpty()) return
        database.reference.updateChildren(updates).await()
    }
}

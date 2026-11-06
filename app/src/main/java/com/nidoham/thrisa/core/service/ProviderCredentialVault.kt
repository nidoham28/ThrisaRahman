package com.nidoham.thrisa.core.service

import android.content.Context
import android.content.SharedPreferences
import com.nidoham.thrisa.domain.ApiKeyStorage
import com.nidoham.thrisa.domain.AssistantProvider
import com.nidoham.thrisa.domain.DecryptedProviderCredentials
import com.nidoham.thrisa.domain.ProviderCredentialCrypto
import com.nidoham.thrisa.domain.ProviderCredentialSession
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed interface ProviderVaultOpenResult {
    data class SetupRequired(val legacyKeyCount: Int) : ProviderVaultOpenResult
    data class Unlocked(
        val credentials: Map<AssistantProvider, String>,
        val activeProvider: AssistantProvider,
        val warning: String? = null,
    ) : ProviderVaultOpenResult
    data class Failed(val message: String) : ProviderVaultOpenResult
}

internal class ProviderCredentialVault(
    context: Context,
    private val storage: ApiKeyStorage = ApiKeyStorage(context.applicationContext),
    private val remote: ProviderCredentialRemoteStore =
        SupabaseProviderCredentialRemoteStore(),
) {
    private val local = EncryptedProviderCredentialLocalStore(context.applicationContext)

    suspend fun open(
        userId: String,
        pin: CharArray,
        confirmation: CharArray?,
        legacyOwnershipConfirmed: Boolean,
    ): ProviderVaultOpenResult {
        if (!ProviderCredentialCrypto.validatePin(pin)) {
            return ProviderVaultOpenResult.Failed("Use a PIN with at least 8 digits.")
        }

        val localVault = local.read(userId)
        val remoteVault = try {
            remote.fetch(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (localVault == null) {
                return ProviderVaultOpenResult.Failed(
                    "Couldn't load encrypted provider credentials. Check your connection and retry."
                )
            }
            null
        }

        val useLocal = localVault?.pendingUpload == true
        val envelope = when {
            useLocal -> localVault.envelope
            remoteVault?.envelope != null -> remoteVault.envelope
            else -> localVault?.envelope
        }
        val activeProvider = when {
            useLocal -> localVault.activeProvider
            remoteVault?.envelope != null -> remoteVault.activeProvider
            localVault != null -> localVault.activeProvider
            else -> storage.getSelectedProvider(userId)
        }

        if (envelope == null) {
            if (confirmation == null) {
                return ProviderVaultOpenResult.SetupRequired(storage.legacyCredentials().size)
            }
            if (!confirmation.contentEquals(pin)) {
                return ProviderVaultOpenResult.Failed("The PIN entries don't match.")
            }
            val legacyCredentials = storage.legacyCredentials()
            if (legacyCredentials.isNotEmpty() && !legacyOwnershipConfirmed) {
                return ProviderVaultOpenResult.SetupRequired(legacyCredentials.size)
            }
            return createVault(userId, pin, activeProvider, legacyCredentials)
        }

        val decrypted = try {
            withContext(Dispatchers.Default) {
                ProviderCredentialCrypto.decrypt(userId, pin, envelope)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            return ProviderVaultOpenResult.Failed(e.message ?: "Couldn't unlock provider credentials.")
        } catch (e: Exception) {
            return ProviderVaultOpenResult.Failed("Couldn't decrypt provider credentials.")
        }
        ProviderCredentialSession.unlock(userId, decrypted, activeProvider)
        decrypted.encryptionKey.fill(0)
        storage.saveSelectedProvider(activeProvider, userId)

        val selected = ProviderVaultData(
            envelope = envelope,
            activeProvider = activeProvider,
            pendingUpload = false,
        )
        val expectedProviders = decrypted.credentials.keys
            .map { it.name.lowercase() }
            .sorted()
        val needsUpload = useLocal ||
            remoteVault?.envelope == null ||
            !remoteVault.envelope.contentEquals(envelope) ||
            remoteVault.activeProvider != activeProvider ||
            remoteVault.activeProviders != expectedProviders
        local.write(userId, selected.copy(pendingUpload = needsUpload))

        if (needsUpload) {
            val warning = uploadOrExplain(userId, selected)
            return ProviderVaultOpenResult.Unlocked(
                decrypted.credentials,
                activeProvider,
                warning,
            )
        }

        removeLegacyAfterCloudSave()
        return ProviderVaultOpenResult.Unlocked(decrypted.credentials, activeProvider)
    }

    suspend fun saveKey(
        userId: String,
        provider: AssistantProvider,
        key: String,
    ): ProviderVaultOpenResult {
        if (!ProviderCredentialSession.isUnlocked(userId)) {
            return ProviderVaultOpenResult.Failed("Unlock provider credentials before editing keys.")
        }
        val snapshot = ProviderCredentialSession.snapshot(userId)
            ?: return ProviderVaultOpenResult.Failed("Unlock provider credentials before editing keys.")
        val credentials = snapshot.credentials.toMutableMap()
        if (key.isBlank()) credentials.remove(provider) else credentials[provider] = key.trim()
        return persistUpdated(userId, snapshot, credentials, snapshot.activeProvider)
    }

    suspend fun setActiveProvider(
        userId: String,
        provider: AssistantProvider,
    ): ProviderVaultOpenResult {
        if (!ProviderCredentialSession.isUnlocked(userId)) {
            return ProviderVaultOpenResult.Failed("Unlock provider credentials before changing the active provider.")
        }
        val snapshot = ProviderCredentialSession.snapshot(userId)
            ?: return ProviderVaultOpenResult.Failed("Unlock provider credentials before changing the active provider.")
        return persistUpdated(userId, snapshot, snapshot.credentials, provider)
    }

    fun lock(userId: String? = null) {
        ProviderCredentialSession.lock(userId)
    }

    fun isUnlocked(userId: String): Boolean = ProviderCredentialSession.isUnlocked(userId)

    private suspend fun createVault(
        userId: String,
        pin: CharArray,
        activeProvider: AssistantProvider,
        legacyCredentials: Map<AssistantProvider, String>,
    ): ProviderVaultOpenResult {
        val (envelope, decrypted) = try {
            withContext(Dispatchers.Default) {
                ProviderCredentialCrypto.create(userId, pin, legacyCredentials)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ProviderVaultOpenResult.Failed(e.message ?: "Couldn't encrypt provider credentials.")
        }
        val data = ProviderVaultData(envelope, activeProvider, pendingUpload = true)
        try {
            local.write(userId, data)
        } catch (e: Exception) {
            decrypted.encryptionKey.fill(0)
            return ProviderVaultOpenResult.Failed(
                e.message ?: "Couldn't save encrypted provider credentials on this device."
            )
        }
        ProviderCredentialSession.unlock(userId, decrypted, activeProvider)
        decrypted.encryptionKey.fill(0)
        storage.saveSelectedProvider(activeProvider, userId)

        val warning = uploadOrExplain(userId, data)
        return ProviderVaultOpenResult.Unlocked(
            credentials = decrypted.credentials,
            activeProvider = activeProvider,
            warning = warning,
        )
    }

    private suspend fun persistUpdated(
        userId: String,
        snapshot: ProviderCredentialSession.Snapshot,
        credentials: Map<AssistantProvider, String>,
        activeProvider: AssistantProvider,
    ): ProviderVaultOpenResult {
        val envelope = try {
            withContext(Dispatchers.Default) {
                ProviderCredentialCrypto.encrypt(
                    userId,
                    snapshot.salt,
                    snapshot.encryptionKey,
                    credentials,
                )
            }
        } catch (e: CancellationException) {
            snapshot.encryptionKey.fill(0)
            snapshot.salt.fill(0)
            throw e
        } catch (e: Exception) {
            snapshot.encryptionKey.fill(0)
            snapshot.salt.fill(0)
            return ProviderVaultOpenResult.Failed("Couldn't encrypt provider credentials.")
        }
        val data = ProviderVaultData(envelope, activeProvider, pendingUpload = true)
        try {
            local.write(userId, data)
        } catch (e: Exception) {
            snapshot.encryptionKey.fill(0)
            snapshot.salt.fill(0)
            return ProviderVaultOpenResult.Failed("Couldn't save encrypted provider credentials on this device.")
        }

        val updatedSession =
            ProviderCredentialSession.update(userId, credentials, activeProvider)
        updatedSession.encryptionKey.fill(0)
        updatedSession.salt.fill(0)
        snapshot.encryptionKey.fill(0)
        snapshot.salt.fill(0)
        storage.saveSelectedProvider(activeProvider, userId)
        val warning = uploadOrExplain(userId, data)
        return ProviderVaultOpenResult.Unlocked(credentials, activeProvider, warning)
    }

    private suspend fun uploadOrExplain(userId: String, data: ProviderVaultData): String? =
        try {
            remote.save(userId, data)
            local.write(userId, data.copy(pendingUpload = false))
            removeLegacyAfterCloudSave()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            local.write(userId, data.copy(pendingUpload = true))
            "Keys are encrypted on this device, but cloud sync failed. Retry while signed in."
        }

    private fun removeLegacyAfterCloudSave() {
        if (storage.legacyCredentials().isNotEmpty()) {
            storage.clearLegacyCredentials()
        }
    }
}

internal data class ProviderVaultData(
    val envelope: ByteArray,
    val activeProvider: AssistantProvider,
    val pendingUpload: Boolean,
    val activeProviders: List<String> = emptyList(),
) {
    fun activeProviders(credentials: Map<AssistantProvider, String>): List<String> =
        credentials.filterValues(String::isNotBlank).keys.map { it.name.lowercase() }.sorted()
}

internal interface ProviderCredentialRemoteStore {
    suspend fun fetch(userId: String): ProviderVaultData?
    suspend fun save(userId: String, data: ProviderVaultData)
}

private class EncryptedProviderCredentialLocalStore(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(userId: String): ProviderVaultData? {
        val suffix = userId.lowercase()
        val encoded = preferences.getString("$ENVELOPE_PREFIX$suffix", null) ?: return null
        val envelope = try {
            Base64.getDecoder().decode(encoded)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("Local encrypted provider credentials are damaged.", e)
        }
        return ProviderVaultData(
            envelope = envelope,
            activeProvider = AssistantProvider.fromStorageValue(
                preferences.getString("$ACTIVE_PREFIX$suffix", null)
            ),
            pendingUpload = preferences.getBoolean("$PENDING_PREFIX$suffix", false),
        )
    }

    fun write(userId: String, data: ProviderVaultData) {
        val suffix = userId.lowercase()
        check(
            preferences.edit()
                .putString("$ENVELOPE_PREFIX$suffix", Base64.getEncoder().encodeToString(data.envelope))
                .putString("$ACTIVE_PREFIX$suffix", data.activeProvider.name)
                .putBoolean("$PENDING_PREFIX$suffix", data.pendingUpload)
                .commit()
        ) { "Couldn't persist encrypted provider credentials." }
    }

    private companion object {
        const val PREFERENCES_NAME = "encrypted_provider_credentials"
        const val ENVELOPE_PREFIX = "envelope_"
        const val ACTIVE_PREFIX = "active_"
        const val PENDING_PREFIX = "pending_"
    }
}

internal class SupabaseProviderCredentialRemoteStore(
    private val client: SupabaseClient = Supabase.client,
) : ProviderCredentialRemoteStore {
    override suspend fun fetch(userId: String): ProviderVaultData? {
        val rows = client.from("user_settings")
            .select(columns = Columns.list("uid", "credentials_enc", "active_provider", "active_providers")) {
                filter { eq("uid", userId) }
            }
            .decodeList<ProviderCredentialRow>()
        check(rows.size <= 1) { "Multiple provider credential rows exist for one account." }
        val row = rows.singleOrNull() ?: return null
        val envelope = row.credentialsEnc?.let(::parsePostgresBytea)
        return envelope?.let {
            ProviderVaultData(
                envelope = it,
                activeProvider = AssistantProvider.fromStorageValue(row.activeProvider),
                pendingUpload = false,
                activeProviders = row.activeProviders.map(String::lowercase).sorted(),
            )
        }
    }

    override suspend fun save(userId: String, data: ProviderVaultData) {
        val credentials = ProviderCredentialSession.snapshot(userId)?.credentials.orEmpty()
        val providers = data.activeProviders(credentials)
        client.from("user_settings").update({
            set("credentials_enc", postgresBytea(data.envelope))
            set("active_providers", providers)
            set("active_provider", data.activeProvider.name.lowercase())
        }) {
            filter { eq("uid", userId) }
        }

        val saved = fetch(userId)
        check(
            saved?.envelope?.contentEquals(data.envelope) == true &&
                saved.activeProvider == data.activeProvider &&
                saved.activeProviders == providers
        ) { "The server didn't confirm encrypted provider credentials." }
    }

    private fun parsePostgresBytea(value: String): ByteArray {
        require(value.startsWith("\\x") && value.length > 2 && value.length % 2 == 0) {
            "The server returned encrypted credentials in an unsupported format."
        }
        return ByteArray((value.length - 2) / 2) { index ->
            value.substring(2 + index * 2, 4 + index * 2).toInt(16).toByte()
        }
    }

    private fun postgresBytea(value: ByteArray): String =
        "\\x" + value.joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }

    @Serializable
    @JsonIgnoreUnknownKeys
    private data class ProviderCredentialRow(
        @SerialName("credentials_enc") val credentialsEnc: String? = null,
        @SerialName("active_provider") val activeProvider: String = "gemini",
        @SerialName("active_providers") val activeProviders: List<String> = emptyList(),
    )
}

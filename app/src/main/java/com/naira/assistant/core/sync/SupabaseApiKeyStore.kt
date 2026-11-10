package com.naira.assistant.core.sync

import com.naira.assistant.core.service.Supabase
import com.naira.assistant.core.service.SupabaseAuthService
import com.naira.assistant.domain.AssistantProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Row of the Supabase `user_provider_keys` table (see
 * `supabase/migrations/…_user_provider_keys.sql`). One row per
 * (account, provider); keys travel as plain text by design.
 */
@Serializable
@JsonIgnoreUnknownKeys
private data class ProviderKeyRow(
    val uid: String? = null,
    val provider: String = "",
    @SerialName("api_key") val apiKey: String = "",
)

/**
 * Cloud sync for provider API keys. The Supabase `user_provider_keys` table
 * is the source of truth shared across devices; the device keeps a synced
 * copy in [com.naira.assistant.domain.ApiKeyStorage] so startup reads stay
 * instant. All calls are no-ops when nobody is signed in.
 */
class SupabaseApiKeyStore(
    private val client: SupabaseClient = Supabase.client,
    private val currentUserId: () -> String? = { SupabaseAuthService().currentUser()?.id },
) {

    /**
     * Upserts [apiKey] for [provider]. Returns false when signed out or the
     * request failed.
     */
    suspend fun push(provider: AssistantProvider, apiKey: String): Boolean {
        val uid = currentUserId() ?: return false
        return try {
            client.from("user_provider_keys")
                .upsert(
                    buildJsonObject {
                        put("uid", uid)
                        put("provider", provider.name.lowercase())
                        put("api_key", apiKey.trim())
                    },
                ) {
                    onConflict = "uid,provider"
                }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /** Removes the cloud row for [provider]; returns false when signed out or failed. */
    suspend fun delete(provider: AssistantProvider): Boolean {
        val uid = currentUserId() ?: return false
        return try {
            client.from("user_provider_keys").delete {
                filter {
                    eq("uid", uid)
                    eq("provider", provider.name.lowercase())
                }
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Fetches every cloud key of the current user, keyed by provider.
     * Empty map when signed out or the request failed.
     */
    suspend fun fetchAll(): Map<AssistantProvider, String> {
        val uid = currentUserId() ?: return emptyMap()
        return try {
            client.from("user_provider_keys")
                .select {
                    filter { eq("uid", uid) }
                }
                .decodeList<ProviderKeyRow>()
                .mapNotNull { row ->
                    val provider = AssistantProvider.entries.firstOrNull {
                        it.name.equals(row.provider, ignoreCase = true)
                    } ?: return@mapNotNull null
                    val key = row.apiKey.trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    provider to key
                }
                .toMap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

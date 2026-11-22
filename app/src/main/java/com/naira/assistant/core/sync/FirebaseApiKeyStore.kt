package com.naira.assistant.core.sync

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseException
import com.google.firebase.database.FirebaseDatabase
import com.naira.assistant.core.sync.model.FirebaseProviderKeyRecord
import com.naira.assistant.domain.settings.AssistantProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class FirebaseApiKeyStore(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(),
    private val currentUserId: () -> String? = { FirebaseAuth.getInstance().currentUser?.uid },
) {
    suspend fun push(provider: AssistantProvider, apiKey: String): Boolean {
        val uid = currentUserId() ?: return false
        val key = apiKey.trim()
        require(key.isNotEmpty()) { "API key must not be blank; use delete() to remove it." }
        return try {
            providerKeysRef(uid).child(provider.name.lowercase())
                .setValue(FirebaseProviderKeyRecord.from(provider, key))
                .await()
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.e(TAG, "Couldn't save the ${provider.name} API key to Firebase", error)
            false
        }
    }

    suspend fun delete(provider: AssistantProvider): Boolean {
        val uid = currentUserId() ?: return false
        return try {
            providerKeysRef(uid).child(provider.name.lowercase()).removeValue().await()
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.e(TAG, "Couldn't delete the ${provider.name} API key from Firebase", error)
            false
        }
    }

    suspend fun fetchAll(): Map<AssistantProvider, String> {
        val uid = currentUserId() ?: return emptyMap()
        return providerKeysRef(uid).get().await().children.mapNotNull { snapshot ->
            snapshot.toProviderKey()
        }.toMap()
    }

    suspend fun pushServiceKey(service: String, apiKey: String): Boolean {
        val uid = currentUserId() ?: return false
        val key = apiKey.trim()
        require(service in SERVICE_KEYS) { "Unsupported service key." }
        if (key.isEmpty()) return deleteServiceKey(service)
        return try {
            providerKeysRef(uid).child(service).setValue(
                mapOf("provider" to service, "apiKey" to key, "updatedAt" to System.currentTimeMillis()),
            ).await()
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.e(TAG, "Couldn't save the $service key to Firebase", error)
            false
        }
    }

    suspend fun deleteServiceKey(service: String): Boolean {
        val uid = currentUserId() ?: return false
        require(service in SERVICE_KEYS) { "Unsupported service key." }
        return try {
            providerKeysRef(uid).child(service).removeValue().await()
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.e(TAG, "Couldn't delete the $service key from Firebase", error)
            false
        }
    }

    suspend fun fetchServiceKeys(): Map<String, String> {
        val uid = currentUserId() ?: return emptyMap()
        return providerKeysRef(uid).get().await().children.mapNotNull { snapshot ->
            val service = snapshot.key?.takeIf { it in SERVICE_KEYS } ?: return@mapNotNull null
            val value = (snapshot.child("apiKey").value as? String)
                ?: (snapshot.value as? String)
            value?.trim()?.takeIf(String::isNotEmpty)?.let { service to it }
        }.toMap()
    }

    private fun DataSnapshot.toProviderKey(): Pair<AssistantProvider, String>? {
        val value = value
        if (value is String) {
            val provider = AssistantProvider.entries.firstOrNull {
                it.name.equals(key, ignoreCase = true)
            } ?: return null
            val apiKey = value.trim().takeIf(String::isNotEmpty) ?: return null
            return provider to apiKey
        }
        if (value !is Map<*, *>) return null
        return try {
            getValue(FirebaseProviderKeyRecord::class.java)?.toEntry()
        } catch (error: DatabaseException) {
            Log.e(TAG, "Ignoring malformed provider key record at $key", error)
            null
        }
    }

    private fun providerKeysRef(uid: String) =
        FirebasePaths.providerKeys(database, uid)

    private companion object {
        const val TAG = "FirebaseApiKeyStore"
        val SERVICE_KEYS = setOf("cricapi", "weatherapi")
    }
}

package com.nidoham.thrisa.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal data class DecryptedProviderCredentials(
    val salt: ByteArray,
    val encryptionKey: ByteArray,
    val credentials: Map<AssistantProvider, String>,
)

internal object ProviderCredentialCrypto {
    private const val VERSION: Byte = 1
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val PBKDF2_ITERATIONS = 600_000
    private const val HEADER_BYTES = 1 + SALT_BYTES + NONCE_BYTES
    private const val MIN_PIN_LENGTH = 8
    private val random = SecureRandom()
    private val json = Json

    fun validatePin(pin: CharArray): Boolean =
        pin.size >= MIN_PIN_LENGTH && pin.all(Char::isDigit)

    fun create(
        userId: String,
        pin: CharArray,
        credentials: Map<AssistantProvider, String>,
    ): Pair<ByteArray, DecryptedProviderCredentials> {
        require(validatePin(pin)) { "Use a PIN with at least 8 digits." }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val key = deriveKey(pin, salt)
        return try {
            val envelope = encrypt(userId, salt, key, credentials)
            envelope to DecryptedProviderCredentials(salt, key, credentials.toMap())
        } catch (e: Exception) {
            key.fill(0)
            throw e
        }
    }

    fun decrypt(
        userId: String,
        pin: CharArray,
        envelope: ByteArray,
    ): DecryptedProviderCredentials {
        require(validatePin(pin)) { "Use a PIN with at least 8 digits." }
        require(envelope.size > HEADER_BYTES) { "Encrypted provider credentials are invalid." }
        require(envelope[0] == VERSION) { "This encrypted credential version isn't supported." }

        val salt = envelope.copyOfRange(1, 1 + SALT_BYTES)
        val nonce = envelope.copyOfRange(1 + SALT_BYTES, HEADER_BYTES)
        val ciphertext = envelope.copyOfRange(HEADER_BYTES, envelope.size)
        val key = deriveKey(pin, salt)
        val plaintext = try {
            cipher(Cipher.DECRYPT_MODE, key, nonce, userId).doFinal(ciphertext)
        } catch (e: AEADBadTagException) {
            key.fill(0)
            throw IllegalArgumentException("The PIN is incorrect or the encrypted data is damaged.", e)
        } catch (e: Exception) {
            key.fill(0)
            throw e
        }
        return try {
            DecryptedProviderCredentials(salt, key, decodeCredentials(plaintext))
        } catch (e: Exception) {
            key.fill(0)
            throw e
        } finally {
            plaintext.fill(0)
        }
    }

    fun encrypt(
        userId: String,
        salt: ByteArray,
        encryptionKey: ByteArray,
        credentials: Map<AssistantProvider, String>,
    ): ByteArray {
        require(salt.size == SALT_BYTES)
        require(encryptionKey.size == KEY_BITS / 8)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val plaintext = encodeCredentials(credentials)
        val encrypted = try {
            cipher(Cipher.ENCRYPT_MODE, encryptionKey, nonce, userId).doFinal(plaintext)
        } finally {
            plaintext.fill(0)
        }
        return ByteBuffer.allocate(HEADER_BYTES + encrypted.size)
            .put(VERSION)
            .put(salt)
            .put(nonce)
            .put(encrypted)
            .array()
    }

    private fun deriveKey(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray, userId: String): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            updateAAD("thrisa-provider-v1:$userId".toByteArray(StandardCharsets.UTF_8))
        }

    private fun encodeCredentials(
        credentials: Map<AssistantProvider, String>,
    ): ByteArray = JsonObject(
        credentials
            .filterValues(String::isNotBlank)
            .mapKeys { (provider, _) -> provider.name }
            .mapValues { (_, value) -> JsonPrimitive(value) }
    ).toString().toByteArray(StandardCharsets.UTF_8)

    private fun decodeCredentials(plaintext: ByteArray): Map<AssistantProvider, String> {
        val values = json.parseToJsonElement(String(plaintext, StandardCharsets.UTF_8)) as? JsonObject
            ?: throw IllegalArgumentException("Encrypted provider credentials are invalid.")
        return values.mapNotNull { (name, value) ->
            val provider = AssistantProvider.entries.firstOrNull { it.name == name }
            val credential = value.jsonPrimitive.contentOrNull
            if (provider == null || credential.isNullOrBlank()) null else provider to credential
        }.toMap()
    }
}

internal object ProviderCredentialSession {
    internal data class Snapshot(
        val userId: String,
        val salt: ByteArray,
        val encryptionKey: ByteArray,
        val credentials: MutableMap<AssistantProvider, String>,
        val activeProvider: AssistantProvider,
    )

    private var unlocked: Snapshot? = null

    @Synchronized
    fun unlock(userId: String, data: DecryptedProviderCredentials, activeProvider: AssistantProvider) {
        lock()
        unlocked = Snapshot(
            userId = userId,
            salt = data.salt.copyOf(),
            encryptionKey = data.encryptionKey.copyOf(),
            credentials = data.credentials.toMutableMap(),
            activeProvider = activeProvider,
        )
    }

    @Synchronized
    fun isUnlocked(userId: String): Boolean = unlocked?.userId == userId

    @Synchronized
    fun getKey(userId: String, provider: AssistantProvider): String? =
        unlocked?.takeIf { it.userId == userId }?.credentials?.get(provider)

    @Synchronized
    fun snapshot(userId: String): Snapshot? =
        unlocked?.takeIf { it.userId == userId }?.let {
            it.copy(
                salt = it.salt.copyOf(),
                encryptionKey = it.encryptionKey.copyOf(),
                credentials = it.credentials.toMutableMap(),
            )
        }

    @Synchronized
    fun update(
        userId: String,
        credentials: Map<AssistantProvider, String>,
        activeProvider: AssistantProvider,
    ): Snapshot {
        val old = checkNotNull(unlocked?.takeIf { it.userId == userId }) {
            "Unlock provider credentials first."
        }
        val updated = old.copy(credentials = credentials.toMutableMap(), activeProvider = activeProvider)
        unlocked = updated
        return updated.copy(
            salt = updated.salt.copyOf(),
            encryptionKey = updated.encryptionKey.copyOf(),
            credentials = updated.credentials.toMutableMap(),
        )
    }

    @Synchronized
    fun setActiveProvider(userId: String, provider: AssistantProvider): Snapshot {
        val old = checkNotNull(unlocked?.takeIf { it.userId == userId }) {
            "Unlock provider credentials first."
        }
        val updated = old.copy(activeProvider = provider)
        unlocked = updated
        return updated.copy(
            salt = updated.salt.copyOf(),
            encryptionKey = updated.encryptionKey.copyOf(),
            credentials = updated.credentials.toMutableMap(),
        )
    }

    @Synchronized
    fun lock(userId: String? = null) {
        val current = unlocked ?: return
        if (userId != null && current.userId != userId) return
        current.salt.fill(0)
        current.encryptionKey.fill(0)
        current.credentials.clear()
        unlocked = null
    }

    @Synchronized
    fun activeProvider(userId: String): AssistantProvider? =
        unlocked?.takeIf { it.userId == userId }?.activeProvider
}

package com.naira.assistant.domain.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject


enum class AsrProvider(
    val displayName: String,
    val requiresManualTurnEnd: Boolean,
) {
    SYSTEM("Android speech recognition", false),
    OPENAI_REALTIME("OpenAI Realtime", false),
    DASHSCOPE("DashScope", false),
    VOLCENGINE("Volcengine", false),
    MIMO("MiMo", true),
    STEP("Step", true),
    DEEPSEEK("DeepSeek", true),
}

data class AsrProviderSettings(
    val provider: AsrProvider = AsrProvider.SYSTEM,
    val apiKeys: Map<AsrProvider, String> = emptyMap(),
) {
    fun apiKey(provider: AsrProvider = this.provider): String? =
        apiKeys[provider]?.takeIf(String::isNotBlank)
}

/**
 * Stores ASR provider credentials encrypted with a non-exportable Android
 * Keystore key. This data is intentionally device-local and is excluded from
 * Android backup by the existing backup rules.
 */
class AsrProviderSettingsStorage(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun load(): AsrProviderSettings {
        val encrypted = preferences.getString(ENCRYPTED_SETTINGS_KEY, null)
            ?: return AsrProviderSettings()
        return try {
            decrypt(encrypted).decodeSettings()
        } catch (e: GeneralSecurityException) {
            throw IllegalStateException(UNREADABLE_SETTINGS_MESSAGE, e)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException(UNREADABLE_SETTINGS_MESSAGE, e)
        } catch (e: SerializationException) {
            throw IllegalStateException(UNREADABLE_SETTINGS_MESSAGE, e)
        }
    }

    @Synchronized
    fun setProvider(provider: AsrProvider) {
        save(load().copy(provider = provider))
    }

    @Synchronized
    fun setApiKey(provider: AsrProvider, apiKey: String) {
        require(provider != AsrProvider.SYSTEM) { "Android speech recognition does not use an API key." }
        val current = load()
        val cleaned = apiKey.trim()
        val keys = if (cleaned.isEmpty()) {
            current.apiKeys - provider
        } else {
            current.apiKeys + (provider to cleaned)
        }
        save(current.copy(apiKeys = keys))
    }

    @Synchronized
    fun clear() {
        check(preferences.edit().remove(ENCRYPTED_SETTINGS_KEY).commit()) {
            "ASR settings could not be cleared."
        }
    }

    private fun save(settings: AsrProviderSettings) {
        val plaintext = settings.encodeSettings()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext)
        val payload = cipher.iv + ciphertext
        val encoded = Base64.encodeToString(payload, Base64.NO_WRAP)
        check(preferences.edit().putString(ENCRYPTED_SETTINGS_KEY, encoded).commit()) {
            "ASR settings could not be saved."
        }
    }

    private fun decrypt(encoded: String): ByteArray {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > GCM_IV_LENGTH_BYTES) { "Encrypted ASR settings are incomplete." }
        val iv = payload.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = payload.copyOfRange(GCM_IV_LENGTH_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun ByteArray.decodeSettings(): AsrProviderSettings {
        val root = Json.parseToJsonElement(toString(StandardCharsets.UTF_8)).jsonObject
        val providerName = root["provider"]?.jsonPrimitive?.content
            ?: throw SerializationException("ASR provider is missing.")
        val provider = AsrProvider.entries.firstOrNull {
            it.name.equals(providerName, ignoreCase = true)
        } ?: throw SerializationException("ASR provider is unsupported.")
        val keys = root["apiKeys"]?.jsonObject.orEmpty().mapNotNull { (name, value) ->
            val keyProvider = AsrProvider.entries.firstOrNull {
                it != AsrProvider.SYSTEM && it.name.equals(name, ignoreCase = true)
            } ?: return@mapNotNull null
            value.jsonPrimitive.content.takeIf(String::isNotBlank)?.let { keyProvider to it }
        }.toMap()
        return AsrProviderSettings(provider = provider, apiKeys = keys)
    }

    private fun AsrProviderSettings.encodeSettings(): ByteArray =
        buildJsonObject {
            put("provider", provider.name)
            putJsonObject("apiKeys") {
                apiKeys.forEach { (keyProvider, key) ->
                    if (keyProvider != AsrProvider.SYSTEM && key.isNotBlank()) {
                        put(keyProvider.name, key)
                    }
                }
            }
        }.toString().toByteArray(StandardCharsets.UTF_8)

    private companion object {
        const val PREFERENCES_NAME = "encrypted_provider_credentials"
        const val ENCRYPTED_SETTINGS_KEY = "asr_settings"
        const val KEY_ALIAS = "naira_asr_provider_settings"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
        const val UNREADABLE_SETTINGS_MESSAGE =
            "Saved ASR settings are unavailable. Clear them and enter the provider credentials again."
    }
}

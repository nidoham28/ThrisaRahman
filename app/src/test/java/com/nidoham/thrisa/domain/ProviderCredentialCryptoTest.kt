package com.nidoham.thrisa.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCredentialCryptoTest {
    @Test
    fun `credentials round trip and are bound to account id`() {
        val pin = "12345678".toCharArray()
        val credentials = mapOf(
            AssistantProvider.GEMINI to "gemini-secret",
            AssistantProvider.OPENROUTER to "openrouter-secret",
        )

        val (envelope, decrypted) =
            ProviderCredentialCrypto.create(USER_ID, pin, credentials)

        assertEquals(credentials, decrypted.credentials)
        assertFalse(String(envelope, Charsets.ISO_8859_1).contains("gemini-secret"))
        assertFalse(String(envelope, Charsets.ISO_8859_1).contains("openrouter-secret"))
        assertThrows(IllegalArgumentException::class.java) {
            ProviderCredentialCrypto.decrypt("another-user", pin, envelope)
        }
        pin.fill('\u0000')
        decrypted.encryptionKey.fill(0)
    }

    @Test
    fun `short or non numeric pins are rejected`() {
        assertFalse(ProviderCredentialCrypto.validatePin("1234567".toCharArray()))
        assertFalse(ProviderCredentialCrypto.validatePin("1234567a".toCharArray()))
        assertTrue(ProviderCredentialCrypto.validatePin("12345678".toCharArray()))
    }

    @Test
    fun `unlocked credentials are only available to their bound account and clear on lock`() {
        val credentials = mapOf(AssistantProvider.GEMINI to "gemini-secret")
        val session = DecryptedProviderCredentials(
            salt = ByteArray(16),
            encryptionKey = ByteArray(32),
            credentials = credentials,
        )
        try {
            ProviderCredentialSession.unlock(
                USER_ID,
                session,
                AssistantProvider.GEMINI,
            )

            assertEquals("gemini-secret", ProviderCredentialSession.getKey(
                USER_ID,
                AssistantProvider.GEMINI,
            ))
            assertEquals(null, ProviderCredentialSession.getKey(
                "another-user",
                AssistantProvider.GEMINI,
            ))

            ProviderCredentialSession.lock(USER_ID)
            assertEquals(null, ProviderCredentialSession.getKey(
                USER_ID,
                AssistantProvider.GEMINI,
            ))
        } finally {
            ProviderCredentialSession.lock()
        }
    }

    private companion object {
        const val USER_ID = "01a9d854-c28c-49dd-a779-9c065b401234"
    }
}

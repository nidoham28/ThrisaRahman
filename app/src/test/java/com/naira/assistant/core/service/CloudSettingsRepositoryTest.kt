package com.naira.assistant.core.service

import com.naira.assistant.core.sound.SpeechEngine
import com.naira.assistant.domain.AssistantLanguage
import com.naira.assistant.domain.AssistantProvider
import com.naira.assistant.domain.AssistantVoice
import com.naira.assistant.domain.Gender
import com.naira.assistant.domain.RelationType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSettingsRepositoryTest {
    @Test
    fun `cloud provider refresh preserves local Groq selection`() {
        assertFalse(
            shouldApplyRemoteProviderSelection(
                localProvider = AssistantProvider.GROQ,
                pendingProvider = null,
            )
        )
        assertTrue(
            shouldApplyRemoteProviderSelection(
                localProvider = AssistantProvider.OPENROUTER,
                pendingProvider = null,
            )
        )
        assertFalse(
            shouldApplyRemoteProviderSelection(
                localProvider = AssistantProvider.GROQ,
                pendingProvider = AssistantProvider.GEMINI,
            )
        )
    }

    @Test
    fun `sync loads all user settings profile and assistant fields`() = runBlocking {
        val local = FakeLocalStore()
        val expected = CloudSettings(
            language = AssistantLanguage.ENGLISH,
            speechEngine = SpeechEngine.ADVANCED,
            activeProvider = AssistantProvider.OPENROUTER,
            theme = "dark",
            fontSize = "large",
            storedHistory = false,
            storedMemories = false,
            voiceOutputEnabled = false,
            username = "rahim",
            displayName = "Rahim",
            phone = "+8801",
            gender = Gender.MALE,
            assistantName = "Mina",
            assistantLanguage = AssistantLanguage.BANGLA,
            relationship = RelationType.FRIEND,
            assistantVoice = AssistantVoice.MALE,
        )
        val remote = FakeRemoteStore(expected)
        val repository = CloudSettingsRepository(local, remote) { USER_ID }

        val result = repository.synchronize()

        assertEquals(CloudSettingsSyncResult.Synced(expected), result)
        assertEquals(expected, local.applied)
        assertEquals(null, remote.lastPatch)
    }

    @Test
    fun `changes spanning tables are saved and verified together`() = runBlocking {
        val local = FakeLocalStore()
        val remote = FakeRemoteStore(CloudSettings(AssistantLanguage.BANGLA, SpeechEngine.SYSTEM))
        val patch = CloudSettingsPatch(
            language = AssistantLanguage.ENGLISH,
            storedHistory = false,
            activeProvider = AssistantProvider.OPENROUTER,
            username = "new-name",
            assistantName = "Mina",
            relationship = RelationType.CLOSE_FRIEND,
            assistantVoice = AssistantVoice.MALE,
        )
        local.markPending(USER_ID, patch)
        val repository = CloudSettingsRepository(local, remote) { USER_ID }

        val result = repository.synchronize()

        assertTrue(result is CloudSettingsSyncResult.Synced)
        assertEquals(patch, remote.lastPatch)
        assertEquals(AssistantProvider.OPENROUTER, remote.settings?.activeProvider)
        assertEquals(AssistantLanguage.ENGLISH, remote.settings?.language)
        assertEquals(false, remote.settings?.storedHistory)
        assertEquals("new-name", remote.settings?.username)
        assertEquals("Mina", remote.settings?.assistantName)
        assertEquals(RelationType.CLOSE_FRIEND, remote.settings?.relationship)
        assertEquals(AssistantVoice.MALE, remote.settings?.assistantVoice)
        assertTrue(local.pendingPatch(USER_ID).isEmpty)
    }

    @Test
    fun `failed save retains pending edits for retry`() = runBlocking {
        val local = FakeLocalStore()
        val patch = CloudSettingsPatch(
            language = AssistantLanguage.ENGLISH,
            assistantName = "Mina",
        )
        local.markPending(USER_ID, patch)
        val remote = FakeRemoteStore(
            settings = CloudSettings(AssistantLanguage.BANGLA, SpeechEngine.SYSTEM),
            failUpdates = true,
        )
        val repository = CloudSettingsRepository(local, remote) { USER_ID }

        val failed = repository.synchronize()
        assertTrue(failed is CloudSettingsSyncResult.Failed)
        assertEquals(patch, local.pendingPatch(USER_ID))

        remote.failUpdates = false
        assertTrue(repository.synchronize() is CloudSettingsSyncResult.Synced)
        assertTrue(local.pendingPatch(USER_ID).isEmpty)
        assertEquals("Mina", remote.settings?.assistantName)
    }

    @Test
    fun `server that ignores an update does not clear pending changes`() = runBlocking {
        val local = FakeLocalStore()
        val patch = CloudSettingsPatch(username = "new-name")
        local.markPending(USER_ID, patch)
        val remote = FakeRemoteStore(
            settings = CloudSettings(AssistantLanguage.BANGLA, SpeechEngine.SYSTEM),
            ignoreUpdates = true,
        )
        val repository = CloudSettingsRepository(local, remote) { USER_ID }

        val result = repository.synchronize()

        assertTrue(result is CloudSettingsSyncResult.Failed)
        assertEquals(patch, local.pendingPatch(USER_ID))
    }

    @Test
    fun `missing settings row is reported without inserting client data`() = runBlocking {
        val local = FakeLocalStore()
        val remote = FakeRemoteStore(settings = null)
        val repository = CloudSettingsRepository(local, remote) { USER_ID }

        assertTrue(repository.synchronize() is CloudSettingsSyncResult.Failed)
        assertEquals(null, remote.lastPatch)
    }

    @Test
    fun `signed out sync does not access remote store`() = runBlocking {
        val remote = FakeRemoteStore(CloudSettings(AssistantLanguage.ENGLISH, SpeechEngine.ADVANCED))
        val repository = CloudSettingsRepository(FakeLocalStore(), remote) { null }

        assertEquals(CloudSettingsSyncResult.SignInRequired, repository.synchronize())
        assertEquals(0, remote.fetchCount)
    }

    private class FakeLocalStore : CloudSettingsLocalStore {
        private val pending = mutableMapOf<String, CloudSettingsPatch>()
        var applied: CloudSettings? = null

        override fun pendingPatch(userId: String): CloudSettingsPatch =
            pending[userId] ?: CloudSettingsPatch()

        override fun markPending(userId: String, patch: CloudSettingsPatch) {
            pending[userId] = pendingPatch(userId).merge(patch)
        }

        override fun applyRemote(userId: String, settings: CloudSettings) {
            applied = settings
        }

        override fun clearPending(userId: String, patch: CloudSettingsPatch) {
            if (pendingPatch(userId) == patch) pending.remove(userId)
        }
    }

    private class FakeRemoteStore(
        var settings: CloudSettings?,
        var failUpdates: Boolean = false,
        var ignoreUpdates: Boolean = false,
    ) : CloudSettingsRemoteStore {
        var lastPatch: CloudSettingsPatch? = null
            private set
        var fetchCount = 0

        override suspend fun fetch(userId: String): CloudSettings? {
            fetchCount++
            return settings
        }

        override suspend fun update(userId: String, patch: CloudSettingsPatch) {
            if (failUpdates) error("offline")
            lastPatch = patch
            if (ignoreUpdates) return
            val current = checkNotNull(settings)
            settings = current.copy(
                language = patch.language ?: current.language,
                speechEngine = patch.speechEngine ?: current.speechEngine,
                activeProvider = patch.activeProvider ?: current.activeProvider,
                theme = patch.theme ?: current.theme,
                fontSize = patch.fontSize ?: current.fontSize,
                storedHistory = patch.storedHistory ?: current.storedHistory,
                storedMemories = patch.storedMemories ?: current.storedMemories,
                voiceOutputEnabled = patch.voiceOutputEnabled ?: current.voiceOutputEnabled,
                username = patch.username ?: current.username,
                displayName = patch.displayName ?: current.displayName,
                phone = patch.phone ?: current.phone,
                gender = patch.gender ?: current.gender,
                assistantName = patch.assistantName ?: current.assistantName,
                assistantLanguage = patch.assistantLanguage ?: current.assistantLanguage,
                relationship = patch.relationship ?: current.relationship,
                assistantVoice = patch.assistantVoice ?: current.assistantVoice,
            )
        }
    }

    private companion object {
        const val USER_ID = "user-1"
    }
}

package com.naira.assistant.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageManagerTest {
    @Test
    fun `language IDs match the database values and retain readable labels`() {
        assertEquals(LanguageManager("Eng", "English"), AssistantLanguage.ENGLISH.language)
        assertEquals(LanguageManager("Ban", "Bangla"), AssistantLanguage.BANGLA.language)
    }

    @Test
    fun `database language parsing uses the first three characters`() {
        assertEquals(AssistantLanguage.ENGLISH, AssistantLanguage.fromDatabaseId("English"))
        assertEquals(AssistantLanguage.ENGLISH, AssistantLanguage.fromDatabaseId("Eng"))
        assertEquals(AssistantLanguage.BANGLA, AssistantLanguage.fromDatabaseId("Bangla"))
        assertEquals(AssistantLanguage.BANGLA, AssistantLanguage.fromDatabaseId("Ban"))
    }

    @Test
    fun `database language IDs preserve case`() {
        assertEquals("Eng", LanguageManager.fromId("English").id)
        assertEquals("Ban", LanguageManager.fromId("Bangla").id)
    }

    @Test
    fun `database language parsing is case insensitive`() {
        assertEquals(LanguageManager.ENGLISH, LanguageManager.fromId("eng"))
        assertEquals(LanguageManager.ENGLISH, LanguageManager.fromId("ENG"))
        assertEquals(LanguageManager.BANGLA, LanguageManager.fromId("ban"))
        assertEquals(LanguageManager.BANGLA, LanguageManager.fromId("bangla"))
        assertEquals(AssistantLanguage.ENGLISH, AssistantLanguage.fromDatabaseId("english"))
    }

    @Test
    fun `unknown or blank database IDs fall back to the default language instead of throwing`() {
        // The settings sync parses every downloaded row through this; one
        // unsupported value must degrade that setting, not abort the sync.
        assertEquals(LanguageManager.BANGLA, LanguageManager.fromId("spanish"))
        assertEquals(LanguageManager.BANGLA, LanguageManager.fromId(""))
        assertEquals(LanguageManager.BANGLA, LanguageManager.fromId(null))
        assertEquals(AssistantLanguage.BANGLA, AssistantLanguage.fromDatabaseId("unsupported"))
    }
}

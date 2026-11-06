package com.nidoham.thrisa.domain

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

    @Test(expected = IllegalStateException::class)
    fun `lowercase database IDs are rejected`() {
        LanguageManager.fromId("eng")
    }
}

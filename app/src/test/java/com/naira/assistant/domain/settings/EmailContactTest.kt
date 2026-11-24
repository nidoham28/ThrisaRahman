package com.naira.assistant.domain.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmailContactTest {

    @Test
    fun `contact requires an owner name and a valid email address`() {
        assertEquals("Enter the contact name.", validateEmailContactInput(" ", "alex@example.com"))
        assertEquals("Enter an email address.", validateEmailContactInput("Alex Morgan", " "))
        assertEquals("Enter a valid email address.", validateEmailContactInput("Alex Morgan", "not-an-email"))
    }

    @Test
    fun `valid contact name and email are accepted`() {
        assertNull(validateEmailContactInput("Alex Morgan", "alex+work@example.com"))
    }
}

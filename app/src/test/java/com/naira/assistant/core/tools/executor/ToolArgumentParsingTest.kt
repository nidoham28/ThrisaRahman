package com.naira.assistant.core.tools.executor

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolArgumentParsingTest {

    @Test
    fun `tag strings are trimmed and de-duplicated`() {
        assertEquals(
            listOf("home", "work"),
            parseToolTags(Json.parseToJsonElement("\" home,work,home \"")),
        )
    }

    @Test
    fun `a malformed tag array is rejected instead of partially accepted`() {
        assertNull(parseToolTags(Json.parseToJsonElement("""["home", 7]""")))
    }
}

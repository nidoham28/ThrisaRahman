package com.nidoham.thrisa.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantModeTest {

    @Test
    fun `reduce sensitive content forces normal mode`() {
        assertEquals(
            AssistantMode.NORMAL,
            effectiveAssistantMode(
                AssistantMode.ADULT_ROLEPLAY,
                reduceSensitiveContent = true,
            ),
        )
    }

    @Test
    fun `turning off sensitive reduction permits requested adult mode`() {
        assertEquals(
            AssistantMode.ADULT_ROLEPLAY,
            effectiveAssistantMode(
                AssistantMode.ADULT_ROLEPLAY,
                reduceSensitiveContent = false,
            ),
        )
    }

    @Test
    fun `normal mode remains available when sensitive reduction is off`() {
        assertEquals(
            AssistantMode.NORMAL,
            effectiveAssistantMode(
                AssistantMode.NORMAL,
                reduceSensitiveContent = false,
            ),
        )
    }
}

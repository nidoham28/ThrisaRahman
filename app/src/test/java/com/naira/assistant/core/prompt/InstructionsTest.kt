package com.naira.assistant.core.prompt

import com.naira.assistant.core.prompt.character.util.Character
import com.naira.assistant.core.prompt.user.User
import org.junit.Assert.assertTrue
import org.junit.Test

class InstructionsTest {

    @Test
    fun `tool guidance prioritizes necessary calls bounded arguments and worthwhile delegation`() {
        val prompt = Instructions(
            character = Character(),
            user = User(),
            language = "English",
            relationship = "Friends",
            moodLabels = listOf("Happy", "Neutral"),
        ).build()

        assertTrue(prompt.contains("Tools are optional"))
        assertTrue(prompt.contains("use only these tools"))
        assertTrue(prompt.contains("pass only required"))
        assertTrue(prompt.contains("relevant optional fields"))
        assertTrue(prompt.contains("smallest"))
        assertTrue(prompt.contains("result limit"))
        assertTrue(prompt.contains("Batch independent calls into one model turn"))
        assertTrue(prompt.contains("extra model calls"))
        assertTrue(prompt.contains("Synthesize results in clear language"))
        assertTrue(prompt.contains("Use a compatible app or Termux fallback"))
    }

    @Test
    fun `memory guidance limits retrieval and saving to relevant durable context`() {
        val prompt = Instructions(
            character = Character(),
            user = User(),
            language = "English",
            relationship = "Friends",
            moodLabels = listOf("Happy", "Neutral"),
        ).build()

        assertTrue(prompt.contains("Search memory only when personal context is relevant"))
        assertTrue(prompt.contains("clearly states it"))
        assertTrue(prompt.contains("lasting preference or fact"))
    }
}

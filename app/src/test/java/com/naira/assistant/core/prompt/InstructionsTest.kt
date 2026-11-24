package com.naira.assistant.core.prompt

import com.naira.assistant.core.prompt.character.util.Character
import com.naira.assistant.core.prompt.user.User
import org.junit.Assert.assertTrue
import org.junit.Test

class InstructionsTest {

    @Test
    fun `tool guidance searches relevant sources before asking and keeps calls bounded`() {
        val prompt = Instructions(
            character = Character(),
            user = User(),
            language = "English",
            relationship = "Friends",
            moodLabels = listOf("Happy", "Neutral"),
        ).build().replace(Regex("\\s+"), " ")

        assertTrue(prompt.contains("Use only these tools"))
        assertTrue(prompt.contains("use the narrowest relevant tool"))
        assertTrue(prompt.contains("search the relevant source before asking the user"))
        assertTrue(prompt.contains("never ask the user to repeat information already present"))
        assertTrue(prompt.contains("provide required arguments plus only relevant"))
        assertTrue(prompt.contains("relevant optional fields"))
        assertTrue(prompt.contains("smallest"))
        assertTrue(prompt.contains("result limit"))
        assertTrue(prompt.contains("Batch independent reads into one model turn"))
        assertTrue(prompt.contains("extra model calls"))
        assertTrue(prompt.contains("explain the result and any remaining limitation clearly"))
        assertTrue(prompt.contains("Use a compatible app or Termux fallback"))
        assertTrue(prompt.contains("Roleplay the selected character in a natural, realistic conversational style"))
        assertTrue(prompt.contains("Do not narrate hidden reasoning or pretend to perform steps"))
    }

    @Test
    fun `memory guidance limits retrieval and saving to relevant durable context`() {
        val prompt = Instructions(
            character = Character(),
            user = User(),
            language = "English",
            relationship = "Friends",
            moodLabels = listOf("Happy", "Neutral"),
        ).build().replace(Regex("\\s+"), " ")

        assertTrue(prompt.contains("For personal or context-specific facts, use the supplied profile"))
        assertTrue(prompt.contains("available tools (such as memory search) before asking"))
        assertTrue(prompt.contains("clearly states it"))
        assertTrue(prompt.contains("lasting preference or fact"))
    }

    @Test
    fun `routine greetings are answered directly and personalization checks available context`() {
        val prompt = Instructions(
            character = Character(),
            user = User(),
            language = "English",
            relationship = "Friends",
            moodLabels = listOf("Happy", "Neutral"),
        ).build().replace(Regex("\\s+"), " ")

        assertTrue(prompt.contains("greetings, welcomes, wishes, or short notes"))
        assertTrue(prompt.contains("Write a concise, friendly default immediately"))
        assertTrue(prompt.contains("whether or not the user explicitly"))
        assertTrue(prompt.contains("Character knowledge limits do not block relevant authorized lookups"))
        assertTrue(prompt.contains("Ask only about a critical detail still missing"))
        assertTrue(prompt.contains("Ask to expand the character's"))
        assertTrue(prompt.contains("knowledge list only if the user requests it"))
    }
}

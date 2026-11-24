package com.naira.assistant.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationHistoryTest {
    @Test
    fun `retains only the most recent complete turns`() {
        val history = (1..3).flatMap { turn ->
            listOf(
                UIMessage.user("question $turn"),
                UIMessage.assistant("answer $turn"),
            )
        }

        val compacted = compactConversationHistory(history, maxTurns = 2)

        assertEquals(
            listOf("question 2", "answer 2", "question 3", "answer 3"),
            compacted.map { it.parts.filterIsInstance<UIMessagePart.Text>().single().text },
        )
    }

    @Test
    fun `removes replayed image and tool payloads while retaining conversational text`() {
        val history = listOf(
            UIMessage(
                role = MessageRole.USER,
                parts = listOf(
                    UIMessagePart.Text("What is in this picture?"),
                    UIMessagePart.Image("data:image/jpeg;base64,private-image-data"),
                ),
            ),
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.Text("It shows a bicycle."),
                    UIMessagePart.Tool(
                        toolCallId = "call-1",
                        toolName = "capture_screen",
                        input = "{}",
                        output = listOf(
                            UIMessagePart.Text("""{"large":"tool result"}"""),
                            UIMessagePart.Image("data:image/png;base64,private-tool-image"),
                        ),
                    ),
                ),
            ),
        )

        val compacted = compactConversationHistory(history)
        val contents = compacted.flatMap { it.parts }.filterIsInstance<UIMessagePart.Text>()
            .joinToString("\n") { it.text }

        assertTrue(contents.contains("What is in this picture?"))
        assertTrue(contents.contains("Earlier image omitted"))
        assertTrue(contents.contains("It shows a bicycle."))
        assertFalse(contents.contains("private-image-data"))
        assertFalse(contents.contains("private-tool-image"))
        assertFalse(contents.contains("tool result"))
        assertTrue(compacted.none { message -> message.parts.any { it is UIMessagePart.Tool } })
    }

    @Test
    fun `caps an oversized latest turn without splitting the user and assistant turn`() {
        val history = listOf(
            UIMessage.user("question ".repeat(100)),
            UIMessage.assistant("answer ".repeat(100)),
        )

        val compacted = compactConversationHistory(history, maxCharacters = 180)

        assertEquals(listOf(MessageRole.USER, MessageRole.ASSISTANT), compacted.map { it.role })
        assertTrue(
            compacted.sumOf { message ->
                message.parts.filterIsInstance<UIMessagePart.Text>().sumOf { it.text.length }
            } <= 180,
        )
        assertTrue(compacted.any { message ->
            message.parts.filterIsInstance<UIMessagePart.Text>().any {
                it.text.contains("Earlier content truncated")
            }
        })
    }
}

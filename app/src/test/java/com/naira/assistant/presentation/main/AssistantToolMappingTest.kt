package com.naira.assistant.presentation.main

import me.rerere.ai.ui.UIMessagePart as AgentPart
import me.rerere.ai.ui.ToolApprovalState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantToolMappingTest {

    @Test
    fun `tool without output is shown as running with empty result`() {
        val calls = listOf(
            AgentPart.Tool(
                toolCallId = "call-1",
                toolName = "set_timer",
                input = """{"seconds":60}""",
            ),
        ).toUiToolCalls()

        assertEquals(1, calls.size)
        val call = calls.single()
        assertEquals("call-1", call.id)
        assertEquals("set_timer", call.name)
        assertTrue(call.isRunning)
        assertFalse(call.isError)
        assertEquals("", call.result)
    }

    @Test
    fun `executed tool shows success and pretty printed result`() {
        val calls = listOf(
            AgentPart.Tool(
                toolCallId = "call-2",
                toolName = "get_current_time",
                input = "{}",
                output = listOf(AgentPart.Text("""{"status":"ok","time":"09:00"}""")),
            ),
        ).toUiToolCalls()

        val call = calls.single()
        assertFalse(call.isRunning)
        assertFalse(call.isError)
        // Pretty-printed JSON keeps the field content intact.
        assertTrue(call.result.contains("\"time\""))
        assertTrue(call.result.contains("09:00"))
    }

    @Test
    fun `tool output carrying an error field is flagged`() {
        val calls = listOf(
            AgentPart.Tool(
                toolCallId = "call-3",
                toolName = "open_app",
                input = """{"app":"Zoom"}""",
                output = listOf(AgentPart.Text("""{"error":"Tool 'open_app' failed: app not found"}""")),
            ),
        ).toUiToolCalls()

        val call = calls.single()
        assertFalse(call.isRunning)
        assertTrue(call.isError)
        assertTrue(call.result.contains("app not found"))
    }

    @Test
    fun `unparseable output falls back to raw text without error flag`() {
        val calls = listOf(
            AgentPart.Tool(
                toolCallId = "call-4",
                toolName = "share_text",
                input = "{}",
                output = listOf(AgentPart.Text("plain text result")),
            ),
        ).toUiToolCalls()

        val call = calls.single()
        assertFalse(call.isError)
        assertEquals("plain text result", call.result)
    }

    @Test
    fun `partial streaming input is preserved as raw text`() {
        val calls = listOf(
            AgentPart.Tool(
                toolCallId = "call-5",
                toolName = "create_reminder",
                input = """{"title":"Take med""", // incomplete JSON mid-stream
            ),
        ).toUiToolCalls()

        assertEquals("""{"title":"Take med""", calls.single().input)
    }

    @Test
    fun `multiple tool calls keep their order and ids`() {
        val calls = listOf(
            AgentPart.Tool(toolCallId = "a", toolName = "get_current_time", input = "{}"),
            AgentPart.Tool(
                toolCallId = "b",
                toolName = "set_timer",
                input = "{}",
                output = listOf(AgentPart.Text("""{"ok":true}""")),
            ),
        ).toUiToolCalls()

        assertEquals(listOf("a", "b"), calls.map { it.id })
        assertTrue(calls[0].isRunning)
        assertFalse(calls[1].isRunning)
    }

    @Test
    fun `pending tool approval is not presented as running`() {
        val call = listOf(
            AgentPart.Tool(
                toolCallId = "approval-1",
                toolName = "perform_screen_action",
                input = """{"action":"tap"}""",
                approvalState = ToolApprovalState.Pending,
            ),
        ).toUiToolCalls().single()

        assertTrue(call.approvalPending)
        assertFalse(call.isRunning)
    }

    @Test
    fun `delegation call surfaces a failed specialist result`() {
        val call = listOf(
            AgentPart.Tool(
                toolCallId = "delegate-1",
                toolName = "delegate_to_specialists",
                input = "{}",
                output = listOf(
                    AgentPart.Text(
                        """{"specialists":[{"role":"Analyst","error":true,"result":"Specialist timed out."}]}"""
                    )
                ),
            ),
        ).toUiToolCalls().single()

        assertTrue(call.isError)
        assertTrue(call.result.contains("Specialist timed out."))
    }
}

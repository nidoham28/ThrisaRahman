package com.naira.assistant.ai

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentOrchestratorTest {

    @Test
    fun `parses bounded specialist tasks and delegation mode`() {
        val request = AgentOrchestrator.parseRequest(
            buildJsonObject {
                put("mode", "parallel")
                putJsonArray("tasks") {
                    add(
                        buildJsonObject {
                            put("role", "planner")
                            put("task", "Create a short plan")
                        }
                    )
                    add(
                        buildJsonObject {
                            put("role", "reviewer")
                            put("task", "Check for missing risks")
                        }
                    )
                }
            }
        )

        assertEquals(DelegationMode.PARALLEL, request.mode)
        assertEquals(
            listOf(SpecialistRole.PLANNER, SpecialistRole.REVIEWER),
            request.tasks.map { it.role },
        )
        assertEquals(2, request.tasks.size)
    }

    @Test
    fun `rejects too many specialist tasks`() {
        val tasks = List(AgentOrchestrator.MAX_SPECIALISTS + 1) {
            buildJsonObject {
                put("role", "analyst")
                put("task", "Analyze this")
            }
        }
        val input = buildJsonObject {
            put("mode", "parallel")
            putJsonArray("tasks") { tasks.forEach { add(it) } }
        }

        val error = runCatching { AgentOrchestrator.parseRequest(input) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("At most"))
    }

    @Test
    fun `rejects invalid roles and blank tasks`() {
        val invalidRole = buildJsonObject {
            put("mode", "sequential")
            putJsonArray("tasks") {
                add(buildJsonObject {
                    put("role", "executor")
                    put("task", "Run a command")
                })
            }
        }
        val blankTask = buildJsonObject {
            put("mode", "sequential")
            putJsonArray("tasks") {
                add(buildJsonObject {
                    put("role", "analyst")
                    put("task", "   ")
                })
            }
        }

        assertTrue(runCatching { AgentOrchestrator.parseRequest(invalidRole) }.isFailure)
        assertTrue(runCatching { AgentOrchestrator.parseRequest(blankTask) }.isFailure)
    }

    @Test
    fun `caps total specialist work across one coordinator run`() {
        assertTrue(AgentOrchestrator.canReserveSpecialists(alreadyUsed = 1, requested = 2))
        assertFalse(AgentOrchestrator.canReserveSpecialists(alreadyUsed = 2, requested = 2))
        assertFalse(AgentOrchestrator.canReserveSpecialists(alreadyUsed = -1, requested = 1))
    }

    @Test
    fun `specialist context excludes system prompts and non-text parts`() {
        val context = specialistContext(
            listOf(
                UIMessage.system("private system instructions"),
                UIMessage(
                    role = MessageRole.USER,
                    parts = listOf(
                        UIMessagePart.Text("Find the key points"),
                        UIMessagePart.Image("data:image/png;base64,private-image-data"),
                    ),
                ),
            )
        )

        assertTrue(context.contains("Find the key points"))
        assertFalse(context.contains("private system instructions"))
        assertFalse(context.contains("private-image-data"))
    }
}

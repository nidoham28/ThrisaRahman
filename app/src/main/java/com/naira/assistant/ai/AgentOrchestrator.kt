package com.naira.assistant.ai

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

const val DELEGATE_TOOL_NAME = "delegate_to_specialists"

enum class SpecialistRole(val displayName: String, val instruction: String) {
    PLANNER(
        "Planner",
        "Break the task into a small, ordered plan. Identify dependencies and important constraints."
    ),
    ANALYST(
        "Analyst",
        "Analyze the supplied context carefully. Separate supported facts from uncertainty."
    ),
    REVIEWER(
        "Reviewer",
        "Critically review the proposed approach. Find missing edge cases and safer alternatives."
    );

    companion object {
        fun fromWireName(value: String): SpecialistRole? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

enum class DelegationMode {
    PARALLEL,
    SEQUENTIAL;

    companion object {
        fun fromWireName(value: String): DelegationMode? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

data class SpecialistTask(
    val role: SpecialistRole,
    val task: String,
)

data class DelegationRequest(
    val mode: DelegationMode,
    val tasks: List<SpecialistTask>,
)

data class SpecialistOutcome(
    val role: SpecialistRole,
    val task: String,
    val result: String,
    val error: Boolean = false,
)

data class DelegationProgress(
    val role: SpecialistRole,
    val completed: Boolean,
    val error: Boolean = false,
)

object AgentOrchestrator {
    const val MAX_SPECIALISTS = 3
    const val MAX_SPECIALISTS_PER_RUN = 3
    const val MAX_TASK_LENGTH = 1_000
    const val MAX_CONTEXT_LENGTH = 6_000
    const val MAX_RESULT_LENGTH = 3_000
    const val SPECIALIST_TIMEOUT_MILLIS = 45_000L
    const val SPECIALIST_MAX_STEPS = 2
    const val SPECIALIST_MAX_TOKENS = 800

    fun canReserveSpecialists(alreadyUsed: Int, requested: Int): Boolean =
        alreadyUsed >= 0 &&
            requested in 1..MAX_SPECIALISTS &&
            alreadyUsed + requested <= MAX_SPECIALISTS_PER_RUN

    fun toolDefinition(): ToolDefinition = ToolDefinition(
        name = DELEGATE_TOOL_NAME,
        description = "Delegate up to three analysis tasks to specialists. Use parallel mode " +
            "only for independent tasks and sequential mode when later tasks depend on earlier findings. " +
            "Specialists only receive the task and recent text context; they cannot use tools or delegate.",
        parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("mode") {
                    put("type", "string")
                    putJsonArray("enum") {
                        add(JsonPrimitive("parallel"))
                        add(JsonPrimitive("sequential"))
                    }
                }
                putJsonObject("tasks") {
                    put("type", "array")
                    put("minItems", 1)
                    put("maxItems", MAX_SPECIALISTS)
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("role") {
                                put("type", "string")
                                putJsonArray("enum") {
                                    SpecialistRole.entries.forEach {
                                        add(JsonPrimitive(it.name.lowercase()))
                                    }
                                }
                            }
                            putJsonObject("task") {
                                put("type", "string")
                                put("maxLength", MAX_TASK_LENGTH)
                            }
                        }
                        putJsonArray("required") {
                            add(JsonPrimitive("role"))
                            add(JsonPrimitive("task"))
                        }
                    }
                }
            }
            putJsonArray("required") {
                add(JsonPrimitive("mode"))
                add(JsonPrimitive("tasks"))
            }
        },
    )

    fun parseRequest(args: JsonObject): DelegationRequest {
        val modeName = args["mode"]?.jsonPrimitive?.contentOrNull
            ?: error("Delegation mode is required.")
        val mode = DelegationMode.fromWireName(modeName)
            ?: error("Delegation mode must be 'parallel' or 'sequential'.")
        val taskValues = args["tasks"]?.jsonArray
            ?: error("Delegation tasks must be an array.")
        require(taskValues.isNotEmpty()) { "At least one specialist task is required." }
        require(taskValues.size <= MAX_SPECIALISTS) {
            "At most $MAX_SPECIALISTS specialist tasks are allowed per delegation."
        }

        val tasks = taskValues.mapIndexed { index, value ->
            val taskObject = value as? JsonObject
                ?: error("Specialist task ${index + 1} must be an object.")
            val roleName = taskObject["role"]?.jsonPrimitive?.contentOrNull
                ?: error("Specialist task ${index + 1} is missing its role.")
            val role = SpecialistRole.fromWireName(roleName)
                ?: error("Specialist task ${index + 1} has an unsupported role.")
            val task = taskObject["task"]?.jsonPrimitive?.contentOrNull?.trim()
                .orEmpty()
            require(task.isNotEmpty()) { "Specialist task ${index + 1} must not be blank." }
            require(task.length <= MAX_TASK_LENGTH) {
                "Specialist task ${index + 1} exceeds $MAX_TASK_LENGTH characters."
            }
            SpecialistTask(role, task)
        }
        return DelegationRequest(mode, tasks)
    }

    fun outcomesJson(outcomes: List<SpecialistOutcome>): JsonObject = buildJsonObject {
        putJsonArray("specialists") {
            outcomes.forEach { outcome ->
                add(
                    buildJsonObject {
                        put("role", outcome.role.displayName)
                        put("task", outcome.task)
                        put("result", outcome.result)
                        put("error", outcome.error)
                    }
                )
            }
        }
    }
}

fun specialistContext(messages: List<UIMessage>): String {
    val recentText = messages.asReversed()
        .asSequence()
        .filter { it.role != MessageRole.SYSTEM }
        .mapNotNull { message ->
            val text = message.parts
                .filterIsInstance<UIMessagePart.Text>()
                .joinToString("\n") { it.text }
                .trim()
            text.takeIf(String::isNotEmpty)?.let { "[${message.role.name.lowercase()}] $it" }
        }
        .take(8)
        .toList()
        .asReversed()
        .joinToString("\n")
    return recentText.takeLast(AgentOrchestrator.MAX_CONTEXT_LENGTH)
}


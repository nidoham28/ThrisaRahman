package com.naira.assistant.core.tools

import android.content.Context
import com.naira.assistant.ai.AssistantToolResult
import com.naira.assistant.ai.DELEGATE_TOOL_NAME
import com.naira.assistant.core.tools.executor.ScreenToolExecutor
import com.naira.assistant.domain.FunctionCall
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Applies app-level tool policy and routes a model tool request to its handler. */
class ToolCallDispatcher(
    context: Context,
    private val sensitiveActionsEnabled: Boolean,
    private val executeRegisteredTool: suspend (FunctionCall) -> JsonObject,
    private val executeSpecialists: suspend (FunctionCall) -> AssistantToolResult,
) {
    private val appContext = context.applicationContext

    suspend fun execute(call: FunctionCall): AssistantToolResult {
        if (call.name == DELEGATE_TOOL_NAME) return executeSpecialists(call)

        if (ToolRegistry.find(call.name) == null) {
            return AssistantToolResult(error("Unknown tool '${call.name}'."))
        }

        if (!sensitiveActionsEnabled && call.name in ToolRegistry.settingsPermissionRequiredNames) {
            return AssistantToolResult(
                error("This action is blocked. Enable Sensitive assistant actions in Settings → Permissions to allow it."),
            )
        }

        return when (call.name) {
            "capture_screen" -> AssistantToolResult(
                response = ScreenToolExecutor.executeCaptureScreen(call, appContext),
                image = ScreenToolExecutor.consumeLastCapture(),
            )
            "perform_screen_action" -> AssistantToolResult(
                response = ScreenToolExecutor.executePerformScreenAction(call, appContext),
                image = ScreenToolExecutor.consumeLastCapture(),
            )
            else -> AssistantToolResult(executeRegisteredTool(call))
        }
    }

    private fun error(message: String) = buildJsonObject {
        put("error", message)
    }
}

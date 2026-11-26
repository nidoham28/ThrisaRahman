
package com.naira.assistant.core.tools.executor

import android.content.Context
import com.naira.assistant.core.tools.CommandExecution
import com.naira.assistant.domain.FunctionCall
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.milliseconds

private const val TERMINAL_TIMEOUT_MILLIS = 10_000L
private const val TERMINAL_COLUMNS = 120
private const val TERMINAL_ROWS = 24
private const val EXIT_MARKER = "__NAIRA_COMMAND_EXIT__"

private val TOOL_NAME_PATTERN =
    Regex("[A-Za-z0-9_][A-Za-z0-9._+-]{0,127}")

// ---------------------------------------------------------------------
// Find terminal executable
// ---------------------------------------------------------------------

internal suspend fun executeFindTerminalTool(
    call: FunctionCall,
    context: Context,
): JsonObject {
    val toolName = CommandExecution.jsonStringArg(call, "tool_name")
        ?.trim()
        ?: return terminalError("'tool_name' must be a non-empty executable name.")

    if (!TOOL_NAME_PATTERN.matches(toolName)) {
        return terminalError(
            "tool_name must be a single executable name containing only letters, " +
                "digits, dot, underscore, plus, or hyphen. Shell commands and arguments are not allowed.",
        )
    }

    val result = try {
        runTerminalScript(context) {
            "command -v $toolName"
        }
    } catch (exception: TerminalScriptException) {
        return terminalError(exception.message ?: "The terminal command failed.")
    }

    val path = result.output
        .substringBeforeLast(EXIT_MARKER)
        .lineSequence()
        .map(String::trim)
        .lastOrNull { it.isNotBlank() }
        .orEmpty()

    val found = result.exitCode == 0 &&
            path.isNotBlank() &&
            path.startsWith("/")

    return buildJsonObject {
        put("success", true)
        put("tool_name", toolName)
        put("found", found)

        if (found) {
            put("path", path)
        } else {
            put(
                "message",
                "The command is not available in the app's Android shell."
            )
        }
    }
}

// ---------------------------------------------------------------------
// Terminal arithmetic calculation
// ---------------------------------------------------------------------

internal suspend fun executeTerminalCalculation(
    call: FunctionCall,
    context: Context,
): JsonObject {
    val expression = CommandExecution.jsonStringArg(call, "expression")
        ?: return terminalError("'expression' must be a non-empty arithmetic expression using integers.")

    val variables = when (val element = call.args["variables"]) {
        null -> emptyMap<String, Long>()
        is JsonObject -> buildMap<String, Long> {
            for ((name, value) in element) {
                val primitive = value as? JsonPrimitive
                val number = primitive?.takeUnless { it.isString }?.content?.toLongOrNull()
                    ?: return terminalError("Variable '$name' must be a 64-bit integer.")
                put(name, number)
            }
        }
        else -> return terminalError("'variables' must be an object of integer values.")
    }

    val calculation = try {
        parseTerminalArithmetic(expression, variables)
    } catch (exception: IllegalArgumentException) {
        return terminalError(exception.message ?: "The expression is not valid terminal arithmetic.")
    }

    val result = try {
        runTerminalScript(context) {
            "printf '%s\\n' \"\\$(( ${calculation.shellExpression} ))\""
        }
    } catch (exception: TerminalScriptException) {
        return terminalError(exception.message ?: "The terminal command failed.")
    }

    if (result.exitCode != 0) {
        return terminalError("The Android shell could not evaluate the arithmetic expression.")
    }

    val actualValue = result.output
        .substringBeforeLast(EXIT_MARKER)
        .lineSequence()
        .map(String::trim)
        .lastOrNull { it.isNotEmpty() }
        ?.toLongOrNull()
        ?: return terminalError("The Android shell returned an invalid calculation result.")

    if (actualValue != calculation.value) {
        return terminalError("The Android shell returned an inconsistent calculation result.")
    }

    return buildJsonObject {
        put("success", true)
        put("expression", expression)
        put("result", actualValue)
    }
}

// ---------------------------------------------------------------------
// Run a controlled terminal script
// ---------------------------------------------------------------------

private suspend fun runTerminalScript(
    context: Context,
    command: () -> String,
): TerminalResult {
    val completed = CompletableDeferred<String>()

    val client = object : TerminalSessionClient {

        override fun onTextChanged(changedSession: TerminalSession) = Unit

        override fun onTitleChanged(changedSession: TerminalSession) = Unit

        override fun onSessionFinished(finishedSession: TerminalSession) {
            val output = try {
                finishedSession.emulator
                    ?.screen
                    ?.transcriptText
                    .orEmpty()
            } catch (_: Exception) {
                ""
            }

            completed.complete(output)
        }

        override fun onCopyTextToClipboard(
            session: TerminalSession,
            text: String,
        ) = Unit

        override fun onPasteTextFromClipboard(
            session: TerminalSession,
        ) = Unit

        override fun onBell(session: TerminalSession) = Unit

        override fun onColorsChanged(session: TerminalSession) = Unit

        override fun onTerminalCursorStateChange(state: Boolean) = Unit

        override fun getTerminalCursorStyle(): Int = 0

        override fun logError(tag: String, message: String) = Unit

        override fun logWarn(tag: String, message: String) = Unit

        override fun logInfo(tag: String, message: String) = Unit

        override fun logDebug(tag: String, message: String) = Unit

        override fun logVerbose(tag: String, message: String) = Unit

        override fun logStackTraceWithMessage(
            tag: String,
            message: String,
            e: Exception,
        ) = Unit

        override fun logStackTrace(
            tag: String,
            e: Exception,
        ) = Unit
    }

    val home = context.applicationContext.filesDir.absolutePath

    val script =
        "${command()}; result=\$?; printf '\\n${EXIT_MARKER}%d\\n' \"\$result\""

    val session = withContext(Dispatchers.Main.immediate) {
        TerminalSession(
            "/system/bin/sh",
            home,
            arrayOf("sh", "-c", script),
            arrayOf(
                "TERM=xterm-256color",
                "HOME=$home",
                "PATH=/system/bin:/system/xbin:/vendor/bin:/product/bin",
            ),
            TERMINAL_ROWS,
            client,
        ).also {
            it.updateSize(TERMINAL_COLUMNS, TERMINAL_ROWS)
        }
    }

    val output = try {
        withTimeout(TERMINAL_TIMEOUT_MILLIS.milliseconds) {
            completed.await()
        }
    } finally {
        withContext(NonCancellable + Dispatchers.Main.immediate) {
            if (session.isRunning) {
                session.finishIfRunning()
            }
        }
    }

    val markerIndex = output.lastIndexOf(EXIT_MARKER)

    if (markerIndex < 0) {
        throw TerminalScriptException("Terminal command did not return an exit status.")
    }

    val status = output
        .substring(markerIndex + EXIT_MARKER.length)
        .lineSequence()
        .firstOrNull()
        ?.trim()
        ?.toIntOrNull()
        ?: throw TerminalScriptException("Terminal command returned an invalid exit status.")

    return TerminalResult(
        output = output,
        exitCode = status,
    )
}

// ---------------------------------------------------------------------
// Result model
// ---------------------------------------------------------------------

private data class TerminalResult(
    val output: String,
    val exitCode: Int,
)

/** A controlled terminal run failed in a way the user should see verbatim. */
private class TerminalScriptException(message: String) : Exception(message)

/** Structured tool failure, matching the executor-wide result contract. */
private fun terminalError(message: String): JsonObject = buildJsonObject {
    put("success", false)
    put("error", message)
}

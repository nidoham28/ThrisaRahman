package com.nidoham.thrisa.core.tools.executor

import android.content.Context
import com.nidoham.thrisa.domain.FunctionCall
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TERMINAL_TIMEOUT_MILLIS = 10_000L
private const val TERMINAL_COLUMNS = 120
private const val TERMINAL_ROWS = 24
private const val EXIT_MARKER = "__THRISA_COMMAND_EXIT__"

internal suspend fun executeFindTerminalTool(
    call: FunctionCall,
    context: Context,
): JsonObject {
    val toolName = BuiltinToolExecutor.jsonStringArg(call, "tool_name")
        ?: error("tool_name is required")
    require(TOOL_NAME_PATTERN.matches(toolName)) {
        "tool_name must be a single executable name containing only letters, digits, dot, underscore, plus, or hyphen"
    }

    val result = runTerminalScript(context) {
        "command -v $toolName"
    }
    val path = result.output.substringBeforeLast(EXIT_MARKER).trim().lineSequence().lastOrNull().orEmpty()

    return buildJsonObject {
        put("tool_name", toolName)
        put("found", result.exitCode == 0 && path.isNotBlank())
        if (result.exitCode == 0 && path.isNotBlank()) put("path", path)
        else put("message", "The command is not available in the app's Android shell.")
    }
}

internal suspend fun executeTerminalCalculation(
    call: FunctionCall,
    context: Context,
): JsonObject {
    val expression = BuiltinToolExecutor.jsonStringArg(call, "expression")
        ?: error("expression is required")
    val calculation = parseTerminalArithmetic(expression)
    val result = runTerminalScript(context) {
        "printf '%s\\n' \"\$(( ${calculation.shellExpression} ))\""
    }
    if (result.exitCode != 0) error("The Android shell could not evaluate the arithmetic expression")
    val actualValue = result.output.substringBeforeLast(EXIT_MARKER)
        .lineSequence()
        .map(String::trim)
        .lastOrNull { it.isNotEmpty() }
        ?.toLongOrNull()
        ?: error("The Android shell returned an invalid calculation result")
    check(actualValue == calculation.value) { "The Android shell returned an inconsistent calculation result" }
    return buildJsonObject {
        put("expression", expression)
        put("result", actualValue)
    }
}

private suspend fun runTerminalScript(
    context: Context,
    command: () -> String,
): TerminalResult {
    val completed = CompletableDeferred<String>()
    val client = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) = Unit
        override fun onTitleChanged(changedSession: TerminalSession) = Unit
        override fun onSessionFinished(finishedSession: TerminalSession) {
            val output = finishedSession.getEmulator()
                ?.getScreen()
                ?.getTranscriptText()
                .orEmpty()
            completed.complete(output)
        }
        override fun onCopyTextToClipboard(session: TerminalSession, text: String) = Unit
        override fun onPasteTextFromClipboard(session: TerminalSession) = Unit
        override fun onBell(session: TerminalSession) = Unit
        override fun onColorsChanged(session: TerminalSession) = Unit
        override fun onTerminalCursorStateChange(state: Boolean) = Unit
        override fun getTerminalCursorStyle(): Int = 0
        override fun logError(tag: String, message: String) = Unit
        override fun logWarn(tag: String, message: String) = Unit
        override fun logInfo(tag: String, message: String) = Unit
        override fun logDebug(tag: String, message: String) = Unit
        override fun logVerbose(tag: String, message: String) = Unit
        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) = Unit
        override fun logStackTrace(tag: String, e: Exception) = Unit
    }

    val home = context.applicationContext.filesDir.absolutePath
    val script = "${command()}; result=\$?; printf '\\n$EXIT_MARKER%d\\n' \"\$result\""
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
        ).also { it.updateSize(TERMINAL_COLUMNS, TERMINAL_ROWS) }
    }

    val output = try {
        withTimeout(TERMINAL_TIMEOUT_MILLIS) { completed.await() }
    } finally {
        if (session.isRunning()) {
            withContext(NonCancellable + Dispatchers.IO) { session.finishIfRunning() }
        }
    }

    val markerIndex = output.lastIndexOf(EXIT_MARKER)
    if (markerIndex < 0) error("Terminal command did not return an exit status")
    val status = output.substring(markerIndex + EXIT_MARKER.length)
        .lineSequence()
        .firstOrNull()
        ?.trim()
        ?.toIntOrNull()
        ?: error("Terminal command returned an invalid exit status")
    return TerminalResult(output, status)
}

private data class TerminalResult(val output: String, val exitCode: Int)

private val TOOL_NAME_PATTERN = Regex("[A-Za-z_][A-Za-z0-9._+-]{0,127}")

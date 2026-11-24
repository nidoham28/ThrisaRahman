package com.naira.assistant.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

internal const val MAX_CONVERSATION_HISTORY_TURNS = 8
internal const val MAX_CONVERSATION_HISTORY_CHARACTERS = 24_000

internal fun compactConversationHistory(
    messages: List<UIMessage>,
    maxTurns: Int = MAX_CONVERSATION_HISTORY_TURNS,
    maxCharacters: Int = MAX_CONVERSATION_HISTORY_CHARACTERS,
): List<UIMessage> {
    require(maxTurns > 0) { "Conversation history must retain at least one turn." }
    require(maxCharacters > 0) { "Conversation history must retain at least one character." }

    val textHistory = messages.mapNotNull { message ->
        val text = message.parts.filterIsInstance<UIMessagePart.Text>()
            .joinToString("\n") { it.text }
            .trim()
        when (message.role) {
            MessageRole.USER -> {
                val imageWasAttached = message.parts.any { it is UIMessagePart.Image }
                val content = listOfNotNull(
                    text.takeIf(String::isNotEmpty),
                    if (imageWasAttached) {
                        "[Earlier image omitted from history; refer to the assistant's prior description.]"
                    } else {
                        null
                    },
                ).joinToString("\n")
                message.copy(parts = listOf(UIMessagePart.Text(content)))
            }
            MessageRole.ASSISTANT -> text.takeIf(String::isNotEmpty)?.let {
                message.copy(parts = listOf(UIMessagePart.Text(it)))
            }
            MessageRole.SYSTEM, MessageRole.TOOL -> null
        }
    }

    val userIndices = textHistory.indices.filter { textHistory[it].role == MessageRole.USER }
    if (userIndices.isEmpty()) return emptyList()

    val firstRetainedTurn = userIndices[(userIndices.size - maxTurns).coerceAtLeast(0)]
    var retained = textHistory.drop(firstRetainedTurn)
    while (retained.count { it.role == MessageRole.USER } > 1 &&
        retained.sumOf { it.parts.filterIsInstance<UIMessagePart.Text>().sumOf { part -> part.text.length } } >
        maxCharacters
    ) {
        val nextTurn = retained.indices.firstOrNull {
            it > 0 && retained[it].role == MessageRole.USER
        } ?: -1
        if (nextTurn < 0) break
        retained = retained.drop(nextTurn)
    }

    val retainedCharacters = retained.sumOf { message ->
        message.parts.filterIsInstance<UIMessagePart.Text>().sumOf { it.text.length }
    }
    if (retainedCharacters <= maxCharacters) return retained

    val latestTurnStart = retained.indexOfLast { it.role == MessageRole.USER }
    val latestTurn = retained.drop(latestTurnStart)
    val baseBudget = maxCharacters / latestTurn.size
    val extraCharacters = maxCharacters % latestTurn.size
    return latestTurn.mapIndexed { index, message ->
        val budget = baseBudget + if (index < extraCharacters) 1 else 0
        val text = message.parts.filterIsInstance<UIMessagePart.Text>()
            .joinToString("\n") { it.text }
        message.copy(parts = listOf(UIMessagePart.Text(text.takeWithinHistoryBudget(budget))))
    }
}

private fun String.takeWithinHistoryBudget(maxCharacters: Int): String {
    if (length <= maxCharacters) return this
    if (maxCharacters <= 0) return ""

    val marker = "\n[Earlier content truncated to limit conversation context.]\n"
    if (maxCharacters <= marker.length) return take(maxCharacters)

    val available = maxCharacters - marker.length
    val leading = available / 2
    val trailing = available - leading
    return take(leading) + marker + takeLast(trailing)
}

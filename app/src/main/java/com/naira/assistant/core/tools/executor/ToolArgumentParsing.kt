package com.naira.assistant.core.tools.executor

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

internal fun parseToolTags(element: JsonElement): List<String>? {
    val rawTags = when (element) {
        is JsonArray -> element.map { item ->
            val primitive = item as? JsonPrimitive ?: return null
            if (!primitive.isString) return null
            primitive.content
        }

        is JsonPrimitive -> {
            if (!element.isString) return null
            if (element.content.isBlank()) emptyList() else element.content.split(",")
        }

        else -> return null
    }

    return rawTags
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
}

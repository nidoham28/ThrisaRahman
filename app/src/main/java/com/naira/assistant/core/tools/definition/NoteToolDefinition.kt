
package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private fun noteParameters(
    properties: JsonObject,
    required: List<String> = emptyList()
) = buildJsonObject {
    put("type", "object")
    put("additionalProperties", false)
    put("properties", properties)

    putJsonArray("required") {
        required.forEach { add(it) }
    }
}

private fun stringProperty(
    description: String,
    maxLength: Int? = null,
    enum: List<String>? = null,
    default: String? = null,
) = buildJsonObject {
    put("type", "string")
    put("description", description)
    maxLength?.let { put("maxLength", it) }
    enum?.let { values ->
        putJsonArray("enum") {
            values.forEach { add(it) }
        }
    }
    default?.let { put("default", it) }
}

private fun integerProperty(
    description: String,
    minimum: Int? = null,
    maximum: Int? = null,
    default: Int? = null,
) = buildJsonObject {
    put("type", "integer")
    put("description", description)
    minimum?.let { put("minimum", it) }
    maximum?.let { put("maximum", it) }
    default?.let { put("default", it) }
}

private fun booleanProperty(
    description: String,
    default: Boolean? = null,
) = buildJsonObject {
    put("type", "boolean")
    put("description", description)
    default?.let { put("default", it) }
}

// CREATE NOTE

fun createNoteTool() = ToolDefinition(
    name = "create_note",
    description = """
        Save information as a note when requested. Use a concise title and include supplied content.
    """.trimIndent(),
    parameters = noteParameters(
        properties = buildJsonObject {
            putJsonObject("title") {
                put("type", "string")
                put("description", "Short note title.")
                put("minLength", 1)
                put("maxLength", 200)
            }

            putJsonObject("content") {
                put("type", "string")
                put("description", "Optional note content.")
            }

            put(
                "tags",
                stringProperty(
                    description = "Optional comma-separated tags.",
                    maxLength = 500
                )
            )

            put(
                "pinned",
                booleanProperty(
                    "Pin note. Default: false.",
                    default = false,
                )
            )

            put(
                "color",
                stringProperty(
                    description = "Optional note color or theme, preferably a supported color or hex code.",
                    maxLength = 50
                )
            )
        },
        required = listOf("title")
    )
)

// UPDATE NOTE

fun updateNoteTool() = ToolDefinition(
    name = "update_note",
    description = """
        Update only requested fields of a note; preserve the rest. Find an unknown ID with
        list_notes or search_notes.
    """.trimIndent(),
    parameters = noteParameters(
        properties = buildJsonObject {
            put(
                "id",
                integerProperty(
                    description = "ID of the note to update.",
                    minimum = 1
                )
            )

            putJsonObject("title") {
                put("type", "string")
                put("description", "Replacement title.")
                put("minLength", 1)
                put("maxLength", 200)
            }

            putJsonObject("content") {
                put("type", "string")
                put("description", "Replacement note content.")
            }

            put(
                "tags",
                stringProperty(
                    description = "Replacement comma-separated tags.",
                    maxLength = 500
                )
            )

            put(
                "pinned",
                booleanProperty("Whether the note should be pinned.")
            )

            put(
                "color",
                stringProperty(
                    description = "Replacement note color or theme.",
                    maxLength = 50
                )
            )
        },
        required = listOf("id")
    )
)

// DELETE NOTE

fun deleteNoteTool() = ToolDefinition(
    name = "delete_note",
    description = """
        Permanently delete a note only on explicit request. Find its ID with list_notes or
        search_notes if unknown.
    """.trimIndent(),
    parameters = noteParameters(
        properties = buildJsonObject {
            put(
                "id",
                integerProperty(
                    description = "ID of the note to permanently delete.",
                    minimum = 1
                )
            )
        },
        required = listOf("id")
    )
)

// LIST NOTES

fun listNotesTool() = ToolDefinition(
    name = "list_notes",
    description = """
        List notes when requested; optionally filter by pin status and limit results. Default pin
        filter: all.
    """.trimIndent(),
    parameters = noteParameters(
        properties = buildJsonObject {
            put(
                "pinned",
                stringProperty(
                    description = "Filter by pin status: true, false, or all.",
                    enum = listOf("true", "false", "all"),
                    default = "all",
                )
            )

            put(
                "limit",
                integerProperty(
                    description = "Maximum results. Default: 20.",
                    minimum = 1,
                    maximum = 100,
                    default = 20,
                )
            )
        }
    )
)

// SEARCH NOTES

fun searchNotesTool() = ToolDefinition(
    name = "search_notes",
    description = """
        Find saved notes by terms in titles, content, or tags.
    """.trimIndent(),
    parameters = noteParameters(
        properties = buildJsonObject {
            putJsonObject("query") {
                put("type", "string")
                put("description", "Search terms.")
                put("minLength", 1)
                put("maxLength", 500)
            }

            put(
                "tags",
                stringProperty(
                    description = "Optional tag filter.",
                    maxLength = 100
                )
            )

            put(
                "limit",
                integerProperty(
                    description = "Maximum results. Default: 10.",
                    minimum = 1,
                    maximum = 50,
                    default = 10,
                )
            )
        },
        required = listOf("query")
    )
)

// ALL NOTE TOOLS

fun allNoteTools() = listOf(
    createNoteTool(),
    updateNoteTool(),
    deleteNoteTool(),
    listNotesTool(),
    searchNotesTool()
)


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
    enum: List<String>? = null
) = buildJsonObject {
    put("type", "string")
    put("description", description)
    maxLength?.let { put("maxLength", it) }
    enum?.let { values ->
        putJsonArray("enum") {
            values.forEach { add(it) }
        }
    }
}

private fun integerProperty(
    description: String,
    minimum: Int? = null,
    maximum: Int? = null
) = buildJsonObject {
    put("type", "integer")
    put("description", description)
    minimum?.let { put("minimum", it) }
    maximum?.let { put("maximum", it) }
}

private fun booleanProperty(description: String) = buildJsonObject {
    put("type", "boolean")
    put("description", description)
}

// CREATE NOTE

fun createNoteTool() = ToolDefinition(
    name = "create_note",
    description = """
        Create a note when the user asks to save, write down, remember, or keep information.
        Include a clear title and the supplied content. Return the created note's details.
    """.trimIndent(),
    parameters = noteParameters(
        properties = buildJsonObject {
            putJsonObject("title") {
                put("type", "string")
                put("description", "Short, descriptive title for the note.")
                put("minLength", 1)
                put("maxLength", 200)
            }

            putJsonObject("content") {
                put("type", "string")
                put("description", "The note's full text content.")
            }

            put(
                "tags",
                stringProperty(
                    description = "Optional comma-separated tags for categorization.",
                    maxLength = 500
                )
            )

            put(
                "pinned",
                booleanProperty(
                    "Whether to pin the note. Defaults to false."
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
        Update an existing note by ID. First use list_notes or search_notes
        if the target note's ID is unknown. Only provide fields the user
        wants to change; leave all other fields unchanged.
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
        Permanently delete a note by ID. Find the note with list_notes
        or search_notes first if its ID is unknown. Use only when the
        user explicitly requests deletion.
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
        Retrieve the user's notes. Use when they ask to view, browse,
        review, or recall saved notes. Results can be filtered by
        pinned status and limited in number.
    """.trimIndent(),
    parameters = noteParameters(
        properties = buildJsonObject {
            put(
                "pinned",
                stringProperty(
                    description = "Filter by pin status: true, false, or all.",
                    enum = listOf("true", "false", "all")
                )
            )

            put(
                "limit",
                integerProperty(
                    description = "Maximum number of notes to return. Defaults to 20.",
                    minimum = 1,
                    maximum = 100
                )
            )
        }
    )
)

// SEARCH NOTES

fun searchNotesTool() = ToolDefinition(
    name = "search_notes",
    description = """
        Search saved notes by keywords or phrases in their titles,
        content, or tags. Use when the user wants to find a specific
        note or information stored in their notes.
    """.trimIndent(),
    parameters = noteParameters(
        properties = buildJsonObject {
            putJsonObject("query") {
                put("type", "string")
                put("description", "Keyword or phrase to search for.")
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
                    description = "Maximum number of matching notes. Defaults to 10.",
                    minimum = 1,
                    maximum = 50
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

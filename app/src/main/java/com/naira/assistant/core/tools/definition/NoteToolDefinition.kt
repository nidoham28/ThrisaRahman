
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
    description = universalToolDescription(
        purpose = "Saves a note with a title, supplied content, and optional tags/formatting.",
        trigger = "The user asks to save information as a note.",
        notFor = "Use `insert_memory` for useful persistent personal facts, `create_todo` for tasks, and `create_reminder` for timed alerts.",
        prerequisite = "A meaningful `title` is required; do not invent content when none was supplied.",
    ),
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
                put("description", "Optional note content, up to 50000 characters.")
                put("maxLength", 50000)
            }

            put(
                "tags",
                stringProperty(
                    description = "Optional comma-separated tags, at most 20 tags of 50 characters each.",
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
                    description = "Optional note color name (for example blue, green, " +
                        "orange, pink, red, yellow) or hex code like #34A853.",
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
    description = universalToolDescription(
        purpose = "Updates only supplied fields of an existing note and preserves omitted fields.",
        trigger = "The user asks to edit a specific saved note.",
        notFor = "Use `create_note` to add a note, `delete_note` to remove one, and `update_memory` to change a saved memory.",
        prerequisite = "An exact note `id` is required; find an unknown ID with `list_notes` or `search_notes`.",
    ),
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
    description = universalToolDescription(
        purpose = "Permanently deletes one note.",
        trigger = "The user explicitly asks to delete a note.",
        notFor = "Use `update_note` to edit a note and `remove_memory` to delete a memory.",
        prerequisite = "An exact `id` is required; identify an unknown note with `list_notes`/`search_notes`.",
    ),
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
    description = universalToolDescription(
        purpose = "Lists saved notes with optional pin-status and result-limit filters.",
        trigger = "The user asks for a note overview/list or pinned notes.",
        notFor = "Use `search_notes` for keyword lookup and `list_memories` for saved personal memories.",
        prerequisite = "Pin-status and limit filters are optional; all pin states are included by default.",
    ),
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
    ),
    parallelizable = true,
)

// SEARCH NOTES

fun searchNotesTool() = ToolDefinition(
    name = "search_notes",
    description = universalToolDescription(
        purpose = "Searches saved note titles, content, and tags for matching terms.",
        trigger = "The user asks to find a note by topic or keyword.",
        notFor = "Use `list_notes` to browse without keywords and `search_memories` to search memories.",
        prerequisite = "A meaningful `query` is required; clarify the target if multiple notes match.",
    ),
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
    ),
    parallelizable = true,
)

// ALL NOTE TOOLS

fun allNoteTools() = listOf(
    createNoteTool(),
    updateNoteTool(),
    deleteNoteTool(),
    listNotesTool(),
    searchNotesTool()
)

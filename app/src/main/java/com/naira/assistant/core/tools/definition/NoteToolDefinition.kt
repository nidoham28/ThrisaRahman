package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun createNoteTool() = ToolDefinition(
    name = "create_note",
    description = """
        Create a new note.
        Use when the user says 'save a note', 'write this down', 'note that', 'jot this', 
        'keep this', or any similar phrasing. Always confirm the note title after creation.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("title") {
                put("type", "string")
                put("description", "Title or summary of the note.")
            }
            putJsonObject("content") {
                put("type", "string")
                put("description", "Detailed text content of the note.")
            }
            putJsonObject("tags") {
                put("type", "string")
                put("description", "Optional comma-separated tags, e.g. 'work, ideas, project'.")
            }
            putJsonObject("pinned") {
                put("type", "boolean")
                put("description", "Whether to pin this note to the top (default false).")
            }
            putJsonObject("color") {
                put("type", "string")
                put("description", "Optional color theme or hex code for the note.")
            }
        }
        putJsonArray("required") { add("title") }
    },
)

fun updateNoteTool() = ToolDefinition(
    name = "update_note",
    description = """
        Update an existing note by its numeric ID.
        Call `list_notes` or `search_notes` first if you don't know the ID.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("id") {
                put("type", "integer")
                put("description", "Numeric ID of the note to update.")
            }
            putJsonObject("title") {
                put("type", "string")
                put("description", "New title for the note.")
            }
            putJsonObject("content") {
                put("type", "string")
                put("description", "New content for the note.")
            }
            putJsonObject("tags") {
                put("type", "string")
                put("description", "New comma-separated tags.")
            }
            putJsonObject("pinned") {
                put("type", "boolean")
                put("description", "Set pinned state (true or false).")
            }
            putJsonObject("color") {
                put("type", "string")
                put("description", "New color theme for the note.")
            }
        }
        putJsonArray("required") { add("id") }
    },
)

fun deleteNoteTool() = ToolDefinition(
    name = "delete_note",
    description = """
        Permanently delete a note by its numeric ID.
        Call `list_notes` or `search_notes` first if you don't know the ID.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("id") {
                put("type", "integer")
                put("description", "Numeric ID of the note to permanently delete.")
            }
        }
        putJsonArray("required") { add("id") }
    },
)

fun listNotesTool() = ToolDefinition(
    name = "list_notes",
    description = """
        List the user's notes. Use when the user asks to see, browse, or recall their notes.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("pinned") {
                put("type", "string")
                put(
                    "description",
                    "Optional filter: 'true' for pinned notes only, 'false' for unpinned, or 'all'."
                )
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Maximum number of notes to return (1-100, default 20).")
            }
        }
        putJsonArray("required") {}
    },
)

fun searchNotesTool() = ToolDefinition(
    name = "search_notes",
    description = """
        Search notes by keyword in title, content, or tags.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put("description", "Keyword or phrase to search for.")
            }
            putJsonObject("tags") {
                put("type", "string")
                put("description", "Optional tag filter.")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Maximum number of results to return (1-50, default 10).")
            }
        }
        putJsonArray("required") {}
    },
)

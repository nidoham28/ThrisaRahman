
package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

// ---------------------------------------------------------------------
// Shared schema helpers
// ---------------------------------------------------------------------

private fun stringField(
    description: String,
    values: List<String>? = null,
): JsonObject = buildJsonObject {
    put("type", "string")
    put("description", description)

    values?.let {
        putJsonArray("enum") {
            it.forEach { value -> add(value) }
        }
    }
}

private fun integerField(description: String): JsonObject =
    buildJsonObject {
        put("type", "integer")
        put("description", description)
    }

private fun booleanField(description: String): JsonObject =
    buildJsonObject {
        put("type", "boolean")
        put("description", description)
    }

private fun todoTool(
    name: String,
    description: String,
    properties: JsonObject,
    required: List<String> = emptyList(),
) = ToolDefinition(
    name = name,
    description = description.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        put("properties", properties)

        putJsonArray("required") {
            required.forEach { add(it) }
        }
    },
)

// ---------------------------------------------------------------------
// Create
// ---------------------------------------------------------------------

fun createTodoTool() = todoTool(
    name = "create_todo",
    description = """
        Create a new to-do task.
        Use when the user asks to add a task or put something on their list.
        Resolve relative due dates using get_current_time first.
        Interpret dates in the user's local timezone.
        Confirm the created task after success.
    """,
    properties = buildJsonObject {
        put(
            "title",
            stringField("Task title or description."),
        )
        put(
            "notes",
            stringField("Optional additional details."),
        )
        put(
            "due_at_ms",
            integerField("Optional due date as Unix epoch milliseconds."),
        )
        put(
            "priority",
            stringField(
                "Task priority. Defaults to normal.",
                listOf("low", "normal", "high", "urgent"),
            ),
        )
        put(
            "tags",
            stringField("Optional comma-separated tags."),
        )
        put(
            "list_name",
            stringField("Destination list. Defaults to inbox."),
        )
    },
    required = listOf("title"),
)

// ---------------------------------------------------------------------
// Update
// ---------------------------------------------------------------------

fun updateTodoTool() = todoTool(
    name = "update_todo",
    description = """
        Update an existing to-do task by its numeric ID.
        Only supplied fields are changed; omitted fields remain unchanged.
        Use list_todos or search_todos first if the task ID is unknown.
        Use an empty notes string to clear existing notes, if supported.
    """,
    properties = buildJsonObject {
        put(
            "id",
            integerField("ID of the task to update."),
        )
        put(
            "title",
            stringField("New task title."),
        )
        put(
            "notes",
            stringField("New notes. Use an empty string to clear, if supported."),
        )
        put(
            "done",
            booleanField("Set true to complete the task or false to reopen it."),
        )
        put(
            "due_at_ms",
            integerField("New due date as Unix epoch milliseconds."),
        )
        put(
            "priority",
            stringField(
                "New task priority.",
                listOf("low", "normal", "high", "urgent"),
            ),
        )
        put(
            "tags",
            stringField("New comma-separated tags."),
        )
        put(
            "list_name",
            stringField("Destination list name."),
        )
    },
    required = listOf("id"),
)

// ---------------------------------------------------------------------
// Delete
// ---------------------------------------------------------------------

fun deleteTodoTool() = todoTool(
    name = "delete_todo",
    description = """
        Permanently delete a to-do task.
        Use list_todos or search_todos to identify the task if its ID is unknown.
        Delete only when the user explicitly requests deletion.
    """,
    properties = buildJsonObject {
        put(
            "id",
            integerField("ID of the task to delete."),
        )
    },
    required = listOf("id"),
)

// ---------------------------------------------------------------------
// List
// ---------------------------------------------------------------------

fun listTodosTool() = todoTool(
    name = "list_todos",
    description = """
        List to-do tasks, optionally filtered by completion status,
        priority, or list name. Defaults to pending tasks.
    """,
    properties = buildJsonObject {
        put(
            "done",
            stringField(
                "Completion filter. Defaults to false.",
                listOf("false", "true", "all"),
            ),
        )
        put(
            "priority",
            stringField(
                "Priority filter.",
                listOf("low", "normal", "high", "urgent", "all"),
            ),
        )
        put(
            "list_name",
            stringField("List name filter, or all lists."),
        )
        put(
            "limit",
            integerField("Maximum results to return (1-100, default 20)."),
        )
    },
)

// ---------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------

fun searchTodosTool() = todoTool(
    name = "search_todos",
    description = """
        Search tasks by keyword or phrase in their titles, notes, or tags.
        Use list_todos instead when the user wants to browse tasks without a search term.
    """,
    properties = buildJsonObject {
        put(
            "query",
            stringField("Keyword or phrase to search for."),
        )
        put(
            "list_name",
            stringField("Optional list name filter."),
        )
        put(
            "limit",
            integerField("Maximum results to return (1-50, default 10)."),
        )
    },
    required = listOf("query"),
)

// ---------------------------------------------------------------------
// Mark done
// ---------------------------------------------------------------------

fun markTodoDoneTool() = todoTool(
    name = "mark_todo_done",
    description = """
        Mark a to-do task as completed.
        Use when the user says they finished or completed a task.
        Identify the correct task first if its ID is unknown.
    """,
    properties = buildJsonObject {
        put(
            "id",
            integerField("ID of the task to complete."),
        )
    },
    required = listOf("id"),
)

// ---------------------------------------------------------------------
// Mark undone
// ---------------------------------------------------------------------

fun markTodoUndoneTool() = todoTool(
    name = "mark_todo_undone",
    description = """
        Reopen a completed to-do task by marking it as not done.
    """,
    properties = buildJsonObject {
        put(
            "id",
            integerField("ID of the task to reopen."),
        )
    },
    required = listOf("id"),
)

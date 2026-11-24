
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
    minLength: Int? = null,
    default: String? = null,
): JsonObject = buildJsonObject {
    put("type", "string")
    put("description", description)
    minLength?.let { put("minLength", it) }
    default?.let { put("default", it) }

    values?.let {
        putJsonArray("enum") {
            it.forEach { value -> add(value) }
        }
    }
}

private fun integerField(
    description: String,
    minimum: Int? = null,
    maximum: Int? = null,
    default: Int? = null,
): JsonObject = buildJsonObject {
    put("type", "integer")
    put("description", description)
    minimum?.let { put("minimum", it) }
    maximum?.let { put("maximum", it) }
    default?.let { put("default", it) }
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
        Create a task on request. Use the context timestamp/local timezone for relative due dates;
        call get_current_time only if stale or another timezone is needed.
    """,
    properties = buildJsonObject {
        put(
            "title",
            stringField("Task title.", minLength = 1),
        )
        put(
            "notes",
            stringField("Optional additional details."),
        )
        put(
            "due_at_ms",
            integerField("Optional due date as Unix epoch milliseconds.", minimum = 0),
        )
        put(
            "priority",
            stringField(
                "Task priority. Defaults to normal.",
                listOf("low", "normal", "high", "urgent"),
                default = "normal",
            ),
        )
        put(
            "tags",
            stringField("Optional comma-separated tags."),
        )
        put(
            "list_name",
            stringField("Destination list. Defaults to inbox.", default = "inbox"),
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
        Update only supplied fields of an existing task; preserve the rest. Find unknown IDs with
        list_todos or search_todos.
    """,
    properties = buildJsonObject {
        put(
            "id",
            integerField("Task ID.", minimum = 1),
        )
        put(
            "title",
            stringField("New task title.", minLength = 1),
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
            integerField("New due date as Unix epoch milliseconds.", minimum = 0),
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
        Permanently delete a task only on explicit request. Find its ID with list_todos or
        search_todos if unknown.
    """,
    properties = buildJsonObject {
        put(
            "id",
            integerField("Task ID.", minimum = 1),
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
        List tasks, optionally filtered by completion, priority, or list. Default: pending.
    """,
    properties = buildJsonObject {
        put(
            "done",
            stringField(
                "Completion filter. Defaults to false.",
                listOf("false", "true", "all"),
                default = "false",
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
            integerField("Maximum results.", minimum = 1, maximum = 100, default = 20),
        )
    },
)

// ---------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------

fun searchTodosTool() = todoTool(
    name = "search_todos",
    description = """
        Search task titles, notes, and tags. Use list_todos to browse without a query.
    """,
    properties = buildJsonObject {
        put(
            "query",
            stringField("Search terms.", minLength = 1),
        )
        put(
            "list_name",
            stringField("Optional list name filter."),
        )
        put(
            "limit",
            integerField("Maximum results.", minimum = 1, maximum = 50, default = 10),
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
        Mark a task complete when the user says it is done. Identify unknown IDs first.
    """,
    properties = buildJsonObject {
        put(
            "id",
            integerField("Task ID.", minimum = 1),
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
        Reopen a completed task by ID.
    """,
    properties = buildJsonObject {
        put(
            "id",
            integerField("Task ID.", minimum = 1),
        )
    },
    required = listOf("id"),
)

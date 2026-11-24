
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
    parallelizable: Boolean = false,
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
    parallelizable = parallelizable,
)

// ---------------------------------------------------------------------
// Create
// ---------------------------------------------------------------------

fun createTodoTool() = todoTool(
    name = "create_todo",
    description = universalToolDescription(
        purpose = "Creates a task item with optional due date, priority, tags, and list.",
        trigger = "The user asks to add a task/to-do.",
        notFor = "Use `create_calendar_event` for calendar events and `create_reminder` for date-specific alerts.",
        prerequisite = "A task `title` is required; for relative due dates use contextual time/local timezone, or `get_current_time` if stale or another timezone is needed.",
    ),
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
    description = universalToolDescription(
        purpose = "Updates only specified fields of an existing task and preserves the rest.",
        trigger = "The user asks to change a task's title, due date, notes, priority, tags, list, or completion state.",
        notFor = "Use `create_todo` to add a task or `mark_todo_done`/`mark_todo_undone` for completion-only changes.",
        prerequisite = "An exact `id` is required; find an unknown task with `list_todos` or `search_todos`.",
    ),
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
    description = universalToolDescription(
        purpose = "Permanently deletes one task.",
        trigger = "The user explicitly asks to delete a task.",
        notFor = "Use `mark_todo_done`/`mark_todo_undone` to complete/reopen a task; do not delete without an explicit request.",
        prerequisite = "An exact `id` is required; find an unknown task with `list_todos` or `search_todos`.",
    ),
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
    description = universalToolDescription(
        purpose = "Lists tasks with optional completion, priority, and list filters.",
        trigger = "The user asks for a task overview or a filtered task list.",
        notFor = "Use `search_todos` for task keywords, `list_calendar_events` for events, or `list_reminders` for reminders.",
        prerequisite = "Filters are optional; pending tasks are returned by default.",
    ),
    parallelizable = true,
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
    description = universalToolDescription(
        purpose = "Searches task titles, notes, and tags for matching terms.",
        trigger = "The user asks to find a specific task or matching tasks.",
        notFor = "Use `list_todos` to browse without keywords, `search_reminders` for reminders, or `search_notes` for notes.",
        prerequisite = "Provide a meaningful `query`; if no results appear, refine the terms rather than inventing a task.",
    ),
    parallelizable = true,
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
    description = universalToolDescription(
        purpose = "Marks a specific task as complete.",
        trigger = "The user says a task is done or asks to mark it complete.",
        notFor = "Use `update_todo` to edit other fields and `delete_todo` to remove a task.",
        prerequisite = "An exact task `id` is required; identify an unknown task with `list_todos`/`search_todos`.",
    ),
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
    description = universalToolDescription(
        purpose = "Reopens a completed task by marking it incomplete.",
        trigger = "The user asks to reopen or mark a completed task as not done.",
        notFor = "Use `update_todo` to edit task details and `create_todo` to add a new task.",
        prerequisite = "An exact task `id` is required; find an unknown task with `list_todos` or `search_todos`.",
    ),
    properties = buildJsonObject {
        put(
            "id",
            integerField("Task ID.", minimum = 1),
        )
    },
    required = listOf("id"),
)

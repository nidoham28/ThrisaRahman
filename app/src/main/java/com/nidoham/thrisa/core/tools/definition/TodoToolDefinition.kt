package com.nidoham.thrisa.core.tools.definition

import com.nidoham.thrisa.core.tools.ToolDefinition
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun createTodoTool() = ToolDefinition(
    name = "create_todo",
    description = """
        Create a new to-do task.
        Use when the user says 'add a task', 'todo', 'I need to', 'put on my list', 
        'add to my to-do', or any similar phrasing. Always confirm after creation.
        Call `get_current_time` first when resolving relative due dates.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("title") {
                put("type", "string")
                put("description", "Clear task description (e.g. 'Buy groceries').")
            }
            putJsonObject("notes") {
                put("type", "string")
                put("description", "Optional notes or details for the task.")
            }
            putJsonObject("due_at_ms") {
                put("type", "integer")
                put("description", "Optional Unix epoch milliseconds when the task is due.")
            }
            putJsonObject("priority") {
                put("type", "string")
                put(
                    "description",
                    "Priority level: 'low', 'normal' (default), 'high', or 'urgent'."
                )
                putJsonArray("enum") { add("low"); add("normal"); add("high"); add("urgent") }
            }
            putJsonObject("tags") {
                put("type", "string")
                put("description", "Optional comma-separated tags, e.g. 'errand, personal'.")
            }
            putJsonObject("list_name") {
                put("type", "string")
                put(
                    "description",
                    "List or category name (e.g. 'inbox', 'shopping', 'work'). Default is 'inbox'."
                )
            }
        }
        putJsonArray("required") { add("title") }
    },
)

fun updateTodoTool() = ToolDefinition(
    name = "update_todo",
    description = """
        Update an existing to-do task by its numeric ID.
        Call `list_todos` or `search_todos` first if you don't know the ID.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("id") {
                put("type", "integer")
                put("description", "Numeric ID of the to-do item to update.")
            }
            putJsonObject("title") {
                put("type", "string")
                put("description", "New task description.")
            }
            putJsonObject("notes") {
                put("type", "string")
                put("description", "New notes for the task.")
            }
            putJsonObject("done") {
                put("type", "boolean")
                put("description", "Mark completed (true) or open (false).")
            }
            putJsonObject("due_at_ms") {
                put("type", "integer")
                put("description", "New due timestamp in Unix epoch ms.")
            }
            putJsonObject("priority") {
                put("type", "string")
                put("description", "New priority: 'low', 'normal', 'high', or 'urgent'.")
                putJsonArray("enum") { add("low"); add("normal"); add("high"); add("urgent") }
            }
            putJsonObject("tags") {
                put("type", "string")
                put("description", "New comma-separated tags.")
            }
            putJsonObject("list_name") {
                put("type", "string")
                put("description", "New list name.")
            }
        }
        putJsonArray("required") { add("id") }
    },
)

fun deleteTodoTool() = ToolDefinition(
    name = "delete_todo",
    description = """
        Permanently delete a to-do task by its numeric ID.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("id") {
                put("type", "integer")
                put("description", "Numeric ID of the to-do item to permanently delete.")
            }
        }
        putJsonArray("required") { add("id") }
    },
)

fun listTodosTool() = ToolDefinition(
    name = "list_todos",
    description = """
        List the user's to-do tasks, optionally filtered by status, priority, or list name.
        Use when the user asks what tasks they have, what's on their to-do list, etc.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("done") {
                put("type", "string")
                put(
                    "description",
                    "Filter by completion: 'false' for pending tasks (default), 'true' for completed, or 'all'."
                )
            }
            putJsonObject("priority") {
                put("type", "string")
                put(
                    "description",
                    "Filter by priority: 'low', 'normal', 'high', 'urgent', or 'all'."
                )
            }
            putJsonObject("list_name") {
                put("type", "string")
                put(
                    "description",
                    "Filter by list name, e.g. 'work', 'shopping', 'inbox', or 'all'."
                )
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Maximum number of items to return (1-100, default 20).")
            }
        }
        putJsonArray("required") {}
    },
)

fun searchTodosTool() = ToolDefinition(
    name = "search_todos",
    description = """
        Search to-do tasks by keyword in title, notes, or tags.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put("description", "Keyword or phrase to search for.")
            }
            putJsonObject("list_name") {
                put("type", "string")
                put("description", "Optional list name filter.")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Maximum results to return (1-50, default 10).")
            }
        }
        putJsonArray("required") {}
    },
)

fun markTodoDoneTool() = ToolDefinition(
    name = "mark_todo_done",
    description = """
        Mark a to-do task as completed.
        Use when the user says 'done', 'finished', 'completed', 'checked off', or 'I did it'.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("id") {
                put("type", "integer")
                put("description", "Numeric ID of the to-do item to mark as done.")
            }
        }
        putJsonArray("required") { add("id") }
    },
)

fun markTodoUndoneTool() = ToolDefinition(
    name = "mark_todo_undone",
    description = """
        Mark a completed to-do task as not done (reopen it).
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("id") {
                put("type", "integer")
                put("description", "Numeric ID of the to-do item to reopen.")
            }
        }
        putJsonArray("required") { add("id") }
    },
)


package com.naira.assistant.core.tools.executor

import com.naira.assistant.core.database.todo.TodoItem
import com.naira.assistant.core.database.todo.TodoPriority
import com.naira.assistant.core.database.todo.TodoRepository
import com.naira.assistant.core.tools.CommandExecution.jsonStringArg
import com.naira.assistant.domain.FunctionCall
import kotlinx.serialization.json.*

/**
 * Executes AI to-do tools.
 */
object TodoToolExecutor {

    private val validPriorities = setOf(
        "low", "normal", "high", "urgent"
    )

    // ----------------------------------------------------------------
    // Create
    // ----------------------------------------------------------------

    suspend fun executeCreateTodo(
        call: FunctionCall,
        repo: TodoRepository,
    ): JsonObject {
        val title = jsonStringArg(call, "title")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return error("'title' is required and cannot be blank.")

        val dueAtMs = if (call.args.containsKey("due_at_ms")) {
            extractLongArg(call, "due_at_ms")
                ?: return error("'due_at_ms' must be an integer.")
        } else {
            0L
        }

        if (dueAtMs < 0L) {
            return error("'due_at_ms' cannot be negative.")
        }

        val priorityValue = if (call.args.containsKey("priority")) {
            jsonStringArg(call, "priority")
                ?: return error("'priority' must be a non-empty string.")
        } else {
            "normal"
        }

        if (priorityValue !in validPriorities) {
            return error(
                "Invalid priority. Allowed values: ${validPriorities.joinToString()}."
            )
        }

        val listName = if (call.args.containsKey("list_name")) {
            jsonStringArg(call, "list_name")
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: return error("'list_name' must be a non-empty string.")
        } else {
            "inbox"
        }
        val notes = if (call.args.containsKey("notes")) {
            rawStringArg(call, "notes") ?: return error("'notes' must be a string.")
        } else {
            ""
        }
        val tags = if (call.args.containsKey("tags")) {
            parseToolTags(call.args.getValue("tags"))
                ?: return error("'tags' must be a string or an array of strings.")
        } else {
            emptyList()
        }

        val inserted = repo.insert(
            title = title,
            notes = notes,
            dueAtMs = dueAtMs,
            priority = TodoPriority.fromId(priorityValue),
            tags = tags,
            listName = listName,
        )

        return success(
            action = "created",
            message = "Task #${inserted.id} '${inserted.title}' created.",
            todo = inserted,
        )
    }

    // ----------------------------------------------------------------
    // Update
    // ----------------------------------------------------------------

    suspend fun executeUpdateTodo(
        call: FunctionCall,
        repo: TodoRepository,
    ): JsonObject {
        val id = extractLongArg(call, "id")
            ?.takeIf { it > 0L }
            ?: return error("A valid positive task 'id' is required.")

        val title = if (call.args.containsKey("title")) {
            jsonStringArg(call, "title")
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: return error("'title' cannot be blank.")
        } else {
            null
        }
        val notes = if (call.args.containsKey("notes")) {
            rawStringArg(call, "notes") ?: return error("'notes' must be a string.")
        } else {
            null
        }

        val done = if (call.args.containsKey("done")) {
            extractBooleanArg(call, "done")
                ?: return error("'done' must be a valid boolean.")
        } else {
            null
        }

        val dueAtMs = if (call.args.containsKey("due_at_ms")) {
            extractLongArg(call, "due_at_ms")
                ?.takeIf { it >= 0L }
                ?: return error("'due_at_ms' must be a non-negative integer.")
        } else {
            null
        }

        val priority = if (call.args.containsKey("priority")) {
            val value = jsonStringArg(call, "priority")
                ?: return error("'priority' must be a valid string.")

            if (value !in validPriorities) {
                return error(
                    "Invalid priority. Allowed values: ${validPriorities.joinToString()}."
                )
            }

            TodoPriority.fromId(value)
        } else {
            null
        }

        val listName = if (call.args.containsKey("list_name")) {
            jsonStringArg(call, "list_name")
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: return error("'list_name' cannot be blank.")
        } else {
            null
        }

        val tags = if (call.args.containsKey("tags")) {
            parseToolTags(call.args.getValue("tags"))
                ?: return error("'tags' must be a string or an array of strings.")
        } else {
            null
        }

        val hasChanges = title != null || notes != null || done != null ||
            dueAtMs != null || priority != null || listName != null || tags != null
        if (!hasChanges) {
            return error(
                "Provide at least one field to update: title, notes, done, " +
                    "due_at_ms, priority, list_name, or tags."
            )
        }

        val updated = repo.update(
            id = id,
            title = title,
            notes = notes,
            done = done,
            dueAtMs = dueAtMs,
            priority = priority,
            tags = tags,
            listName = listName,
        ) ?: return error("Task #$id was not found.")

        return success(
            action = "updated",
            message = "Task #$id updated successfully.",
            todo = updated,
        )
    }

    // ----------------------------------------------------------------
    // Delete
    // ----------------------------------------------------------------

    suspend fun executeDeleteTodo(
        call: FunctionCall,
        repo: TodoRepository,
    ): JsonObject {
        val id = extractLongArg(call, "id")
            ?.takeIf { it > 0L }
            ?: return error("A valid positive task 'id' is required.")

        if (!repo.delete(id)) {
            return error("Task #$id was not found.")
        }

        return buildJsonObject {
            put("success", true)
            put("action", "deleted")
            put("id", id)
            put("message", "Task #$id deleted successfully.")
        }
    }

    // ----------------------------------------------------------------
    // List
    // ----------------------------------------------------------------

    suspend fun executeListTodos(
        call: FunctionCall,
        repo: TodoRepository,
    ): JsonObject {
        // Accept both JSON booleans and the documented string values; models
        // frequently send a bare boolean for a tri-state filter.
        val doneFilter = if (call.args.containsKey("done")) {
            val text = (call.args["done"] as? JsonPrimitive)
                ?.takeIf { it.isString || it.booleanOrNull != null }
                ?.content
                ?.lowercase()
            when (text) {
                "true" -> true
                "false" -> false
                "all" -> null
                else -> return error(
                    "Invalid 'done' filter. Use true, false, or all (as a boolean or quoted string)."
                )
            }
        } else {
            false
        }

        val priority = if (call.args.containsKey("priority")) {
            jsonStringArg(call, "priority")
                ?: return error("'priority' must be a non-empty string.")
        } else {
            null
        }

        if (priority != null &&
            priority != "all" &&
            priority !in validPriorities
        ) {
            return error(
                "Invalid priority. Allowed values: ${validPriorities.joinToString()}, all."
            )
        }

        val requestedListName = if (call.args.containsKey("list_name")) {
            jsonStringArg(call, "list_name")
                ?.trim()
                ?: return error("'list_name' must be a non-empty string or 'all'.")
        } else {
            null
        }
        val listName = requestedListName?.takeUnless { it == "all" }

        val limit = if (call.args.containsKey("limit")) {
            extractIntArg(call, "limit") ?: return error("'limit' must be an integer.")
        } else {
            20
        }

        if (limit !in 1..100) {
            return error("'limit' must be between 1 and 100.")
        }

        val results = repo.getAll(
            done = doneFilter,
            priority = priority?.takeUnless { it == "all" },
            listName = listName,
            limit = limit,
        )

        return buildJsonObject {
            put("success", true)
            put("action", "list")
            put("done_filter", doneFilter?.toString() ?: "all")
            put("priority_filter", priority ?: "all")
            put("list_filter", listName ?: "all")
            put("total_stored", repo.count())
            put("returned_count", results.size)
            put("todos", results.toJsonArray())
        }
    }

    // ----------------------------------------------------------------
    // Search
    // ----------------------------------------------------------------

    suspend fun executeSearchTodos(
        call: FunctionCall,
        repo: TodoRepository,
    ): JsonObject {
        val query = jsonStringArg(call, "query")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return error("'query' is required and cannot be blank.")

        val listName = if (call.args.containsKey("list_name")) {
            jsonStringArg(call, "list_name")
                ?.trim()
                ?: return error("'list_name' must be a string.")
        } else {
            null
        }?.takeUnless(String::isBlank)

        val limit = if (call.args.containsKey("limit")) {
            extractIntArg(call, "limit") ?: return error("'limit' must be an integer.")
        } else {
            10
        }

        if (limit !in 1..50) {
            return error("'limit' must be between 1 and 50.")
        }

        val results = repo.search(
            query = query,
            listName = listName,
            limit = limit,
        )

        return buildJsonObject {
            put("success", true)
            put("action", "search")
            put("query", query)
            put("count", results.size)
            put("todos", results.toJsonArray())
        }
    }

    // ----------------------------------------------------------------
    // Mark done
    // ----------------------------------------------------------------

    suspend fun executeMarkTodoDone(
        call: FunctionCall,
        repo: TodoRepository,
    ): JsonObject {
        val id = extractLongArg(call, "id")
            ?.takeIf { it > 0L }
            ?: return error("A valid positive task 'id' is required.")

        val updated = repo.markDone(id)
            ?: return error("Task #$id was not found.")

        return success(
            action = "marked_done",
            message = "Task #$id '${updated.title}' marked as done.",
            todo = updated,
        )
    }

    // ----------------------------------------------------------------
    // Mark undone
    // ----------------------------------------------------------------

    suspend fun executeMarkTodoUndone(
        call: FunctionCall,
        repo: TodoRepository,
    ): JsonObject {
        val id = extractLongArg(call, "id")
            ?.takeIf { it > 0L }
            ?: return error("A valid positive task 'id' is required.")

        val updated = repo.markUndone(id)
            ?: return error("Task #$id was not found.")

        return success(
            action = "reopened",
            message = "Task #$id '${updated.title}' reopened.",
            todo = updated,
        )
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private fun error(message: String): JsonObject =
        buildJsonObject {
            put("success", false)
            put("error", message)
        }

    private fun success(
        action: String,
        message: String,
        todo: TodoItem,
    ): JsonObject = buildJsonObject {
        put("success", true)
        put("action", action)
        put("message", message)
        put("todo", todo.toJsonObject())
    }

    private fun extractLongArg(
        call: FunctionCall,
        key: String,
    ): Long? {
        val primitive = call.args[key] as? JsonPrimitive ?: return null

        return primitive.longOrNull
            ?: primitive.content.trim().toLongOrNull()
    }

    private fun extractIntArg(
        call: FunctionCall,
        key: String,
    ): Int? {
        val primitive = call.args[key] as? JsonPrimitive ?: return null

        return primitive.intOrNull
            ?: primitive.content.trim().toIntOrNull()
    }

    private fun extractBooleanArg(
        call: FunctionCall,
        key: String,
    ): Boolean? {
        val primitive = call.args[key] as? JsonPrimitive ?: return null

        return primitive.booleanOrNull
    }

    private fun rawStringArg(call: FunctionCall, key: String): String? {
        val value = call.args[key] as? JsonPrimitive ?: return null
        return value.content.takeIf { value.isString }
    }

    private fun List<TodoItem>.toJsonArray(): JsonArray =
        buildJsonArray {
            forEach { add(it.toJsonObject()) }
        }

    private fun TodoItem.toJsonObject(): JsonObject =
        buildJsonObject {
            put("id", id)
            put("uuid", uuid)
            put("title", title)
            put("notes", notes)
            put("done", done)

            if (dueAtMs > 0L) {
                put("due_at_ms", dueAtMs)
            }

            put("priority", priority.id)

            put("tags", buildJsonArray {
                tags.forEach { add(JsonPrimitive(it)) }
            })

            put("list_name", listName)
            put("created_at_ms", createdAt)
            put("updated_at_ms", updatedAt)

            if (completedAt > 0L) {
                put("completed_at_ms", completedAt)
            }
        }
}

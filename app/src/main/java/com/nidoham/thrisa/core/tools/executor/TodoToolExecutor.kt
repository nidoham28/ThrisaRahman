package com.nidoham.thrisa.core.tools.executor

import com.nidoham.thrisa.core.database.todo.TodoItem
import com.nidoham.thrisa.core.database.todo.TodoPriority
import com.nidoham.thrisa.core.database.todo.TodoRepository
import com.nidoham.thrisa.core.tools.executor.BuiltinToolExecutor.jsonStringArg
import com.nidoham.thrisa.domain.FunctionCall
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

object TodoToolExecutor {

    suspend fun executeCreateTodo(
        call: FunctionCall,
        repo: TodoRepository,
    ): JsonObject {
        val title = jsonStringArg(call, "title")
        if (title.isNullOrBlank()) {
            return error("Argument 'title' is required and must not be blank.")
        }

        val notes = jsonStringArg(call, "notes") ?: ""
        val dueAtMs = extractLongArg(call, "due_at_ms") ?: 0L
        val priority = TodoPriority.fromId(jsonStringArg(call, "priority"))
        val tags = extractTagsArg(call, "tags")
        val listName = jsonStringArg(call, "list_name") ?: "inbox"

        val inserted = repo.insert(
            title = title,
            notes = notes,
            dueAtMs = dueAtMs,
            priority = priority,
            tags = tags,
            listName = listName,
        )

        return buildJsonObject {
            put("success", true)
            put("action", "created")
            put("message", "Task #${inserted.id} '${inserted.title}' created.")
            put("todo", inserted.toJsonObject())
        }
    }

    suspend fun executeUpdateTodo(
        call: FunctionCall,
        repo: TodoRepository,
    ): JsonObject {
        val id = extractLongArg(call, "id")
            ?: return error("A valid positive numeric 'id' is required to update a task.")

        val title = jsonStringArg(call, "title")
        val notes = jsonStringArg(call, "notes")
        val done = if (call.args.containsKey("done")) extractBooleanArg(call, "done") else null
        val dueAtMs = extractLongArg(call, "due_at_ms")
        val priority = if (call.args.containsKey("priority"))
            TodoPriority.fromId(jsonStringArg(call, "priority")) else null
        val tags = if (call.args.containsKey("tags")) extractTagsArg(call, "tags") else null
        val listName = jsonStringArg(call, "list_name")

        val updated = repo.update(
            id = id,
            title = title,
            notes = notes,
            done = done,
            dueAtMs = dueAtMs,
            priority = priority,
            tags = tags,
            listName = listName,
        ) ?: return error("Task with id #$id was not found.")

        return buildJsonObject {
            put("success", true)
            put("action", "updated")
            put("message", "Task #$id updated successfully.")
            put("todo", updated.toJsonObject())
        }
    }

    suspend fun executeDeleteTodo(
        call: FunctionCall,
        repo: TodoRepository,
    ): JsonObject {
        val id = extractLongArg(call, "id")
            ?: return error("A valid positive numeric 'id' is required to delete a task.")

        val deleted = repo.delete(id)
        return if (deleted) {
            buildJsonObject {
                put("success", true)
                put("action", "deleted")
                put("id", id)
                put("message", "Task #$id has been permanently deleted.")
            }
        } else {
            error("Task with id #$id was not found or has already been deleted.")
        }
    }

    suspend fun executeListTodos(
        call: FunctionCall,
        repo: TodoRepository,
    ): JsonObject {
        val doneFilter = if (call.args.containsKey("done")) {
            val raw = jsonStringArg(call, "done")
            if (raw == "all") null else raw?.toBooleanStrictOrNull()
        } else false // Default to false (pending tasks) if omitted

        val priority = jsonStringArg(call, "priority")
        val listName = jsonStringArg(call, "list_name")
        val limit = extractIntArg(call, "limit") ?: 20

        val results = repo.getAll(
            done = doneFilter,
            priority = priority,
            listName = listName,
            limit = limit
        )
        val total = repo.count()

        return buildJsonObject {
            put("success", true)
            put("action", "list")
            put("done_filter", doneFilter?.toString() ?: "all")
            put("total_stored", total)
            put("returned_count", results.size)
            put("todos", buildJsonArray { results.forEach { add(it.toJsonObject()) } })
        }
    }

    suspend fun executeSearchTodos(
        call: FunctionCall,
        repo: TodoRepository,
    ): JsonObject {
        val query = jsonStringArg(call, "query")
        val listName = jsonStringArg(call, "list_name")
        val limit = extractIntArg(call, "limit") ?: 10

        val results = repo.search(query = query, listName = listName, limit = limit)

        return buildJsonObject {
            put("success", true)
            put("action", "search")
            put("query", query ?: "")
            put("count", results.size)
            put("todos", buildJsonArray { results.forEach { add(it.toJsonObject()) } })
        }
    }

    suspend fun executeMarkTodoDone(
        call: FunctionCall,
        repo: TodoRepository,
    ): JsonObject {
        val id = extractLongArg(call, "id")
            ?: return error("A valid positive numeric 'id' is required.")

        val updated = repo.markDone(id) ?: return error("Task with id #$id was not found.")

        return buildJsonObject {
            put("success", true)
            put("action", "marked_done")
            put("message", "Task #$id '${updated.title}' marked as done.")
            put("todo", updated.toJsonObject())
        }
    }

    suspend fun executeMarkTodoUndone(
        call: FunctionCall,
        repo: TodoRepository,
    ): JsonObject {
        val id = extractLongArg(call, "id")
            ?: return error("A valid positive numeric 'id' is required.")

        val updated = repo.markUndone(id) ?: return error("Task with id #$id was not found.")

        return buildJsonObject {
            put("success", true)
            put("action", "reopened")
            put("message", "Task #$id '${updated.title}' reopened.")
            put("todo", updated.toJsonObject())
        }
    }

    private fun error(message: String): JsonObject = buildJsonObject { put("error", message) }

    private fun extractLongArg(call: FunctionCall, key: String): Long? =
        runCatching {
            val element = call.args[key] ?: return null
            element.jsonPrimitive.longOrNull
                ?: element.jsonPrimitive.content.trim().toLongOrNull()
        }.getOrNull()

    private fun extractIntArg(call: FunctionCall, key: String): Int? =
        runCatching {
            val element = call.args[key] ?: return null
            element.jsonPrimitive.intOrNull
                ?: element.jsonPrimitive.content.trim().toIntOrNull()
        }.getOrNull()

    private fun extractBooleanArg(call: FunctionCall, key: String): Boolean? =
        runCatching {
            val element = call.args[key] ?: return null
            element.jsonPrimitive.booleanOrNull
                ?: element.jsonPrimitive.content.trim().lowercase().toBooleanStrictOrNull()
        }.getOrNull()

    private fun extractTagsArg(call: FunctionCall, key: String): List<String> =
        runCatching {
            val element = call.args[key] ?: return emptyList()
            when (element) {
                is JsonArray -> element.mapNotNull { it.jsonPrimitive.content.takeIf(String::isNotBlank) }
                else -> {
                    val raw = element.jsonPrimitive.content
                    if (raw.isBlank()) emptyList()
                    else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                }
            }
        }.getOrDefault(emptyList())

    private fun TodoItem.toJsonObject(): JsonObject = buildJsonObject {
        put("id", id)
        put("uuid", uuid)
        put("title", title)
        put("notes", notes)
        put("done", done)
        if (dueAtMs > 0L) put("due_at_ms", dueAtMs)
        put("priority", priority.id)
        put("tags", buildJsonArray { tags.forEach { add(JsonPrimitive(it)) } })
        put("list_name", listName)
        put("created_at_ms", createdAt)
        put("updated_at_ms", updatedAt)
        if (completedAt > 0L) put("completed_at_ms", completedAt)
    }
}

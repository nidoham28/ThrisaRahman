package com.nidoham.thrisa.core.tools.executor

import com.nidoham.thrisa.core.database.note.NoteItem
import com.nidoham.thrisa.core.database.note.NoteRepository
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

object NoteToolExecutor {

    suspend fun executeCreateNote(
        call: FunctionCall,
        repo: NoteRepository,
    ): JsonObject {
        val title = jsonStringArg(call, "title")
        if (title.isNullOrBlank()) {
            return error("Argument 'title' is required and must not be blank.")
        }

        val content = jsonStringArg(call, "content") ?: ""
        val tags = extractTagsArg(call, "tags")
        val pinned = extractBooleanArg(call, "pinned") ?: false
        val color = jsonStringArg(call, "color") ?: ""

        val inserted = repo.insert(
            title = title,
            content = content,
            tags = tags,
            pinned = pinned,
            color = color,
        )

        return buildJsonObject {
            put("success", true)
            put("action", "created")
            put("message", "Note #${inserted.id} '${inserted.title}' saved successfully.")
            put("note", inserted.toJsonObject())
        }
    }

    suspend fun executeUpdateNote(
        call: FunctionCall,
        repo: NoteRepository,
    ): JsonObject {
        val id = extractLongArg(call, "id")
            ?: return error("A valid positive numeric 'id' is required to update a note.")

        val title = jsonStringArg(call, "title")
        val content = jsonStringArg(call, "content")
        val tags = if (call.args.containsKey("tags")) extractTagsArg(call, "tags") else null
        val pinned = if (call.args.containsKey("pinned")) extractBooleanArg(call, "pinned") else null
        val color = jsonStringArg(call, "color")

        val updated = repo.update(
            id = id,
            title = title,
            content = content,
            tags = tags,
            pinned = pinned,
            color = color,
        ) ?: return error("Note with id #$id was not found.")

        return buildJsonObject {
            put("success", true)
            put("action", "updated")
            put("message", "Note #$id updated successfully.")
            put("note", updated.toJsonObject())
        }
    }

    suspend fun executeDeleteNote(
        call: FunctionCall,
        repo: NoteRepository,
    ): JsonObject {
        val id = extractLongArg(call, "id")
            ?: return error("A valid positive numeric 'id' is required to delete a note.")

        val deleted = repo.delete(id)
        return if (deleted) {
            buildJsonObject {
                put("success", true)
                put("action", "deleted")
                put("id", id)
                put("message", "Note #$id has been permanently deleted.")
            }
        } else {
            error("Note with id #$id was not found or has already been deleted.")
        }
    }

    suspend fun executeListNotes(
        call: FunctionCall,
        repo: NoteRepository,
    ): JsonObject {
        val pinnedFilter = jsonStringArg(call, "pinned")?.let {
            if (it == "all") null else it.toBooleanStrictOrNull()
        }
        val limit = extractIntArg(call, "limit") ?: 20

        val allNotes = repo.getAll(limit = limit)
        val filtered = if (pinnedFilter != null) {
            allNotes.filter { it.pinned == pinnedFilter }
        } else {
            allNotes
        }
        val total = repo.count()

        return buildJsonObject {
            put("success", true)
            put("action", "list")
            put("total_stored", total)
            put("returned_count", filtered.size)
            put("notes", buildJsonArray { filtered.forEach { add(it.toJsonObject()) } })
        }
    }

    suspend fun executeSearchNotes(
        call: FunctionCall,
        repo: NoteRepository,
    ): JsonObject {
        val query = jsonStringArg(call, "query")
        val tags = jsonStringArg(call, "tags")
        val limit = extractIntArg(call, "limit") ?: 10

        val results = repo.search(query = query, tags = tags, limit = limit)

        return buildJsonObject {
            put("success", true)
            put("action", "search")
            put("query", query ?: "")
            put("count", results.size)
            put("notes", buildJsonArray { results.forEach { add(it.toJsonObject()) } })
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

    private fun NoteItem.toJsonObject(): JsonObject = buildJsonObject {
        put("id", id)
        put("uuid", uuid)
        put("title", title)
        put("content", content)
        put("tags", buildJsonArray { tags.forEach { add(JsonPrimitive(it)) } })
        put("pinned", pinned)
        if (color.isNotBlank()) put("color", color)
        put("created_at_ms", createdAt)
        put("updated_at_ms", updatedAt)
    }
}

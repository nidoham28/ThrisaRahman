
package com.naira.assistant.core.tools.executor

import com.naira.assistant.core.database.note.NoteItem
import com.naira.assistant.core.database.note.NoteRepository
import com.naira.assistant.domain.FunctionCall
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

object NoteToolExecutor {

    private const val DEFAULT_LIST_LIMIT = 20
    private const val MAX_LIST_LIMIT = 100

    private const val DEFAULT_SEARCH_LIMIT = 10
    private const val MAX_SEARCH_LIMIT = 50

    private const val MAX_TITLE_LENGTH = 200
    private const val MAX_CONTENT_LENGTH = 50_000
    private const val MAX_TAGS = 20
    private const val MAX_TAG_LENGTH = 50
    private const val MAX_COLOR_LENGTH = 50

    // CREATE NOTE

    suspend fun executeCreateNote(
        call: FunctionCall,
        repo: NoteRepository
    ): JsonObject = safely {
        val title = stringArg(call, "title")
            ?: return@safely error("A note title is required.")

        if (title.isBlank()) {
            return@safely error("The note title cannot be blank.")
        }

        if (title.length > MAX_TITLE_LENGTH) {
            return@safely error("The note title cannot exceed $MAX_TITLE_LENGTH characters.")
        }

        val content = stringArg(call, "content") ?: ""

        if (content.length > MAX_CONTENT_LENGTH) {
            return@safely error("Note content cannot exceed $MAX_CONTENT_LENGTH characters.")
        }

        val tags = if (hasArg(call, "tags")) {
            parseTags(call.args["tags"])
                ?: return@safely error("The 'tags' argument must be a string or an array of strings.")
        } else {
            emptyList()
        }

        val pinned = if (hasArg(call, "pinned")) {
            booleanArg(call, "pinned")
                ?: return@safely error("'pinned' must be a boolean.")
        } else {
            false
        }

        val color = stringArg(call, "color") ?: ""

        if (color.length > MAX_COLOR_LENGTH) {
            return@safely error("'color' cannot exceed $MAX_COLOR_LENGTH characters.")
        }

        val inserted = repo.insert(
            title = title.trim(),
            content = content,
            tags = tags,
            pinned = pinned,
            color = color.trim()
        )

        buildJsonObject {
            put("success", true)
            put("action", "created")
            put("message", "Note #${inserted.id} '${inserted.title}' saved successfully.")
            put("note", inserted.toJsonObject())
        }
    }

    // UPDATE NOTE

    suspend fun executeUpdateNote(
        call: FunctionCall,
        repo: NoteRepository
    ): JsonObject = safely {
        val id = longArg(call, "id")
            ?.takeIf { it > 0 }
            ?: return@safely error("A valid positive numeric 'id' is required.")

        val title = if (hasArg(call, "title")) {
            stringArg(call, "title")
                ?: return@safely error("'title' must be a string.")

        } else {
            null
        }

        if (title != null) {
            if (title.isBlank()) {
                return@safely error("The note title cannot be blank.")
            }

            if (title.length > MAX_TITLE_LENGTH) {
                return@safely error("The note title cannot exceed $MAX_TITLE_LENGTH characters.")
            }
        }

        val content = if (hasArg(call, "content")) {
            stringArg(call, "content")
                ?: return@safely error("'content' must be a string.")
        } else {
            null
        }

        if (content != null && content.length > MAX_CONTENT_LENGTH) {
            return@safely error("Note content cannot exceed $MAX_CONTENT_LENGTH characters.")
        }

        val tags = if (hasArg(call, "tags")) {
            parseTags(call.args["tags"])
                ?: return@safely error("'tags' must be a string or an array of strings.")
        } else {
            null
        }

        val pinned = if (hasArg(call, "pinned")) {
            booleanArg(call, "pinned")
                ?: return@safely error("'pinned' must be a boolean.")
        } else {
            null
        }

        val color = if (hasArg(call, "color")) {
            stringArg(call, "color")
                ?: return@safely error("'color' must be a string.")
        } else {
            null
        }

        if (color != null && color.length > MAX_COLOR_LENGTH) {
            return@safely error("'color' cannot exceed $MAX_COLOR_LENGTH characters.")
        }

        if (
            title == null &&
            content == null &&
            tags == null &&
            pinned == null &&
            color == null
        ) {
            return@safely error("Provide at least one field to update.")
        }

        val updated = repo.update(
            id = id,
            title = title?.trim(),
            content = content,
            tags = tags,
            pinned = pinned,
            color = color?.trim()
        ) ?: return@safely error("Note with ID #$id was not found.")

        buildJsonObject {
            put("success", true)
            put("action", "updated")
            put("message", "Note #$id updated successfully.")
            put("note", updated.toJsonObject())
        }
    }

    // DELETE NOTE

    suspend fun executeDeleteNote(
        call: FunctionCall,
        repo: NoteRepository
    ): JsonObject = safely {
        val id = longArg(call, "id")
            ?.takeIf { it > 0 }
            ?: return@safely error("A valid positive numeric 'id' is required.")

        val deleted = repo.delete(id)

        if (!deleted) {
            return@safely error(
                "Note with ID #$id was not found or has already been deleted."
            )
        }

        buildJsonObject {
            put("success", true)
            put("action", "deleted")
            put("id", id)
            put("message", "Note #$id has been permanently deleted.")
        }
    }

    // LIST NOTES

    suspend fun executeListNotes(
        call: FunctionCall,
        repo: NoteRepository
    ): JsonObject = safely {
        // Accept both JSON booleans and the documented string values; models
        // frequently send a bare boolean for a tri-state filter.
        val pinnedFilter: Boolean? = if (hasArg(call, "pinned")) {
            val text = (call.args["pinned"] as? JsonPrimitive)
                ?.takeIf { it.isString || it.booleanOrNull != null }
                ?.content
                ?.lowercase()
            when (text) {
                "true" -> true
                "false" -> false
                "all" -> null
                else -> return@safely error(
                    "'pinned' must be 'true', 'false', or 'all' (as a boolean or quoted string)."
                )
            }
        } else {
            null
        }

        val limit = if (hasArg(call, "limit")) {
            intArg(call, "limit")
                ?.takeIf { it in 1..MAX_LIST_LIMIT }
                ?: return@safely error(
                    "'limit' must be between 1 and $MAX_LIST_LIMIT."
                )
        } else {
            DEFAULT_LIST_LIMIT
        }

        // Fetch up to the maximum supported amount before applying the filter.
        // This avoids filtering an already-small result set.
        val allNotes = repo.getAll(limit = MAX_LIST_LIMIT)

        val filtered = allNotes
            .let { notes ->
                if (pinnedFilter == null) notes
                else notes.filter { it.pinned == pinnedFilter }
            }
            .take(limit)

        val total = repo.count()

        buildJsonObject {
            put("success", true)
            put("action", "list")
            put("total_stored", total)
            put("returned_count", filtered.size)
            put(
                "notes",
                buildJsonArray {
                    filtered.forEach { add(it.toJsonObject()) }
                }
            )
        }
    }

    // SEARCH NOTES

    suspend fun executeSearchNotes(
        call: FunctionCall,
        repo: NoteRepository
    ): JsonObject = safely {
        val query = stringArg(call, "query")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return@safely error("A non-empty search query is required.")

        if (query.length > 500) {
            return@safely error("The search query cannot exceed 500 characters.")
        }

        val tags = if (hasArg(call, "tags")) {
            stringArg(call, "tags")
                ?: return@safely error("'tags' must be a string.")
        } else {
            null
        }

        val limit = if (hasArg(call, "limit")) {
            intArg(call, "limit")
                ?.takeIf { it in 1..MAX_SEARCH_LIMIT }
                ?: return@safely error(
                    "'limit' must be between 1 and $MAX_SEARCH_LIMIT."
                )
        } else {
            DEFAULT_SEARCH_LIMIT
        }

        val results = repo.search(
            query = query,
            tags = tags,
            limit = limit
        )

        buildJsonObject {
            put("success", true)
            put("action", "search")
            put("query", query)
            put("count", results.size)
            put(
                "notes",
                buildJsonArray {
                    results.forEach { add(it.toJsonObject()) }
                }
            )
        }
    }

    // ARGUMENT HELPERS

    private fun hasArg(
        call: FunctionCall,
        key: String
    ): Boolean {
        return call.args.containsKey(key)
    }

    private fun stringArg(
        call: FunctionCall,
        key: String
    ): String? {
        val element = call.args[key] ?: return null

        if (element !is JsonPrimitive || !element.isString) {
            return null
        }

        return element.content
    }

    private fun longArg(
        call: FunctionCall,
        key: String
    ): Long? {
        val element = call.args[key] ?: return null

        if (element !is JsonPrimitive || element.isString) {
            return null
        }

        return element.longOrNull
    }

    private fun intArg(
        call: FunctionCall,
        key: String
    ): Int? {
        val element = call.args[key] ?: return null

        if (element !is JsonPrimitive || element.isString) {
            return null
        }

        return element.intOrNull
    }

    private fun booleanArg(
        call: FunctionCall,
        key: String
    ): Boolean? {
        val element = call.args[key] ?: return null

        if (element !is JsonPrimitive) {
            return null
        }

        return element.booleanOrNull
    }

    // TAG PARSER

    private fun parseTags(
        element: JsonElement?
    ): List<String>? {
        if (element == null || element == JsonNull) {
            return null
        }

        val rawTags = when (element) {
            is JsonArray -> {
                element.map { item ->
                    val primitive = item as? JsonPrimitive
                        ?: return null

                    if (!primitive.isString) return null

                    primitive.content
                }
            }

            is JsonPrimitive -> {
                if (!element.isString) return null

                if (element.content.isBlank()) {
                    emptyList()
                } else {
                    element.content.split(",")
                }
            }

            else -> return null
        }

        val tags = rawTags
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        if (tags.size > MAX_TAGS) return null
        if (tags.any { it.length > MAX_TAG_LENGTH }) return null

        return tags
    }

    // JSON RESPONSE HELPERS

    private fun error(message: String): JsonObject = buildJsonObject {
        put("success", false)
        put("error", message)
    }

    private suspend fun safely(
        block: suspend () -> JsonObject
    ): JsonObject {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error("The note operation failed. Please try again.")
        }
    }

    private fun NoteItem.toJsonObject(): JsonObject = buildJsonObject {
        put("id", id)
        put("uuid", uuid)
        put("title", title)
        put("content", content)

        put(
            "tags",
            buildJsonArray {
                tags.forEach { add(JsonPrimitive(it)) }
            }
        )

        put("pinned", pinned)

        if (color.isNotBlank()) {
            put("color", color)
        }

        put("created_at_ms", createdAt)
        put("updated_at_ms", updatedAt)
    }
}

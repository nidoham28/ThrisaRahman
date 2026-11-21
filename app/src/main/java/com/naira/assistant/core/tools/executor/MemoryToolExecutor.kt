
package com.naira.assistant.core.tools.executor

import com.naira.assistant.core.database.memory.MemoryItem
import com.naira.assistant.core.database.memory.MemoryRepository
import com.naira.assistant.domain.FunctionCall
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Executes Naira's persistent memory tools.
 *
 * Supports:
 * - insert_memory
 * - update_memory
 * - remove_memory
 * - merge_memories
 * - search_memories
 * - list_memories
 *
 * Ownership, authorization, and database transaction guarantees
 * must be enforced by the repository/database layer.
 */
object MemoryToolExecutor {

    // ---------------------------------------------------------
    // Configuration
    // ---------------------------------------------------------

    private val categories = setOf(
        "profile",
        "preference",
        "fact",
        "work",
        "relationship",
        "goal",
        "health",
        "general"
    )

    private const val DEFAULT_IMPORTANCE = 3
    private const val DEFAULT_SEARCH_LIMIT = 10
    private const val DEFAULT_LIST_LIMIT = 20

    private const val MAX_CONTENT_LENGTH = 2000
    private const val MAX_MERGED_CONTENT_LENGTH = 3000
    private const val MAX_TAGS = 24
    private const val MAX_TAG_LENGTH = 50
    private const val MAX_MERGE_IDS = 20

    // ---------------------------------------------------------
    // 1. INSERT MEMORY
    // ---------------------------------------------------------

    suspend fun executeInsertMemory(
        call: FunctionCall,
        repository: MemoryRepository
    ): JsonObject = safely("insert_memory") {

        val content = stringArg(call, "content")?.trim()
            ?: return@safely error("A non-empty 'content' is required.")

        if (content.isBlank() || content.length > MAX_CONTENT_LENGTH) {
            return@safely error(
                "'content' must contain 1-$MAX_CONTENT_LENGTH characters."
            )
        }

        val category = stringArg(call, "category") ?: "general"

        if (category !in categories) {
            return@safely error("Invalid memory category: $category")
        }

        val importance = intArg(call, "importance")
            ?: DEFAULT_IMPORTANCE

        if (importance !in 1..5) {
            return@safely error("'importance' must be between 1 and 5.")
        }

        val tags = if (call.args.containsKey("tags")) {
            parseTags(call.args["tags"])
                ?: return@safely error("Invalid 'tags'.")
        } else {
            emptyList()
        }

        val inserted = repository.insert(
            content = content,
            category = category,
            importance = importance,
            tags = tags,
            source = "ai_tool"
        )

        buildJsonObject {
            put("success", true)
            put("action", "inserted")
            put("message", "Memory #${inserted.id} successfully saved.")
            put("memory", inserted.toJsonObject())
        }
    }

    // ---------------------------------------------------------
    // 2. UPDATE MEMORY
    // ---------------------------------------------------------

    suspend fun executeUpdateMemory(
        call: FunctionCall,
        repository: MemoryRepository
    ): JsonObject = safely("update_memory") {

        val id = longArg(call, "id")
            ?: return@safely error("A valid positive memory ID is required.")

        if (id <= 0) {
            return@safely error("Memory ID must be positive.")
        }

        val content = stringArg(call, "content")?.trim()
            ?: return@safely error("A non-empty 'content' is required.")

        if (content.isBlank() || content.length > MAX_CONTENT_LENGTH) {
            return@safely error(
                "'content' must contain 1-$MAX_CONTENT_LENGTH characters."
            )
        }

        val category = if (call.args.containsKey("category")) {
            stringArg(call, "category")
                ?: return@safely error("Invalid 'category'.")
        } else {
            null
        }

        if (category != null && category !in categories) {
            return@safely error("Invalid memory category: $category")
        }

        val tags = if (call.args.containsKey("tags")) {
            parseTags(call.args["tags"])
                ?: return@safely error("Invalid 'tags'.")
        } else {
            null
        }

        val importance = if (call.args.containsKey("importance")) {
            intArg(call, "importance")
                ?: return@safely error("Invalid 'importance'.")
        } else {
            null
        }

        if (importance != null && importance !in 1..5) {
            return@safely error("'importance' must be between 1 and 5.")
        }

        val updated = repository.update(
            id = id,
            content = content,
            category = category,
            importance = importance,
            tags = tags
        )

        if (updated == null) {
            return@safely error("Memory #$id was not found.")
        }

        buildJsonObject {
            put("success", true)
            put("action", "updated")
            put("message", "Memory #$id successfully updated.")
            put("memory", updated.toJsonObject())
        }
    }

    // ---------------------------------------------------------
    // 3. REMOVE MEMORY
    // ---------------------------------------------------------

    suspend fun executeRemoveMemory(
        call: FunctionCall,
        repository: MemoryRepository
    ): JsonObject = safely("remove_memory") {

        val id = longArg(call, "id")
            ?: return@safely error("A valid positive memory ID is required.")

        if (id <= 0) {
            return@safely error("Memory ID must be positive.")
        }

        val reason = stringArg(call, "reason")

        if (reason != null && reason.length > 300) {
            return@safely error("'reason' cannot exceed 300 characters.")
        }

        val removed = repository.remove(id)

        if (!removed) {
            return@safely error(
                "Memory #$id was not found or has already been removed."
            )
        }

        buildJsonObject {
            put("success", true)
            put("action", "removed")
            put("id", id)
            put("message", "Memory #$id was successfully removed.")

            if (!reason.isNullOrBlank()) {
                put("reason", reason)
            }
        }
    }

    // ---------------------------------------------------------
    // 4. MERGE MEMORIES
    // ---------------------------------------------------------

    suspend fun executeMergeMemories(
        call: FunctionCall,
        repository: MemoryRepository
    ): JsonObject = safely("merge_memories") {

        val ids = memoryIdsArg(call)
            ?: return@safely error("Invalid 'memory_ids'.")

        if (ids.size !in 2..MAX_MERGE_IDS) {
            return@safely error(
                "'memory_ids' must contain 2-$MAX_MERGE_IDS IDs."
            )
        }

        if (ids.any { it <= 0 }) {
            return@safely error("All memory IDs must be positive.")
        }

        if (ids.distinct().size != ids.size) {
            return@safely error("Duplicate memory IDs are not allowed.")
        }

        val mergedContent = stringArg(call, "merged_content")?.trim()
            ?: return@safely error("A non-empty 'merged_content' is required.")

        if (
            mergedContent.isBlank() ||
            mergedContent.length > MAX_MERGED_CONTENT_LENGTH
        ) {
            return@safely error(
                "'merged_content' must contain 1-$MAX_MERGED_CONTENT_LENGTH characters."
            )
        }

        val category = if (call.args.containsKey("category")) {
            stringArg(call, "category")
                ?: return@safely error("Invalid 'category'.")
        } else {
            null
        }

        if (category != null && category !in categories) {
            return@safely error("Invalid memory category: $category")
        }

        val importance = if (call.args.containsKey("importance")) {
            intArg(call, "importance")
                ?: return@safely error("Invalid 'importance'.")
        } else {
            null
        }

        if (importance != null && importance !in 1..5) {
            return@safely error("'importance' must be between 1 and 5.")
        }

        val tags = if (call.args.containsKey("tags")) {
            parseTags(call.args["tags"])
                ?: return@safely error("Invalid 'tags'.")
        } else {
            null
        }

        // Must execute atomically inside the repository.
        val mergedItem = repository.merge(
            ids = ids,
            mergedContent = mergedContent,
            category = category,
            importance = importance,
            tags = tags
        )

        buildJsonObject {
            put("success", true)
            put("action", "merged")
            put("merged_into_id", mergedItem.id)

            put(
                "removed_ids",
                buildJsonArray {
                    ids.forEach { add(JsonPrimitive(it)) }
                }
            )

            put(
                "message",
                "Successfully merged ${ids.size} memories into #${mergedItem.id}."
            )

            put("memory", mergedItem.toJsonObject())
        }
    }

    // ---------------------------------------------------------
    // 5. SEARCH MEMORIES
    // ---------------------------------------------------------

    suspend fun executeSearchMemories(
        call: FunctionCall,
        repository: MemoryRepository
    ): JsonObject = safely("search_memories") {

        val query = if (call.args.containsKey("query")) {
            stringArg(call, "query")
                ?: return@safely error("Invalid 'query'.")
        } else {
            null
        }

        if (query != null && query.length > 200) {
            return@safely error("'query' cannot exceed 200 characters.")
        }

        val category = if (call.args.containsKey("category")) {
            stringArg(call, "category")
                ?: return@safely error("Invalid 'category'.")
        } else {
            null
        }

        if (category != null && category !in categories) {
            return@safely error("Invalid memory category: $category")
        }

        val tags = if (call.args.containsKey("tags")) {
            parseTags(call.args["tags"])
                ?: return@safely error("Invalid 'tags'.")
        } else {
            null
        }

        val limit = intArg(call, "limit") ?: DEFAULT_SEARCH_LIMIT

        if (limit !in 1..50) {
            return@safely error("'limit' must be between 1 and 50.")
        }

        val results = repository.search(
            query = query?.takeIf { it.isNotBlank() },
            category = category,
            limit = limit,
            tags = tags,
        )

        buildJsonObject {
            put("success", true)
            put("action", "search")
            put("query", query ?: "")
            put("category", category ?: "all")
            put("tags", buildJsonArray { tags.orEmpty().forEach { add(JsonPrimitive(it)) } })
            put("count", results.size)

            put(
                "memories",
                buildJsonArray {
                    results.forEach { add(it.toJsonObject()) }
                }
            )
        }
    }

    // ---------------------------------------------------------
    // 6. LIST MEMORIES
    // ---------------------------------------------------------

    suspend fun executeListMemories(
        call: FunctionCall,
        repository: MemoryRepository
    ): JsonObject = safely("list_memories") {

        val requestedCategory = if (call.args.containsKey("category")) {
            stringArg(call, "category")
                ?: return@safely error("Invalid 'category'.")
        } else {
            "all"
        }

        if (
            requestedCategory !in categories &&
            requestedCategory != "all"
        ) {
            return@safely error(
                "Invalid memory category: $requestedCategory"
            )
        }

        val limit = intArg(call, "limit") ?: DEFAULT_LIST_LIMIT

        if (limit !in 1..100) {
            return@safely error("'limit' must be between 1 and 100.")
        }

        val category = requestedCategory.takeUnless { it == "all" }

        val total = repository.count()

        val results = repository.getAll(
            category = category,
            limit = limit
        )

        buildJsonObject {
            put("success", true)
            put("action", "list")
            put("category", requestedCategory)
            put("total_stored", total)
            put("returned_count", results.size)

            put(
                "memories",
                buildJsonArray {
                    results.forEach { add(it.toJsonObject()) }
                }
            )
        }
    }

    // ---------------------------------------------------------
    // Argument Helpers
    // ---------------------------------------------------------

    private fun stringArg(
        call: FunctionCall,
        key: String
    ): String? {
        val element = call.args[key] ?: return null
        val primitive = element as? JsonPrimitive ?: return null

        return primitive.content.takeIf { primitive.isString }
    }

    private fun longArg(
        call: FunctionCall,
        key: String
    ): Long? {
        val element = call.args[key] ?: return null
        val primitive = element as? JsonPrimitive ?: return null

        if (primitive.isString) return null

        if (primitive.content == "true" || primitive.content == "false") {
            return null
        }

        return primitive.longOrNull
            ?: primitive.content.trim().toLongOrNull()
    }

    private fun intArg(
        call: FunctionCall,
        key: String
    ): Int? {
        val value = longArg(call, key) ?: return null

        return value
            .takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }
            ?.toInt()
    }

    // ---------------------------------------------------------
    // Tag Parsing
    // ---------------------------------------------------------

    private fun parseTags(
        element: JsonElement?
    ): List<String>? {
        element ?: return null

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

                element.content.split(",")
            }

            else -> return null
        }

        val tags = rawTags.map { it.trim() }

        if (tags.size !in 1..MAX_TAGS) return null

        if (tags.any { it.isBlank() || it.length > MAX_TAG_LENGTH }) {
            return null
        }

        if (tags.distinct().size != tags.size) {
            return null
        }

        return tags
    }

    // ---------------------------------------------------------
    // Memory ID Parsing
    // ---------------------------------------------------------

    private fun memoryIdsArg(
        call: FunctionCall
    ): List<Long>? {
        val element = call.args["memory_ids"] ?: return null

        val values = when (element) {
            is JsonArray -> {
                element.map { item ->
                    val primitive = item as? JsonPrimitive
                        ?: return null

                    if (primitive.isString) return null

                    if (
                        primitive.content == "true" ||
                        primitive.content == "false"
                    ) {
                        return null
                    }

                    primitive.longOrNull
                        ?: primitive.content.trim().toLongOrNull()
                        ?: return null
                }
            }

            is JsonPrimitive -> {
                if (!element.isString) return null

                val parts = element.content.split(",")

                parts.map {
                    it.trim().toLongOrNull() ?: return null
                }
            }

            else -> return null
        }

        return values
    }

    // ---------------------------------------------------------
    // Error Handling
    // ---------------------------------------------------------

    private fun error(message: String): JsonObject =
        buildJsonObject {
            put("success", false)
            put("error", message)
        }

    private suspend fun safely(
        operation: String,
        block: suspend () -> JsonObject
    ): JsonObject {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            error("The $operation operation failed. Please try again.")
        }
    }

    // ---------------------------------------------------------
    // Memory Serialization
    // ---------------------------------------------------------

    private fun MemoryItem.toJsonObject(): JsonObject =
        buildJsonObject {
            put("id", id)
            put("uuid", uuid)
            put("content", content)
            put("category", category)
            put("importance", importance)

            put(
                "tags",
                buildJsonArray {
                    tags.forEach { add(JsonPrimitive(it)) }
                }
            )

            put("updated_at", updatedAt)
        }
}

package com.nidoham.thrisa.core.tools.executor

import com.nidoham.thrisa.core.database.memory.MemoryItem
import com.nidoham.thrisa.core.database.memory.MemoryRepository
import com.nidoham.thrisa.core.tools.executor.BuiltinToolExecutor.jsonStringArg
import com.nidoham.thrisa.domain.FunctionCall
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Logic implementations for AI memory tools:
 *  - `insert_memory`
 *  - `update_memory`
 *  - `remove_memory`
 *  - `merge_memories`
 *  - `search_memories`
 *  - `list_memories`
 */
object MemoryToolExecutor {

    /**
     * Executes `insert_memory` to store a new fact, preference, or detail about the user.
     */
    suspend fun executeInsertMemory(call: FunctionCall, repository: MemoryRepository): JsonObject {
        val content = jsonStringArg(call, "content")
        if (content.isNullOrBlank()) {
            return buildJsonObject {
                put("error", "Argument 'content' is required and must not be blank.")
            }
        }

        val category = jsonStringArg(call, "category") ?: "general"
        val importance = extractIntArg(call, "importance") ?: 3
        val tags = extractTagsArg(call, "tags")

        val inserted = repository.insert(
            content = content,
            category = category,
            importance = importance,
            tags = tags,
            source = "ai_tool",
        )

        return buildJsonObject {
            put("success", true)
            put("action", "inserted")
            put("message", "Memory #${inserted.id} successfully saved.")
            put("memory", inserted.toJsonObject())
        }
    }

    /**
     * Executes `update_memory` to modify an existing memory by ID.
     */
    suspend fun executeUpdateMemory(call: FunctionCall, repository: MemoryRepository): JsonObject {
        val id = extractLongArg(call, "id")
        if (id == null || id <= 0) {
            return buildJsonObject {
                put("error", "A valid positive numeric 'id' is required to update a memory.")
            }
        }

        val content = jsonStringArg(call, "content")
        if (content.isNullOrBlank()) {
            return buildJsonObject {
                put("error", "Argument 'content' is required and must not be blank.")
            }
        }

        val category = jsonStringArg(call, "category")
        val importance = extractIntArg(call, "importance")
        val tags = if (call.args.containsKey("tags")) extractTagsArg(call, "tags") else null

        val updated = repository.update(
            id = id,
            content = content,
            category = category,
            importance = importance,
            tags = tags,
        )

        return if (updated != null) {
            buildJsonObject {
                put("success", true)
                put("action", "updated")
                put("message", "Memory #$id successfully updated.")
                put("memory", updated.toJsonObject())
            }
        } else {
            buildJsonObject {
                put("error", "Memory with id #$id was not found.")
            }
        }
    }

    /**
     * Executes `remove_memory` to delete/forget a memory by ID.
     */
    suspend fun executeRemoveMemory(call: FunctionCall, repository: MemoryRepository): JsonObject {
        val id = extractLongArg(call, "id")
        if (id == null || id <= 0) {
            return buildJsonObject {
                put("error", "A valid positive numeric 'id' is required to remove a memory.")
            }
        }

        val reason = jsonStringArg(call, "reason")
        val removed = repository.remove(id)

        return if (removed) {
            buildJsonObject {
                put("success", true)
                put("action", "removed")
                put("id", id)
                put("message", "Memory #$id was forgotten." + (reason?.let { " Reason: $it" } ?: ""))
            }
        } else {
            buildJsonObject {
                put("error", "Memory with id #$id was not found or has already been removed.")
            }
        }
    }

    /**
     * Executes `merge_memories` to atomically replace multiple older memories
     * with a single unified, updated memory.
     */
    suspend fun executeMergeMemories(call: FunctionCall, repository: MemoryRepository): JsonObject {
        val ids = extractLongListArg(call, "memory_ids")
        if (ids.size < 2) {
            return buildJsonObject {
                put("error", "Argument 'memory_ids' must contain at least 2 memory IDs to merge (e.g. '1, 2' or [1, 2]).")
            }
        }

        val mergedContent = jsonStringArg(call, "merged_content")
        if (mergedContent.isNullOrBlank()) {
            return buildJsonObject {
                put("error", "Argument 'merged_content' is required and must not be blank.")
            }
        }

        val category = jsonStringArg(call, "category")
        val importance = extractIntArg(call, "importance")
        val tags = if (call.args.containsKey("tags")) extractTagsArg(call, "tags") else null

        return try {
            val mergedItem = repository.merge(
                ids = ids,
                mergedContent = mergedContent,
                category = category,
                importance = importance,
                tags = tags,
            )
            buildJsonObject {
                put("success", true)
                put("action", "merged")
                put("merged_into_id", mergedItem.id)
                put("removed_ids", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } })
                put("message", "Successfully merged ${ids.size} memories into new memory #${mergedItem.id}.")
                put("memory", mergedItem.toJsonObject())
            }
        } catch (t: Throwable) {
            buildJsonObject {
                put("error", "Merge failed: ${t.message ?: t::class.simpleName}")
            }
        }
    }

    /**
     * Executes `search_memories` to query stored memories by text keyword or category.
     */
    suspend fun executeSearchMemories(call: FunctionCall, repository: MemoryRepository): JsonObject {
        val query = jsonStringArg(call, "query")
        val category = jsonStringArg(call, "category")
        val limit = extractIntArg(call, "limit") ?: 10

        val results = repository.search(query = query, category = category, limit = limit)

        return buildJsonObject {
            put("success", true)
            put("action", "search")
            put("query", query ?: "")
            put("category", category ?: "all")
            put("count", results.size)
            put("memories", buildJsonArray {
                for (item in results) {
                    add(item.toJsonObject())
                }
            })
        }
    }

    /**
     * Executes `list_memories` to retrieve all or category-filtered memories.
     */
    suspend fun executeListMemories(call: FunctionCall, repository: MemoryRepository): JsonObject {
        val category = jsonStringArg(call, "category")
        val limit = extractIntArg(call, "limit") ?: 20

        val total = repository.count()
        val results = repository.getAll(category = category, limit = limit)

        return buildJsonObject {
            put("success", true)
            put("action", "list")
            put("category", category ?: "all")
            put("total_stored", total)
            put("returned_count", results.size)
            put("memories", buildJsonArray {
                for (item in results) {
                    add(item.toJsonObject())
                }
            })
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun extractLongArg(call: FunctionCall, key: String): Long? =
        runCatching {
            val element = call.args[key] ?: return null
            element.jsonPrimitive.longOrNull
                ?: element.jsonPrimitive.content.trim().toLongOrNull()
        }.getOrNull()

    private fun extractIntArg(call: FunctionCall, key: String): Int? =
        runCatching {
            val element = call.args[key] ?: return null
            element.jsonPrimitive.content.trim().toIntOrNull()
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

    private fun extractLongListArg(call: FunctionCall, key: String): List<Long> =
        runCatching {
            val element = call.args[key] ?: return emptyList()
            when (element) {
                is JsonArray -> element.mapNotNull {
                    it.jsonPrimitive.longOrNull ?: it.jsonPrimitive.content.trim().toLongOrNull()
                }
                else -> {
                    val raw = element.jsonPrimitive.content
                    raw.split(",")
                        .mapNotNull { it.trim().toLongOrNull() }
                        .filter { it > 0 }
                }
            }
        }.getOrDefault(emptyList())

    private fun MemoryItem.toJsonObject(): JsonObject = buildJsonObject {
        put("id", id)
        put("uuid", uuid)
        put("content", content)
        put("category", category)
        put("importance", importance)
        put("tags", buildJsonArray { tags.forEach { add(JsonPrimitive(it)) } })
        put("updated_at", updatedAt)
    }
}

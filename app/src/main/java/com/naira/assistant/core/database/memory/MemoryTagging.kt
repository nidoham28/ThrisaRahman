package com.naira.assistant.core.database.memory

/** Builds stable retrieval tags and scores related memories without exact-phrase matching. */
internal object MemoryTagging {
    const val MAX_TAGS = 32

    private val wordPattern = Regex("[\\p{L}\\p{N}]{2,}")
    private val tagSeparators = Regex("[^\\p{L}\\p{N}]+")
    private val stopWords = setOf(
        "a", "about", "an", "and", "are", "as", "at", "be", "been", "but", "by",
        "did", "do", "does", "for", "from", "had", "has", "have", "how", "i", "in",
        "is", "it", "me", "my", "of", "on", "or", "please", "tell", "that", "the",
        "their", "them", "there", "this", "to", "was", "we", "what", "when", "where",
        "which", "who", "why", "with", "you", "your", "আমার", "আমি", "আমাকে", "আমরা",
        "কি", "কী", "কে", "কেন", "কখন", "কোথায়", "বল", "বলো", "মনে", "সম্পর্কে",
    )

    private val queryAliases = mapOf(
        "me" to setOf("user", "self", "personal", "username"),
        "my" to setOf("user", "self", "personal", "username"),
        "mine" to setOf("user", "self", "personal"),
        "myself" to setOf("user", "self", "personal"),
        "i" to setOf("user", "self", "personal"),
        "user" to setOf("me", "self", "personal"),
        "username" to setOf("user", "self", "name", "profile"),
        "name" to setOf("username", "profile"),
        "আমি" to setOf("user", "self", "me", "profile"),
        "আমার" to setOf("user", "self", "me", "profile"),
        "পছন্দ" to setOf("preference", "favorite", "like"),
        "নাম" to setOf("name", "username", "profile"),
        "কাজ" to setOf("work", "job", "career"),
        "পরিবার" to setOf("relationship", "family"),
        "like" to setOf("preference", "favorite", "enjoy", "love"),
        "likes" to setOf("preference", "favorite", "enjoy", "love"),
        "love" to setOf("preference", "favorite", "like", "enjoy"),
        "enjoy" to setOf("preference", "favorite", "like", "love"),
        "favorite" to setOf("preference", "like", "enjoy", "love"),
        "favourite" to setOf("preference", "like", "enjoy", "love"),
        "prefer" to setOf("preference", "favorite", "like"),
        "food" to setOf("meal", "dish", "cuisine", "eat"),
        "drink" to setOf("beverage", "coffee", "tea"),
        "beverage" to setOf("drink", "coffee", "tea"),
        "job" to setOf("work", "career", "occupation"),
        "career" to setOf("work", "job", "occupation"),
        "study" to setOf("education", "school", "university"),
        "partner" to setOf("relationship", "family", "wife", "husband"),
        "wife" to setOf("relationship", "partner", "family"),
        "husband" to setOf("relationship", "partner", "family"),
        "goal" to setOf("plan", "aim", "project"),
        "plan" to setOf("goal", "aim", "project"),
        "sleep" to setOf("health", "habit", "routine"),
        "fitness" to setOf("health", "habit", "exercise"),
        "health" to setOf("habit", "fitness", "sleep"),
        "remember" to setOf("memory", "fact", "profile", "preference"),
        "remembered" to setOf("memory", "fact", "profile", "preference"),
    )

    fun normalizeTag(value: String): String = value.trim()
        .lowercase()
        .replace(tagSeparators, " ")
        .trim()
        .replace(Regex("\\s+"), " ")
        .take(50)
        .trim()

    fun enrich(category: String, content: String, suppliedTags: List<String>): List<String> {
        val result = linkedMapOf<String, String>()
        fun add(raw: String) {
            val tag = normalizeTag(raw)
            if (tag.isNotBlank() && tag !in result && result.size < MAX_TAGS) result[tag] = tag
        }

        add(category)
        add("me")
        add("user")
        add("self")

        val words = terms(content).filterNot(stopWords::contains).distinct()
        if (category.equals("profile", ignoreCase = true) ||
            words.any { it in setOf("name", "named", "username", "called") }
        ) {
            add("username")
            add("name")
        }

        suppliedTags.forEach(::add)

        // Add meaningful adjacent concepts before single-word fallbacks.
        words.windowed(size = 2).forEach { pair -> add(pair.joinToString(" ")) }
        words.forEach(::add)
        return result.values.toList()
    }

    fun relevance(
        memory: MemoryItem,
        query: String?,
        requestedTags: List<String>,
    ): Int {
        val cleanQuery = query?.trim().orEmpty()
        val queryWords = terms(cleanQuery)
        val meaningfulWords = queryWords.filterNot(stopWords::contains).distinct()
        val storedTags = enrich(memory.category, memory.content, memory.tags).toSet()
        val contentWords = terms(memory.content).toSet()

        var score = 0
        var tagMatches = 0
        for (requestedTag in requestedTags.map(::normalizeTag).filter(String::isNotBlank).distinct()) {
            val requestedWords = terms(requestedTag).toSet()
            val aliases = requestedWords.flatMap { queryAliases[it].orEmpty() }.toSet()
            val matched = requestedTag in storedTags ||
                aliases.any(storedTags::contains) ||
                (requestedWords.isNotEmpty() && requestedWords.all { it in storedTags || it in contentWords })
            if (matched) {
                tagMatches++
                score += 12
            }
        }
        if (requestedTags.isNotEmpty() && tagMatches == 0) return 0

        for (word in meaningfulWords) {
            if (word in storedTags) score += 8
            if (word in contentWords) score += 5
            if (word == memory.category.lowercase()) score += 6

            for (alias in queryAliases[word].orEmpty()) {
                if (alias in storedTags) score += 2
                if (alias in contentWords) score += 1
                if (alias == memory.category.lowercase()) score += 3
            }
        }

        if (cleanQuery.isNotBlank() &&
            memory.content.contains(cleanQuery, ignoreCase = true)
        ) score += 10

        // Pronoun-only questions intentionally browse user memories instead of failing.
        if (score == 0 && queryWords.any { it in setOf("me", "my", "mine", "myself", "i", "আমার", "আমি") }) {
            score = 1
        }
        return score
    }

    private fun terms(value: String): List<String> =
        wordPattern.findAll(value.lowercase()).map { it.value }.toList()
}

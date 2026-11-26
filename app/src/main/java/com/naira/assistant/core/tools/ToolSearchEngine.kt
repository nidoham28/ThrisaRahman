package com.naira.assistant.core.tools

import kotlinx.serialization.json.JsonObject
import kotlin.math.sqrt

/** Lightweight BM25 search with optional precomputed cosine vectors. */
class ToolSearchEngine(
    private val tools: List<ToolDefinition>,
    private val semanticWeight: Double = 0.35,
) {
    private val documents = tools.map { tool ->
        val fields = tool.parameters["properties"] as? JsonObject
        tokenize(
            listOf(tool.name, tool.description, fields?.keys.orEmpty().joinToString(" "),
                tool.exampleIntents.joinToString(" ")).joinToString(" "),
        )
    }
    private val averageLength = documents.map { it.size }.average().takeIf { it > 0 } ?: 1.0
    private val documentFrequency = documents.flatMap { it.toSet() }.groupingBy { it }.eachCount()

    fun search(query: String, topK: Int = 5, queryEmbedding: List<Float>? = null): List<ToolDefinition> {
        if (topK <= 0 || tools.isEmpty()) return emptyList()
        val terms = tokenize(query)
        val semanticAvailable = queryEmbedding != null && queryEmbedding.isNotEmpty() &&
            tools.any { it.embeddings.isNotEmpty() }
        val vector = queryEmbedding
        return tools.mapIndexed { index, tool ->
            val document = documents[index]
            val frequencies = document.groupingBy { it }.eachCount()
            val bm25 = terms.sumOf { term ->
                val frequency = frequencies[term] ?: 0
                if (frequency == 0) 0.0 else {
                    val df = documentFrequency[term] ?: 0
                    val idf = kotlin.math.ln(1.0 + (tools.size - df + 0.5) / (df + 0.5))
                    val denominator = frequency + 1.2 * (1.0 - 0.75 + 0.75 * document.size / averageLength)
                    idf * frequency * 2.2 / denominator
                }
            }
            val cosine = if (semanticAvailable && vector != null && tool.embeddings.isNotEmpty()) {
                cosine(vector, tool.embeddings)
            } else 0.0
            val score = if (semanticAvailable) bm25 + semanticWeight * cosine else bm25
            Triple(tool, score, index)
        }.asSequence()
            .filter { it.second > 0.0 }
            .sortedWith(compareByDescending<Triple<ToolDefinition, Double, Int>> { it.second }.thenBy { it.third })
            .take(topK)
            .map { it.first }
            .toList()
    }

    private fun cosine(left: List<Float>, right: List<Float>): Double {
        if (left.size != right.size || left.isEmpty()) return 0.0
        var dot = 0.0
        var leftNorm = 0.0
        var rightNorm = 0.0
        for (i in left.indices) {
            dot += left[i] * right[i]
            leftNorm += left[i] * left[i]
            rightNorm += right[i] * right[i]
        }
        return if (leftNorm == 0.0 || rightNorm == 0.0) 0.0 else dot / sqrt(leftNorm * rightNorm)
    }

    private companion object {
        fun tokenize(text: String): List<String> = Regex("[\\p{L}\\p{N}_]+")
            .findAll(text.lowercase())
            .map { it.value }
            .toList()
    }
}

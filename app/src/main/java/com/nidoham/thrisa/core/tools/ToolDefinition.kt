package com.nidoham.thrisa.core.tools

import kotlinx.serialization.json.*

/**
 * A single tool (function declaration) exposed to the model: its wire name,
 * a natural-language description of when and how to use it, and the JSON
 * schema of its arguments. Instances are produced by the factories in
 * [core.tools.definition] and registered in [ToolRegistry].
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

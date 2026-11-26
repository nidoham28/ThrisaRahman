package com.naira.assistant.core.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** JSON Schema subset validation for tool arguments before handler dispatch. */
object ToolExecutor {
    fun validate(definition: ToolDefinition, arguments: JsonObject): String? {
        val properties = definition.parameters["properties"] as? JsonObject ?: return null
        val required = (definition.parameters["required"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        required.firstOrNull { it !in arguments }?.let { return "Missing required argument '$it'." }
        for ((name, value) in arguments) {
            val schema = properties[name] as? JsonObject ?: continue
            val type = schema["type"]?.jsonPrimitive?.contentOrNull ?: continue
            val valid = when (type) {
                "string" -> value is JsonPrimitive && value.isString
                "integer" -> value is JsonPrimitive && !value.isString && value.content.toLongOrNull() != null
                "number" -> value is JsonPrimitive && !value.isString && value.content.toDoubleOrNull() != null
                "boolean" -> value is JsonPrimitive && !value.isString && value.content in setOf("true", "false")
                "object" -> value is JsonObject
                "array" -> value is JsonArray
                else -> true
            }
            if (!valid) return "Argument '$name' must be $type."
            if (type == "object" && value is JsonObject) {
                val nestedProperties = schema["properties"] as? JsonObject
                val nestedRequired = (schema["required"] as? JsonArray).orEmpty()
                    .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                nestedRequired.firstOrNull { it !in value }?.let {
                    return "Argument '$name' is missing required field '$it'."
                }
                if (nestedProperties != null) {
                    for ((nestedName, nestedValue) in value) {
                        val nestedSchema = nestedProperties[nestedName] as? JsonObject ?: continue
                        val nestedType = nestedSchema["type"]?.jsonPrimitive?.contentOrNull ?: continue
                        val nestedValid = when (nestedType) {
                            "string" -> nestedValue is JsonPrimitive && nestedValue.isString
                            "integer" -> nestedValue is JsonPrimitive && !nestedValue.isString && nestedValue.content.toLongOrNull() != null
                            "number" -> nestedValue is JsonPrimitive && !nestedValue.isString && nestedValue.content.toDoubleOrNull() != null
                            "boolean" -> nestedValue is JsonPrimitive && !nestedValue.isString && nestedValue.content in setOf("true", "false")
                            "object" -> nestedValue is JsonObject
                            "array" -> nestedValue is JsonArray
                            else -> true
                        }
                        if (!nestedValid) return "Argument '$name.$nestedName' must be $nestedType."
                    }
                }
            }
            val enum = (schema["enum"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            if (enum != null && (value as? JsonPrimitive)?.content !in enum) {
                return "Argument '$name' must be one of: ${enum.joinToString()}."
            }
        }
        return null
    }
}

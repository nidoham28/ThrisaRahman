package com.naira.assistant.core.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistrySchemaTest {

    @Test
    fun `all required arguments exist in registered tool schemas`() {
        val tools = ToolRegistry.definitions

        assertTrue(tools.isNotEmpty())
        assertTrue(tools.map { it.name }.toSet().size == tools.size)

        tools.forEach { tool ->
            val properties = tool.parameters["properties"] as? JsonObject
                ?: error("${tool.name} must declare object properties.")
            val required = tool.parameters["required"]?.jsonArray.orEmpty()

            required.forEach { argument ->
                val name = argument.jsonPrimitive.content
                assertTrue(
                    "${tool.name} requires undeclared argument '$name'.",
                    properties.containsKey(name),
                )
            }
        }
    }

    @Test
    fun `all result limits are bounded and have an in-range default`() {
        val limits = ToolRegistry.definitions.mapNotNull { tool ->
            val properties = tool.parameters["properties"]?.jsonObject
            properties?.get("limit")?.let { tool.name to it.jsonObject }
        }

        assertTrue(limits.isNotEmpty())
        limits.forEach { (toolName, schema) ->
            val minimum = schema.getValue("minimum").jsonPrimitive.intOrNull
                ?: error("$toolName limit needs an integer minimum.")
            val maximum = schema.getValue("maximum").jsonPrimitive.intOrNull
                ?: error("$toolName limit needs an integer maximum.")
            val default = schema.getValue("default").jsonPrimitive.intOrNull
                ?: error("$toolName limit needs an integer default.")

            assertTrue("$toolName has an invalid limit range.", minimum > 0 && minimum <= maximum)
            assertTrue("$toolName limit default is out of range.", default in minimum..maximum)
        }
    }

    @Test
    fun `email lookup tool accepts a required query`() {
        val tool = ToolRegistry.definitions.single { it.name == "find_email_contact" }
        val properties = tool.parameters.getValue("properties").jsonObject
        val query = properties.getValue("query").jsonObject

        assertTrue(query.getValue("type").jsonPrimitive.content == "string")
        assertTrue(tool.parameters.getValue("required").jsonArray.any {
            it.jsonPrimitive.content == "query"
        })
    }
}

package com.naira.assistant.core.tools.definition

import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppToolDefinitionTest {

    @Test
    fun `app lookup and launch allow either app name or exact package name`() {
        val definitions = listOf(checkAppInstalledTool(), openAppTool())

        definitions.forEach { definition ->
            val properties = definition.parameters.getValue("properties").jsonObject
            assertTrue(properties.containsKey("app_name"))
            assertTrue(properties.containsKey("package_name"))

            val required = definition.parameters["required"]
                ?.jsonArray
                ?.map { it.jsonPrimitive.content }
                .orEmpty()
            assertFalse("app_name" in required)
            assertFalse("package_name" in required)
        }
    }

    @Test
    fun `installed app list limit matches executor bounds and default`() {
        val limit = listInstalledAppsTool()
            .parameters
            .getValue("properties")
            .jsonObject
            .getValue("limit")
            .jsonObject

        assertEquals(1, limit.getValue("minimum").jsonPrimitive.intOrNull)
        assertEquals(100, limit.getValue("maximum").jsonPrimitive.intOrNull)
        assertEquals(50, limit.getValue("default").jsonPrimitive.intOrNull)
    }
}

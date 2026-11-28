package com.naira.assistant.core.tools

import com.naira.assistant.core.tools.executor.RandomToolExecutor
import com.naira.assistant.domain.FunctionCall
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomToolExecutorTest {

    private fun call(args: kotlinx.serialization.json.JsonObject = buildJsonObject { }) =
        FunctionCall(name = "random_tool", args = args)

    @Test
    fun `random number uses default range 1 to 100`() {
        repeat(100) {
            val result = RandomToolExecutor.executeRandomNumber(call())
            assertTrue(result["success"]!!.jsonPrimitive.boolean)
            val number = result["number"]!!.jsonPrimitive.long
            assertTrue("number $number out of default range", number in 1..100)
            assertEquals(1L, result["min"]!!.jsonPrimitive.long)
            assertEquals(100L, result["max"]!!.jsonPrimitive.long)
        }
    }

    @Test
    fun `random number respects inclusive custom bounds`() {
        val args = buildJsonObject {
            put("min", JsonPrimitive(5))
            put("max", JsonPrimitive(5))
        }
        repeat(20) {
            val result = RandomToolExecutor.executeRandomNumber(call(args))
            assertEquals(5L, result["number"]!!.jsonPrimitive.long)
        }

        val wide = buildJsonObject {
            put("min", JsonPrimitive(-10))
            put("max", JsonPrimitive(10))
        }
        repeat(100) {
            val result = RandomToolExecutor.executeRandomNumber(call(wide))
            val number = result["number"]!!.jsonPrimitive.long
            assertTrue("number $number out of range", number in -10..10)
        }
    }

    @Test
    fun `random number accepts string bounds`() {
        val args = buildJsonObject {
            put("min", JsonPrimitive("3"))
            put("max", JsonPrimitive("7"))
        }
        repeat(50) {
            val result = RandomToolExecutor.executeRandomNumber(call(args))
            val number = result["number"]!!.jsonPrimitive.long
            assertTrue("number $number out of range", number in 3..7)
        }
    }

    @Test
    fun `random number rejects inverted range`() {
        val args = buildJsonObject {
            put("min", JsonPrimitive(10))
            put("max", JsonPrimitive(1))
        }
        val result = RandomToolExecutor.executeRandomNumber(call(args))
        assertFalse(result["success"]!!.jsonPrimitive.boolean)
        assertTrue(result["error"]!!.jsonPrimitive.content.contains("min"))
    }

    @Test
    fun `random number rejects oversized range`() {
        val args = buildJsonObject {
            put("min", JsonPrimitive(0))
            put("max", JsonPrimitive(2_000_000_000L))
        }
        val result = RandomToolExecutor.executeRandomNumber(call(args))
        assertFalse(result["success"]!!.jsonPrimitive.boolean)
        assertTrue(result["error"]!!.jsonPrimitive.content.contains("too large"))
    }

    @Test
    fun `random boolean reports consistent result and label`() {
        repeat(50) {
            val result = RandomToolExecutor.executeRandomBoolean(call())
            assertTrue(result["success"]!!.jsonPrimitive.boolean)
            val flipped = result["result"]!!.jsonPrimitive.boolean
            val label = result["result_label"]!!.jsonPrimitive.content
            assertEquals(if (flipped) "Yes/Heads" else "No/Tails", label)
            assertFalse(result.containsKey("guess"))
            assertFalse(result.containsKey("correct"))
        }
    }

    @Test
    fun `random boolean checks guess correctness`() {
        val guessingTrue = buildJsonObject { put("guess", JsonPrimitive(true)) }
        repeat(20) {
            val result = RandomToolExecutor.executeRandomBoolean(call(guessingTrue))
            val flipped = result["result"]!!.jsonPrimitive.boolean
            assertEquals(true, result["guess"]!!.jsonPrimitive.boolean)
            assertEquals(flipped, result["correct"]!!.jsonPrimitive.boolean)
        }

        val guessingHeads = buildJsonObject { put("guess", JsonPrimitive("heads")) }
        repeat(20) {
            val result = RandomToolExecutor.executeRandomBoolean(call(guessingHeads))
            val flipped = result["result"]!!.jsonPrimitive.boolean
            assertEquals(flipped, result["correct"]!!.jsonPrimitive.boolean)
        }
    }

    @Test
    fun `random boolean rejects unparseable guess`() {
        val args = buildJsonObject { put("guess", JsonPrimitive("maybe")) }
        val result = RandomToolExecutor.executeRandomBoolean(call(args))
        assertFalse(result["success"]!!.jsonPrimitive.boolean)
        assertTrue(result["error"]!!.jsonPrimitive.content.contains("guess"))
    }
}

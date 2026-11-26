package com.naira.assistant.core.tools.executor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TerminalArithmeticTest {

    @Test
    fun respectsOperatorPrecedenceAndParentheses() {
        assertEquals(14L, parseTerminalArithmetic("2 + 3 * 4").value)
        assertEquals(20L, parseTerminalArithmetic("(2 + 3) * 4").value)
    }

    @Test
    fun supportsIntegerDivisionRemaindersAndUnarySigns() {
        assertEquals(-2L, parseTerminalArithmetic("-7 / 3").value)
        assertEquals(1L, parseTerminalArithmetic("7 % 3").value)
        assertEquals(12L, parseTerminalArithmetic("3 * -(2 - 6)").value)
    }

    @Test
    fun resolvesArbitraryNamedVariablesBeforeShellEvaluation() {
        val result = parseTerminalArithmetic("(monthly_income - rent) / people", mapOf(
            "monthly_income" to 5000L,
            "rent" to 1000L,
            "people" to 4L,
        ))

        assertEquals(1000L, result.value)
        assertEquals("(((5000-1000))/4)", result.shellExpression)
    }

    @Test
    fun rejectsUnknownVariablesAndInvalidIdentifierCharacters() {
        assertThrows(IllegalArgumentException::class.java) {
            parseTerminalArithmetic("unknown + 1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseTerminalArithmetic("x;id", mapOf("x" to 2L))
        }
    }

    @Test
    fun rejectsCommandsAndUnsupportedSyntax() {
        assertThrows(IllegalArgumentException::class.java) {
            parseTerminalArithmetic("1 + 2; id")
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseTerminalArithmetic("1.5 + 2")
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseTerminalArithmetic("(1 + 2")
        }
    }

    @Test
    fun rejectsDivisionByZeroAndOverflow() {
        assertThrows(IllegalArgumentException::class.java) {
            parseTerminalArithmetic("10 / (3 - 3)")
        }
        val overflow = assertThrows(IllegalArgumentException::class.java) {
            parseTerminalArithmetic("9223372036854775807 + 1")
        }
        assertEquals(
            "Calculation exceeds the supported 64-bit integer range.",
            overflow.message,
        )
    }
}

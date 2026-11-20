
package com.naira.assistant.core.tools.executor

internal data class TerminalArithmeticResult(
    val value: Long,
    val shellExpression: String,
)

internal fun parseTerminalArithmetic(
    expression: String,
): TerminalArithmeticResult {
    require(expression.length in 1..128) {
        "Expression must contain between 1 and 128 characters."
    }

    return try {
        TerminalArithmeticParser(expression).parse()
    } catch (exception: ArithmeticException) {
        throw IllegalArgumentException(
            "Calculation exceeds the supported 64-bit integer range.",
            exception,
        )
    }
}

private class TerminalArithmeticParser(
    private val source: String,
) {
    private var position = 0
    private var depth = 0

    companion object {
        private const val MAX_NESTING_DEPTH = 32
    }

    fun parse(): TerminalArithmeticResult {
        val result = parseExpression()

        skipSpaces()

        require(position == source.length) {
            "Unexpected character in arithmetic expression."
        }

        return TerminalArithmeticResult(
            value = result.value,
            shellExpression = result.shellExpression,
        )
    }

    private fun parseExpression(): ParsedValue {
        var result = parseTerm()

        while (true) {
            skipSpaces()

            result = when {
                consume('+') -> combine(
                    result,
                    parseTerm(),
                    Math::addExact,
                    "+",
                )

                consume('-') -> combine(
                    result,
                    parseTerm(),
                    Math::subtractExact,
                    "-",
                )

                else -> return result
            }
        }
    }

    private fun parseTerm(): ParsedValue {
        var result = parseUnary()

        while (true) {
            skipSpaces()

            result = when {
                consume('*') -> combine(
                    result,
                    parseUnary(),
                    Math::multiplyExact,
                    "*",
                )

                consume('/') -> combine(
                    result,
                    parseUnary(),
                    ::divide,
                    "/",
                )

                consume('%') -> combine(
                    result,
                    parseUnary(),
                    ::remainder,
                    "%",
                )

                else -> return result
            }
        }
    }

    private fun parseUnary(): ParsedValue {
        skipSpaces()

        return when {
            consume('+') -> withDepth {
                parseUnary()
            }

            consume('-') -> withDepth {
                val value = parseUnary()

                ParsedValue(
                    value = Math.negateExact(value.value),
                    shellExpression = "-(${value.shellExpression})",
                )
            }

            else -> parsePrimary()
        }
    }

    private fun parsePrimary(): ParsedValue {
        skipSpaces()

        if (consume('(')) {
            return withDepth {
                val result = parseExpression()

                skipSpaces()

                require(consume(')')) {
                    "Missing closing parenthesis."
                }

                result.copy(
                    shellExpression = "(${result.shellExpression})",
                )
            }
        }

        val start = position

        while (
            position < source.length &&
            source[position] in '0'..'9'
        ) {
            position++
        }

        require(position > start) {
            "Expected an integer or parenthesized expression."
        }

        val value = source.substring(start, position).toLongOrNull()
            ?: throw IllegalArgumentException(
                "Integer is outside the supported 64-bit range."
            )

        return ParsedValue(
            value = value,
            shellExpression = value.toString(),
        )
    }

    private fun combine(
        left: ParsedValue,
        right: ParsedValue,
        operation: (Long, Long) -> Long,
        operator: String,
    ): ParsedValue {
        return ParsedValue(
            value = operation(left.value, right.value),
            shellExpression =
                "(${left.shellExpression}$operator${right.shellExpression})",
        )
    }

    private fun divide(left: Long, right: Long): Long {
        require(right != 0L) {
            "Division by zero."
        }

        if (left == Long.MIN_VALUE && right == -1L) {
            throw ArithmeticException("64-bit division overflow.")
        }

        return left / right
    }

    private fun remainder(left: Long, right: Long): Long {
        require(right != 0L) {
            "Division by zero."
        }

        return left % right
    }

    private inline fun <T> withDepth(block: () -> T): T {
        depth++

        require(depth <= MAX_NESTING_DEPTH) {
            "Expression nesting exceeds the maximum allowed depth."
        }

        return try {
            block()
        } finally {
            depth--
        }
    }

    private fun skipSpaces() {
        while (
            position < source.length &&
            source[position].isWhitespace()
        ) {
            position++
        }
    }

    private fun consume(character: Char): Boolean {
        if (
            position < source.length &&
            source[position] == character
        ) {
            position++
            return true
        }

        return false
    }

    private data class ParsedValue(
        val value: Long,
        val shellExpression: String,
    )
}

package com.nidoham.thrisa.core.tools.executor

internal data class TerminalArithmeticResult(
    val value: Long,
    val shellExpression: String,
)

internal fun parseTerminalArithmetic(expression: String): TerminalArithmeticResult {
    require(expression.length in 1..128) { "Expression must contain between 1 and 128 characters" }
    return TerminalArithmeticParser(expression).parse()
}

private class TerminalArithmeticParser(private val source: String) {
    private var position = 0

    fun parse(): TerminalArithmeticResult {
        val result = parseExpression()
        skipSpaces()
        require(position == source.length) { "Unexpected character in arithmetic expression" }
        return TerminalArithmeticResult(result.value, result.shellExpression)
    }

    private fun parseExpression(): ParsedValue {
        var result = parseTerm()
        while (true) {
            skipSpaces()
            result = when {
                consume('+') -> combine(result, parseTerm(), Math::addExact, "+")
                consume('-') -> combine(result, parseTerm(), Math::subtractExact, "-")
                else -> return result
            }
        }
    }

    private fun parseTerm(): ParsedValue {
        var result = parseUnary()
        while (true) {
            skipSpaces()
            result = when {
                consume('*') -> combine(result, parseUnary(), Math::multiplyExact, "*")
                consume('/') -> combine(result, parseUnary(), ::divide, "/")
                consume('%') -> combine(result, parseUnary(), ::remainder, "%")
                else -> return result
            }
        }
    }

    private fun parseUnary(): ParsedValue {
        skipSpaces()
        return when {
            consume('+') -> parseUnary()
            consume('-') -> {
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
            val result = parseExpression()
            skipSpaces()
            require(consume(')')) { "Missing closing parenthesis" }
            return result.copy(shellExpression = "(${result.shellExpression})")
        }

        val start = position
        while (position < source.length && source[position] in '0'..'9') position++
        require(position > start) { "Expected an integer or parenthesized expression" }
        val value = source.substring(start, position).toLongOrNull()
            ?: throw IllegalArgumentException("Integer is outside the supported 64-bit range")
        return ParsedValue(value, value.toString())
    }

    private fun combine(
        left: ParsedValue,
        right: ParsedValue,
        operation: (Long, Long) -> Long,
        operator: String,
    ): ParsedValue = ParsedValue(
        value = operation(left.value, right.value),
        shellExpression = "(${left.shellExpression}$operator${right.shellExpression})",
    )

    private fun divide(left: Long, right: Long): Long {
        require(right != 0L) { "Division by zero" }
        if (left == Long.MIN_VALUE && right == -1L) {
            throw IllegalArgumentException("Calculation exceeds the supported 64-bit range")
        }
        return left / right
    }

    private fun remainder(left: Long, right: Long): Long {
        require(right != 0L) { "Division by zero" }
        return left % right
    }

    private fun skipSpaces() {
        while (position < source.length && source[position] == ' ') position++
    }

    private fun consume(character: Char): Boolean {
        if (position < source.length && source[position] == character) {
            position++
            return true
        }
        return false
    }

    private data class ParsedValue(val value: Long, val shellExpression: String)
}

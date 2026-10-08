package com.example.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Robust calculator engine supporting basic arithmetic, decimals, percentages, and secret trigger matching.
 */
class CalculatorEngine {

    companion object {
        const val DEFAULT_SECRET_EXPRESSION = "2 + 9"

        /**
         * Normalizes an expression for matching (e.g. "2+9" or "2 + 9" -> "2+9").
         * Replaces display symbols like '×' with '*', '÷' with '/'.
         */
        fun normalizeExpression(expr: String): String {
            return expr.replace(" ", "")
                .replace("×", "*")
                .replace("÷", "/")
                .replace("−", "-")
                .trim()
        }

        /**
         * Validates whether a custom trigger expression is a valid arithmetic expression like "7*8" or "100-37".
         */
        fun isValidExpression(expr: String): Boolean {
            val normalized = normalizeExpression(expr)
            if (normalized.isBlank()) return false
            // Must contain at least one operator (+, -, *, /) and valid numbers
            val regex = Regex("""^(\d+(\.\d+)?)[+\-*/](\d+(\.\d+)?)$""")
            return regex.matches(normalized)
        }
    }

    /**
     * Evaluates a basic expression string like "5+3", "20*4", "100/5", "15-7", or percentages.
     * Returns the formatted result string or throws an exception on error (e.g. division by zero).
     */
    fun evaluate(expression: String): String {
        val normalized = normalizeExpression(expression)
        if (normalized.isEmpty()) return "0"

        // Handle single number or simple binary operations
        val regex = Regex("""^([-+]?\d+(\.\d+)?)\s*([+\-*/])\s*([-+]?\d+(\.\d+)?)$""")
        val match = regex.matchEntire(normalized)

        if (match != null) {
            val (leftStr, _, op, rightStr) = match.destructured
            val left = BigDecimal(leftStr)
            val right = BigDecimal(rightStr)
            val mc = MathContext(10, RoundingMode.HALF_UP)

            val result = when (op) {
                "+" -> left.add(right, mc)
                "-" -> left.subtract(right, mc)
                "*" -> left.multiply(right, mc)
                "/" -> {
                    if (right.compareTo(BigDecimal.ZERO) == 0) {
                        return "Error"
                    }
                    left.divide(right, mc)
                }
                else -> return "Error"
            }
            return formatResult(result)
        }

        // Try evaluating as a single number (e.g. "123.45")
        return try {
            val num = BigDecimal(normalized)
            formatResult(num)
        } catch (e: Exception) {
            "Error"
        }
    }

    /**
     * Checks if the evaluated expression matches the configured secret expression.
     */
    fun isSecretTrigger(enteredExpression: String, secretExpression: String): Boolean {
        val normEntered = normalizeExpression(enteredExpression)
        val normSecret = normalizeExpression(secretExpression)
        return normEntered.isNotEmpty() && normEntered == normSecret
    }

    private fun formatResult(value: BigDecimal): String {
        // Strip trailing zeros and avoid scientific notation if within reasonable length
        val stripped = value.stripTrailingZeros()
        val plain = stripped.toPlainString()
        return if (plain == "-0") "0" else plain
    }
}

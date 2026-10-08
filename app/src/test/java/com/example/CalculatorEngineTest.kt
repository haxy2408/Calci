package com.example

import com.example.calculator.CalculatorEngine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying:
 * - Normal calculator operations (addition, subtraction, multiplication, division, decimals, errors)
 * - 2 + 9 secret trigger identification
 * - Custom secret triggers (e.g. 7 * 8, 100 - 37)
 * - Expression normalization and validation
 */
class CalculatorEngineTest {

    private lateinit var engine: CalculatorEngine

    @Before
    fun setUp() {
        engine = CalculatorEngine()
    }

    @Test
    fun testNormalCalculations() {
        assertEquals("8", engine.evaluate("5+3"))
        assertEquals("80", engine.evaluate("20×4"))
        assertEquals("20", engine.evaluate("100÷5"))
        assertEquals("8", engine.evaluate("15-7"))
        assertEquals("10.5", engine.evaluate("5.25+5.25"))
        assertEquals("Error", engine.evaluate("10÷0"))
    }

    @Test
    fun testSecretTriggerDefault() {
        val defaultTrigger = "2 + 9"

        // Positive matches
        assertTrue(engine.isSecretTrigger("2+9", defaultTrigger))
        assertTrue(engine.isSecretTrigger("2 + 9", defaultTrigger))

        // Normal calculations must NOT trigger
        assertFalse(engine.isSecretTrigger("2+8", defaultTrigger))
        assertFalse(engine.isSecretTrigger("2", defaultTrigger))
        assertFalse(engine.isSecretTrigger("9", defaultTrigger))
        assertFalse(engine.isSecretTrigger("2+90", defaultTrigger))
    }

    @Test
    fun testCustomSecretTriggers() {
        val customTrigger1 = "7 × 8"
        assertTrue(engine.isSecretTrigger("7*8", customTrigger1))
        assertTrue(engine.isSecretTrigger("7×8", customTrigger1))
        assertFalse(engine.isSecretTrigger("7×7", customTrigger1))

        val customTrigger2 = "100 - 37"
        assertTrue(engine.isSecretTrigger("100-37", customTrigger2))
        assertTrue(engine.isSecretTrigger("100 - 37", customTrigger2))
        assertFalse(engine.isSecretTrigger("100-36", customTrigger2))
    }

    @Test
    fun testExpressionValidation() {
        assertTrue(CalculatorEngine.isValidExpression("2+9"))
        assertTrue(CalculatorEngine.isValidExpression("7 * 8"))
        assertTrue(CalculatorEngine.isValidExpression("100 - 37"))
        assertTrue(CalculatorEngine.isValidExpression("50.5 / 2"))

        assertFalse(CalculatorEngine.isValidExpression("123"))
        assertFalse(CalculatorEngine.isValidExpression("abc"))
        assertFalse(CalculatorEngine.isValidExpression("++"))
        assertFalse(CalculatorEngine.isValidExpression(""))
    }
}

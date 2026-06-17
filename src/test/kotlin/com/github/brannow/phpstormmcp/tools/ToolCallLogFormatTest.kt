package com.github.brannow.phpstormmcp.tools

import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolCallLogFormatTest {

    @Test
    fun `no args renders bare tool name`() {
        assertEquals("debug_snapshot", formatToolCall("debug_snapshot", null))
        assertEquals("debug_snapshot", formatToolCall("debug_snapshot", buildJsonObject {}))
    }

    @Test
    fun `scalar args render unquoted as key=value`() {
        val args = buildJsonObject {
            put("expression", "\$user->getName()")
            put("depth", 1)
        }
        assertEquals("debug_evaluate(expression=\$user->getName(), depth=1)", formatToolCall("debug_evaluate", args))
    }

    @Test
    fun `array args render with brackets`() {
        val args = buildJsonObject {
            put("include", buildJsonArray { add("source"); add("variables") })
            put("globals", true)
        }
        assertEquals("debug_snapshot(include=[source, variables], globals=true)", formatToolCall("debug_snapshot", args))
    }

    @Test
    fun `realistic argument lists log in full`() {
        // A multi-location breakpoint_add arg (~250 chars) must not be truncated under the generous cap.
        val locations = (1..6).joinToString(", ") { "src/Billing/PriceCalculator.php:$it" }
        val args = buildJsonObject { put("location", locations) }
        val out = formatToolCall("breakpoint_add", args)
        assertEquals("breakpoint_add(location=$locations)", out)
        assertTrue(!out.contains("…"), "expected no truncation, got: $out")
    }

    @Test
    fun `pathological multi-KB values are truncated with ellipsis`() {
        val long = "x".repeat(5000)
        val args = buildJsonObject { put("expression", long) }
        val out = formatToolCall("debug_evaluate", args)
        assertTrue(out.contains("…"), "expected ellipsis, got: $out")
        assertTrue(out.endsWith("…)"), "expected truncated value before closing paren, got: $out")
        // tool name + "expression=" + 2000-char-capped value + ")" — well under the raw 5000 chars
        assertTrue(out.length < 2100, "expected truncation, length was ${out.length}")
    }
}

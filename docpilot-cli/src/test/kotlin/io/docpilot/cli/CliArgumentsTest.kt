package io.docpilot.cli

import io.docpilot.cli.command.CliArguments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CliArgumentsTest {
    @Test
    fun `parses named options`() {
        val args = CliArguments.parse(listOf("--provider", "ollama", "--model", "qwen3:8b"))
        assertEquals("ollama", args.required("provider"))
        assertEquals("qwen3:8b", args.required("model"))
    }

    @Test
    fun `rejects duplicate options`() {
        assertFailsWith<IllegalArgumentException> {
            CliArguments.parse(listOf("--model", "a", "--model", "b"))
        }
    }
}

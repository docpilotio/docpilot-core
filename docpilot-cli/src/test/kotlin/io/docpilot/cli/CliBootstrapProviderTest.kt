package io.docpilot.cli

import io.docpilot.cli.bootstrap.CliBootstrap
import io.docpilot.provider.ollama.OllamaAiProvider
import io.docpilot.provider.openai.OpenAiProvider
import kotlin.test.*

class CliBootstrapProviderTest {
    @Test
    fun `explicit OpenAI selection requires configuration and never falls back`() {
        val error = assertFailsWith<IllegalArgumentException> {
            CliBootstrap(emptyMap()).createProvider("openai")
        }
        assertTrue(error.message.orEmpty().contains("OPENAI_API_KEY"))
    }

    @Test
    fun `explicit provider selection returns only requested provider`() {
        assertIs<OpenAiProvider>(CliBootstrap(mapOf("OPENAI_API_KEY" to "test-key")).createProvider("openai"))
        assertIs<OllamaAiProvider>(CliBootstrap(emptyMap()).createProvider("ollama"))
        assertFailsWith<IllegalArgumentException> { CliBootstrap(emptyMap()).createProvider("missing") }
    }
}

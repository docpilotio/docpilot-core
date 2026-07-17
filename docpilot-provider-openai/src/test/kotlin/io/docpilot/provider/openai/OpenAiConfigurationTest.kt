package io.docpilot.provider.openai

import java.time.Duration
import kotlin.test.*

class OpenAiConfigurationTest {
    @Test
    fun `loads environment overrides`() {
        val configuration = OpenAiConfiguration.fromEnvironment(
            mapOf(
                "OPENAI_API_KEY" to "secret",
                "DOCPILOT_OPENAI_BASE_URL" to "http://127.0.0.1:18080",
                "DOCPILOT_OPENAI_MODEL" to "gpt-5.6-luna",
                "DOCPILOT_OPENAI_TIMEOUT_SECONDS" to "30",
            ),
        )
        assertEquals("secret", configuration.apiKey)
        assertEquals("http://127.0.0.1:18080", configuration.baseUri.toString())
        assertEquals("gpt-5.6-luna", configuration.defaultModel)
        assertEquals(Duration.ofSeconds(30), configuration.requestTimeout)
    }

    @Test
    fun `requires API key`() {
        assertFailsWith<IllegalStateException> {
            OpenAiConfiguration.fromEnvironment(emptyMap())
        }
    }
}

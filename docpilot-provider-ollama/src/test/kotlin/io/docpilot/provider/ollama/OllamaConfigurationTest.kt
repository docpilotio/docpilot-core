package io.docpilot.provider.ollama

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class OllamaConfigurationTest {

    @Test
    fun `loads environment overrides`() {
        val configuration =
            OllamaConfiguration.fromEnvironment(
                mapOf(
                    "DOCPILOT_OLLAMA_BASE_URL" to
                        "http://127.0.0.1:22434",
                    "DOCPILOT_OLLAMA_MODEL" to
                        "llama3.2:3b",
                    "DOCPILOT_OLLAMA_TIMEOUT_SECONDS" to
                        "30",
                ),
            )

        assertEquals(
            "http://127.0.0.1:22434",
            configuration.baseUri.toString(),
        )
        assertEquals(
            "llama3.2:3b",
            configuration.defaultModel,
        )
        assertEquals(
            Duration.ofSeconds(30),
            configuration.requestTimeout,
        )
    }
}

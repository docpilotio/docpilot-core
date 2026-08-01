package io.docpilot.provider.openai

import java.net.URI
import java.time.Duration
import kotlin.test.*

class OpenAiConfigurationTest {
    @Test
    fun `loads trims and defaults environment configuration`() {
        val configuration = OpenAiConfiguration.fromEnvironment(mapOf("OPENAI_API_KEY" to "  secret  "))
        assertEquals("secret", configuration.apiKey)
        assertEquals(URI.create(OpenAiConfiguration.DEFAULT_BASE_URL), configuration.baseUri)
        assertEquals(OpenAiConfiguration.DEFAULT_MODEL, configuration.defaultModel)
        assertEquals(OpenAiConfiguration.DEFAULT_TIMEOUT, configuration.requestTimeout)
    }

    @Test
    fun `loads optional environment overrides`() {
        val configuration = OpenAiConfiguration.fromEnvironment(mapOf(
            "OPENAI_API_KEY" to "secret",
            "DOCPILOT_OPENAI_BASE_URL" to "http://127.0.0.1:18080",
            "DOCPILOT_OPENAI_MODEL" to " model ",
            "DOCPILOT_OPENAI_TIMEOUT_SECONDS" to "30",
            "OPENAI_ORGANIZATION_ID" to " org-test ",
            "OPENAI_PROJECT_ID" to " project-test ",
        ))
        assertEquals("model", configuration.defaultModel)
        assertEquals("org-test", configuration.organizationId)
        assertEquals("project-test", configuration.projectId)
    }

    @Test
    fun `rejects missing or blank API key`() {
        assertFailsWith<IllegalStateException> { OpenAiConfiguration.fromEnvironment(emptyMap()) }
        assertFailsWith<IllegalArgumentException> { OpenAiConfiguration.fromEnvironment(mapOf("OPENAI_API_KEY" to "  ")) }
    }

    @Test
    fun `rejects blank model invalid timeout and malformed base URL`() {
        assertFailsWith<IllegalArgumentException> { environment("DOCPILOT_OPENAI_MODEL", " ") }
        for (timeout in listOf("nope", "0", "301")) {
            assertFailsWith<IllegalArgumentException> { environment("DOCPILOT_OPENAI_TIMEOUT_SECONDS", timeout) }
        }
        for (url in listOf("not a uri", "ftp://example.com", "https:///missing-host", "https://user@example.com")) {
            assertFailsWith<IllegalArgumentException> { environment("DOCPILOT_OPENAI_BASE_URL", url) }
        }
    }

    @Test
    fun `accepts timeout boundaries`() {
        assertEquals(Duration.ofSeconds(1), environment("DOCPILOT_OPENAI_TIMEOUT_SECONDS", "1").requestTimeout)
        assertEquals(Duration.ofSeconds(300), environment("DOCPILOT_OPENAI_TIMEOUT_SECONDS", "300").requestTimeout)
    }

    @Test
    fun `masks secret in diagnostic output`() {
        val text = OpenAiConfiguration(apiKey = "sk-test-secret").toString()
        assertFalse(text.contains("sk-test-secret"))
        assertTrue(text.contains("<redacted>"))
    }

    private fun environment(name: String, value: String) =
        OpenAiConfiguration.fromEnvironment(mapOf("OPENAI_API_KEY" to "test-key", name to value))
}

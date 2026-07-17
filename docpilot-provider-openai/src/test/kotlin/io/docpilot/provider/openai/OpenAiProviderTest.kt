package io.docpilot.provider.openai

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.docpilot.core.model.ai.*
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import kotlin.test.*

class OpenAiProviderTest {

    @Test
    fun `generates response using Responses API`() {
        TestServer { exchange ->
            val requestBody = exchange.requestBody.readAllBytes()
                .toString(StandardCharsets.UTF_8)
            assertEquals("/v1/responses", exchange.requestURI.path)
            assertEquals("Bearer test-key", exchange.requestHeaders.getFirst("Authorization"))
            assertTrue(requestBody.contains("\"model\":\"gpt-5.6-terra\""))
            assertTrue(requestBody.contains("\"role\":\"system\""))
            assertTrue(requestBody.contains("\"role\":\"user\""))
            assertTrue(requestBody.contains("\"max_output_tokens\":500"))
            assertTrue(requestBody.contains("\"type\":\"json_object\""))
            exchange.respond(200, """
                {
                  "status":"completed",
                  "model":"gpt-5.6-terra",
                  "output_text":"{\"summary\":\"ok\"}",
                  "usage":{"input_tokens":20,"output_tokens":8,"total_tokens":28}
                }
            """.trimIndent())
        }.use { server ->
            val result = provider(server).generate(
                AiRequest(
                    modelId = AiModelId("gpt-5.6-terra"),
                    messages = listOf(
                        AiMessage(AiMessageRole.SYSTEM, "You are an architect."),
                        AiMessage(AiMessageRole.USER, "Analyze this project."),
                    ),
                    temperature = 0.2,
                    maxOutputTokens = 500,
                    responseFormat = AiResponseFormat.JSON,
                ),
            )
            val success = assertIs<AiGenerationResult.Success>(result)
            assertEquals("{\"summary\":\"ok\"}", success.response.content)
            assertEquals(AiFinishReason.STOP, success.response.finishReason)
            assertEquals(20, success.response.usage?.inputTokens)
            assertEquals(8, success.response.usage?.outputTokens)
        }
    }

    @Test
    fun `maps authentication error`() {
        TestServer { it.respond(401, """{"error":{"message":"Incorrect API key","code":"invalid_api_key"}}""") }
            .use { server ->
                val failure = assertIs<AiGenerationResult.Failure>(provider(server).generate(request()))
                assertEquals(AiErrorCode.AUTHENTICATION, failure.error.code)
            }
    }

    @Test
    fun `maps rate limit error`() {
        TestServer { it.respond(429, """{"error":{"message":"Rate limit reached","code":"rate_limit_exceeded"}}""") }
            .use { server ->
                val failure = assertIs<AiGenerationResult.Failure>(provider(server).generate(request()))
                assertEquals(AiErrorCode.RATE_LIMITED, failure.error.code)
                assertTrue(failure.error.retryable)
            }
    }

    @Test
    fun `rejects tool messages`() {
        val provider = OpenAiProvider(OpenAiConfiguration(apiKey = "test-key"))
        val failure = assertIs<AiGenerationResult.Failure>(provider.generate(
            AiRequest(
                modelId = AiModelId("gpt-5.6-terra"),
                messages = listOf(AiMessage(AiMessageRole.TOOL, "result")),
            ),
        ))
        assertEquals(AiErrorCode.INVALID_REQUEST, failure.error.code)
    }

    private fun provider(server: TestServer) = OpenAiProvider(
        OpenAiConfiguration(
            apiKey = "test-key",
            baseUri = URI.create(server.baseUrl),
        ),
    )

    private fun request() = AiRequest(
        modelId = AiModelId("gpt-5.6-terra"),
        messages = listOf(AiMessage(AiMessageRole.USER, "Hello")),
    )

    private class TestServer(handler: (HttpExchange) -> Unit) : AutoCloseable {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { handler(it) }
            start()
        }
        val baseUrl get() = "http://127.0.0.1:${server.address.port}"
        override fun close() = server.stop(0)
    }

    private fun HttpExchange.respond(status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        responseHeaders.add("Content-Type", "application/json")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}

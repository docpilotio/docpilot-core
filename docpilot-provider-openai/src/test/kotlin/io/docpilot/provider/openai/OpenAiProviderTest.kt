package io.docpilot.provider.openai

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.docpilot.core.model.ai.*
import java.net.InetSocketAddress
import java.net.URI
import java.net.ServerSocket
import java.time.Duration
import java.nio.charset.StandardCharsets
import kotlin.test.*

class OpenAiProviderTest {
    @Test
    fun `constructs deterministic Responses API request and parses all text blocks`() {
        val requests = mutableListOf<String>()
        TestServer { exchange ->
            requests += exchange.requestBody.readAllBytes().toString(StandardCharsets.UTF_8)
            assertEquals("/v1/responses", exchange.requestURI.path)
            assertEquals("Bearer test-key", exchange.requestHeaders.getFirst("Authorization"))
            assertEquals("org-test", exchange.requestHeaders.getFirst("OpenAI-Organization"))
            assertEquals("project-test", exchange.requestHeaders.getFirst("OpenAI-Project"))
            exchange.respond(200, success("안녕", " 세계"), mapOf("x-request-id" to "req_test"))
        }.use { server ->
            val request = AiRequest(
                modelId = AiModelId("gpt-5.6-terra"),
                messages = listOf(
                    AiMessage(AiMessageRole.SYSTEM, "한국어 \"규칙\"\n둘째 줄"),
                    AiMessage(AiMessageRole.USER, "분석"),
                ),
                temperature = 0.2,
                maxOutputTokens = 500,
            )
            val provider = provider(server, organizationId = "org-test", projectId = "project-test")
            val first = assertIs<AiGenerationResult.Success>(provider.generate(request))
            val second = assertIs<AiGenerationResult.Success>(provider.generate(request))
            assertEquals("안녕 세계", first.response.content)
            assertEquals(20, first.response.usage?.inputTokens)
            assertEquals(8, first.response.usage?.outputTokens)
            assertEquals("resp_test", first.response.metadata["responseId"])
            assertEquals("req_test", first.response.metadata["requestId"])
            assertEquals(requests[0], requests[1])
            assertFalse(requests[0].contains("test-key"))
            assertTrue(requests[0].contains("\\\"규칙\\\"\\n"))
        }
    }

    @Test
    fun `requests and validates JSON object output`() {
        TestServer { exchange ->
            val body = exchange.requestBody.readAllBytes().toString(StandardCharsets.UTF_8)
            assertTrue(body.contains("\"text\":{\"format\":{\"type\":\"json_object\"}}"))
            exchange.respond(200, success("{\"summary\":\"좋음\"}"))
        }.use { server ->
            val result = provider(server).generate(request(AiResponseFormat.JSON))
            assertIs<AiGenerationResult.Success>(result)
        }
        TestServer { it.respond(200, success("not json")) }.use { server ->
            val failure = assertIs<AiGenerationResult.Failure>(provider(server).generate(request(AiResponseFormat.JSON)))
            assertEquals(AiErrorCode.PROVIDER_FAILURE, failure.error.code)
        }
    }

    @Test
    fun `maps HTTP errors without exposing provider message or secret`() {
        val cases = mapOf(
            400 to AiErrorCode.INVALID_REQUEST, 401 to AiErrorCode.AUTHENTICATION,
            403 to AiErrorCode.AUTHENTICATION, 404 to AiErrorCode.MODEL_NOT_SUPPORTED,
            408 to AiErrorCode.TIMEOUT, 409 to AiErrorCode.PROVIDER_FAILURE,
            429 to AiErrorCode.RATE_LIMITED, 500 to AiErrorCode.UNAVAILABLE,
            502 to AiErrorCode.UNAVAILABLE, 503 to AiErrorCode.UNAVAILABLE,
        )
        cases.forEach { (status, expected) ->
            TestServer { it.respond(status, """{"error":{"message":"prompt and sk-test-key","code":"safe_code"}}""") }
                .use { server ->
                    val failure = assertIs<AiGenerationResult.Failure>(provider(server).generate(request()))
                    assertEquals(expected, failure.error.code, "HTTP $status")
                    assertFalse(failure.error.message.contains("prompt"))
                    assertFalse(failure.error.message.contains("sk-test-key"))
                    assertTrue(failure.error.message.contains("safe_code"))
                }
        }
    }

    @Test
    fun `handles malformed empty incomplete refusal and oversized responses`() {
        val bodies = listOf(
            "not-json",
            "{}",
            success(),
            successBody(status = "incomplete", incompleteReason = "max_output_tokens"),
            successBody(content = """[{"type":"refusal","refusal":"cannot"}]"""),
        )
        bodies.forEach { body ->
            TestServer { it.respond(200, body) }.use { server ->
                assertIs<AiGenerationResult.Failure>(provider(server).generate(request()))
            }
        }
        TestServer { it.respond(200, success("long output")) }.use { server ->
            val failure = provider(server, maxResponseBytes = 4).generate(request())
            assertIs<AiGenerationResult.Failure>(failure)
        }
    }

    @Test
    fun `maps connection failure and client timeout`() {
        val unusedPort = ServerSocket(0).use { it.localPort }
        val unavailable = OpenAiProvider(OpenAiConfiguration(
            "test-key", URI.create("http://127.0.0.1:$unusedPort"),
            requestTimeout = Duration.ofSeconds(1),
        )).generate(request())
        assertEquals(AiErrorCode.UNAVAILABLE, assertIs<AiGenerationResult.Failure>(unavailable).error.code)

        TestServer { exchange ->
            Thread.sleep(1_500)
            runCatching { exchange.respond(200, success("late")) }
        }.use { server ->
            val timedOut = OpenAiProvider(OpenAiConfiguration(
                "test-key", URI.create(server.baseUrl), requestTimeout = Duration.ofSeconds(1),
            )).generate(request())
            assertEquals(AiErrorCode.TIMEOUT, assertIs<AiGenerationResult.Failure>(timedOut).error.code)
        }
    }

    @Test
    fun `ignores unknown fields and rejects tool messages`() {
        TestServer { it.respond(200, success("ok").dropLast(1) + ",\"future\":true}") }.use { server ->
            assertIs<AiGenerationResult.Success>(provider(server).generate(request()))
        }
        val failure = OpenAiProvider(OpenAiConfiguration("test-key")).generate(
            AiRequest(AiModelId("model"), listOf(AiMessage(AiMessageRole.TOOL, "result"))),
        )
        assertEquals(AiErrorCode.INVALID_REQUEST, assertIs<AiGenerationResult.Failure>(failure).error.code)
    }

    private fun provider(
        server: TestServer,
        organizationId: String? = null,
        projectId: String? = null,
        maxResponseBytes: Int = OpenAiConfiguration.DEFAULT_MAX_RESPONSE_BYTES,
    ) = OpenAiProvider(OpenAiConfiguration(
        apiKey = "test-key", baseUri = URI.create(server.baseUrl),
        organizationId = organizationId, projectId = projectId,
        maxResponseBytes = maxResponseBytes,
    ))

    private fun request(format: AiResponseFormat = AiResponseFormat.TEXT) = AiRequest(
        AiModelId("gpt-5.6-terra"), listOf(AiMessage(AiMessageRole.USER, "Hello")),
        responseFormat = format,
    )

    private fun success(vararg text: String): String = successBody(content = text.joinToString(",") {
        """{"type":"output_text","text":${OpenAiJson.stringify(JsonValue.StringValue(it))},"annotations":[]}"""
    }.let { "[$it]" })

    private fun successBody(
        status: String = "completed",
        incompleteReason: String? = null,
        content: String = "[]",
    ) = """{"id":"resp_test","object":"response","status":"$status","model":"gpt-5.6-terra","output":[{"type":"reasoning","summary":[]},{"type":"message","role":"assistant","content":$content}],"usage":{"input_tokens":20,"output_tokens":8,"total_tokens":28}${incompleteReason?.let { ",\"incomplete_details\":{\"reason\":\"$it\"}" }.orEmpty()}}"""

    private class TestServer(handler: (HttpExchange) -> Unit) : AutoCloseable {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { handler(it) }
            start()
        }
        val baseUrl get() = "http://127.0.0.1:${server.address.port}"
        override fun close() = server.stop(0)
    }

    private fun HttpExchange.respond(status: Int, body: String, headers: Map<String, String> = emptyMap()) {
        headers.forEach(responseHeaders::add)
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}

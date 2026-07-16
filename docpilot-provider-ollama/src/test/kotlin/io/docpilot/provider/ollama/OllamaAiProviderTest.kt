package io.docpilot.provider.ollama

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.docpilot.core.model.ai.AiErrorCode
import io.docpilot.core.model.ai.AiFinishReason
import io.docpilot.core.model.ai.AiGenerationResult
import io.docpilot.core.model.ai.AiMessage
import io.docpilot.core.model.ai.AiMessageRole
import io.docpilot.core.model.ai.AiModelId
import io.docpilot.core.model.ai.AiRequest
import io.docpilot.core.model.ai.AiResponseFormat
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OllamaAiProviderTest {

    @Test
    fun `generates response using Ollama chat API`() {
        TestServer { exchange ->
            val requestBody = exchange.requestBody
                .readAllBytes()
                .toString(StandardCharsets.UTF_8)

            assertEquals("/api/chat", exchange.requestURI.path)
            assertTrue(requestBody.contains("\"model\":\"qwen3:8b\""))
            assertTrue(requestBody.contains("\"role\":\"system\""))
            assertTrue(requestBody.contains("\"role\":\"user\""))
            assertTrue(requestBody.contains("\"stream\":false"))
            assertTrue(requestBody.contains("\"format\":\"json\""))

            exchange.respond(
                status = 200,
                body = """
                    {
                      "model":"qwen3:8b",
                      "message":{
                        "role":"assistant",
                        "content":"{\"summary\":\"ok\"}"
                      },
                      "done":true,
                      "done_reason":"stop",
                      "prompt_eval_count":12,
                      "eval_count":7
                    }
                """.trimIndent(),
            )
        }.use { server ->
            val provider = provider(server)

            val result = provider.generate(
                AiRequest(
                    modelId = AiModelId("qwen3:8b"),
                    messages = listOf(
                        AiMessage(
                            role = AiMessageRole.SYSTEM,
                            content = "You are an architect.",
                        ),
                        AiMessage(
                            role = AiMessageRole.USER,
                            content = "Analyze this project.",
                        ),
                    ),
                    temperature = 0.2,
                    maxOutputTokens = 500,
                    responseFormat = AiResponseFormat.JSON,
                ),
            )

            val success =
                assertIs<AiGenerationResult.Success>(result)

            assertEquals(
                "{\"summary\":\"ok\"}",
                success.response.content,
            )
            assertEquals(
                AiFinishReason.STOP,
                success.response.finishReason,
            )
            assertEquals(12, success.response.usage?.inputTokens)
            assertEquals(7, success.response.usage?.outputTokens)
        }
    }

    @Test
    fun `maps missing model response to model not supported`() {
        TestServer { exchange ->
            exchange.respond(
                status = 404,
                body = """
                    {"error":"model 'missing' not found"}
                """.trimIndent(),
            )
        }.use { server ->
            val result = provider(server).generate(
                request(model = "missing"),
            )

            val failure =
                assertIs<AiGenerationResult.Failure>(result)

            assertEquals(
                AiErrorCode.MODEL_NOT_SUPPORTED,
                failure.error.code,
            )
            assertTrue(
                failure.error.message.contains("not found"),
            )
        }
    }

    @Test
    fun `maps invalid success response to provider failure`() {
        TestServer { exchange ->
            exchange.respond(
                status = 200,
                body = """{"model":"qwen3:8b","done":true}""",
            )
        }.use { server ->
            val result = provider(server).generate(request())

            val failure =
                assertIs<AiGenerationResult.Failure>(result)

            assertEquals(
                AiErrorCode.PROVIDER_FAILURE,
                failure.error.code,
            )
        }
    }

    private fun provider(
        server: TestServer,
    ): OllamaAiProvider =
        OllamaAiProvider(
            configuration = OllamaConfiguration(
                baseUri = URI.create(server.baseUrl),
                defaultModel = "qwen3:8b",
            ),
        )

    private fun request(
        model: String = "qwen3:8b",
    ): AiRequest =
        AiRequest(
            modelId = AiModelId(model),
            messages = listOf(
                AiMessage(
                    role = AiMessageRole.USER,
                    content = "Hello",
                ),
            ),
        )

    private class TestServer(
        handler: (HttpExchange) -> Unit,
    ) : AutoCloseable {
        private val server = HttpServer.create(
            InetSocketAddress("127.0.0.1", 0),
            0,
        ).apply {
            createContext("/") { exchange ->
                handler(exchange)
            }
            start()
        }

        val baseUrl: String
            get() =
                "http://127.0.0.1:${server.address.port}"

        override fun close() {
            server.stop(0)
        }
    }

    private fun HttpExchange.respond(
        status: Int,
        body: String,
    ) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        responseHeaders.add(
            "Content-Type",
            "application/json",
        )
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}

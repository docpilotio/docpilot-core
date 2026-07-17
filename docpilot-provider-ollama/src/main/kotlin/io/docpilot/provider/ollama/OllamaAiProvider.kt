package io.docpilot.provider.ollama

import io.docpilot.core.api.AiProvider
import io.docpilot.core.model.ai.AiCapability
import io.docpilot.core.model.ai.AiError
import io.docpilot.core.model.ai.AiErrorCode
import io.docpilot.core.model.ai.AiExecutionLocation
import io.docpilot.core.model.ai.AiFinishReason
import io.docpilot.core.model.ai.AiGenerationResult
import io.docpilot.core.model.ai.AiMessageRole
import io.docpilot.core.model.ai.AiModelId
import io.docpilot.core.model.ai.AiProviderDescriptor
import io.docpilot.core.model.ai.AiProviderId
import io.docpilot.core.model.ai.AiRequest
import io.docpilot.core.model.ai.AiResponse
import io.docpilot.core.model.ai.AiResponseFormat
import io.docpilot.core.model.ai.AiUsage
import java.io.IOException
import java.net.ConnectException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException

class OllamaAiProvider(
    private val configuration: OllamaConfiguration =
        OllamaConfiguration.fromEnvironment(),
    private val httpClient: HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(configuration.requestTimeout)
            .build(),
) : AiProvider {

    override val descriptor = AiProviderDescriptor(
        id = PROVIDER_ID,
        displayName = "Ollama",
        version = "0.1.0",
        executionLocation = AiExecutionLocation.LOCAL,
        capabilities = setOf(
            AiCapability.TEXT_GENERATION,
            AiCapability.STRUCTURED_OUTPUT,
        ),
    )

    override fun generate(
        request: AiRequest,
    ): AiGenerationResult {
        val model = request.modelId.value.ifBlank {
            configuration.defaultModel
        }
        val effectiveMaxOutputTokens =
            request.maxOutputTokens ?: DEFAULT_MAX_OUTPUT_TOKENS
        val body = OllamaJson.requestBody(
            model = model,
            messages = request.messages.map { message ->
                message.role.toOllamaRole() to message.content
            },
            temperature = request.temperature,
            maxOutputTokens = effectiveMaxOutputTokens,
            jsonResponse =
                request.responseFormat == AiResponseFormat.JSON,
            thinking = false,
        )

        println("[DEBUG] Ollama model: $model")
        println("[DEBUG] Request chars: ${body.length}")
        println("[DEBUG] Timeout: ${configuration.requestTimeout}")
        println("[DEBUG] think=false")

        val httpRequest = HttpRequest.newBuilder()
            .uri(
                configuration.baseUri.resolve("/api/chat"),
            )
            .timeout(configuration.requestTimeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        return try {
            val response = httpClient.send(
                httpRequest,
                HttpResponse.BodyHandlers.ofString(),
            )
            println("[DEBUG] HTTP status: ${response.statusCode()}")
            println("[DEBUG] Response chars: ${response.body().length}")
            parseResponse(
                statusCode = response.statusCode(),
                responseBody = response.body(),
                requestedModel = request.modelId,
            )
        } catch (exception: HttpTimeoutException) {
            failure(
                code = AiErrorCode.TIMEOUT,
                message = "Ollama request timed out.",
                retryable = true,
                exception = exception,
            )
        } catch (exception: ConnectException) {
            failure(
                code = AiErrorCode.UNAVAILABLE,
                message =
                    "Cannot connect to Ollama at " +
                        configuration.baseUri +
                        ". Start Ollama with 'ollama serve'.",
                retryable = true,
                exception = exception,
            )
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            failure(
                code = AiErrorCode.PROVIDER_FAILURE,
                message = "Ollama request was interrupted.",
                retryable = true,
                exception = exception,
            )
        } catch (exception: IOException) {
            failure(
                code = AiErrorCode.UNAVAILABLE,
                message =
                    "Ollama communication failed: " +
                        (exception.message ?: "I/O error"),
                retryable = true,
                exception = exception,
            )
        } catch (exception: RuntimeException) {
            failure(
                code = AiErrorCode.PROVIDER_FAILURE,
                message =
                    "Invalid Ollama response: " +
                        (exception.message ?: "Unknown response error"),
                retryable = false,
                exception = exception,
            )
        }
    }

    private fun parseResponse(
        statusCode: Int,
        responseBody: String,
        requestedModel: AiModelId,
    ): AiGenerationResult {
        if (statusCode !in 200..299) {
            val providerMessage =
                OllamaJson.errorMessage(responseBody)
                    ?: "HTTP $statusCode"

            val modelMissing =
                statusCode == 404 ||
                    providerMessage.contains(
                        "model",
                        ignoreCase = true,
                    )

            return failure(
                code = if (modelMissing) {
                    AiErrorCode.MODEL_NOT_SUPPORTED
                } else {
                    AiErrorCode.PROVIDER_FAILURE
                },
                message = "Ollama error: $providerMessage",
                retryable = statusCode >= 500,
            )
        }

        val content = OllamaJson.nestedStringField(
            responseBody,
            objectField = "message",
            field = "content",
        )?.takeIf(String::isNotBlank)
            ?: return failure(
                code = AiErrorCode.PROVIDER_FAILURE,
                message =
                    "Ollama response does not contain assistant content.",
                retryable = false,
            )

        val responseModel =
            OllamaJson.stringField(responseBody, "model")
                ?.let(::AiModelId)
                ?: requestedModel

        val finishReason = when (
            OllamaJson.stringField(
                responseBody,
                "done_reason",
            )?.lowercase()
        ) {
            "stop" -> AiFinishReason.STOP
            "length" -> AiFinishReason.LENGTH
            null -> AiFinishReason.UNKNOWN
            else -> AiFinishReason.UNKNOWN
        }

        val inputTokens =
            OllamaJson.intField(
                responseBody,
                "prompt_eval_count",
            )
        val outputTokens =
            OllamaJson.intField(
                responseBody,
                "eval_count",
            )

        return AiGenerationResult.Success(
            response = AiResponse(
                providerId = PROVIDER_ID,
                modelId = responseModel,
                content = content,
                finishReason = finishReason,
                usage =
                    if (inputTokens != null && outputTokens != null) {
                        AiUsage(
                            inputTokens = inputTokens,
                            outputTokens = outputTokens,
                        )
                    } else {
                        null
                    },
                metadata = mapOf(
                    "baseUri" to configuration.baseUri.toString(),
                ),
            ),
        )
    }

    private fun failure(
        code: AiErrorCode,
        message: String,
        retryable: Boolean,
        exception: Throwable? = null,
    ): AiGenerationResult.Failure =
        AiGenerationResult.Failure(
            error = AiError(
                code = code,
                message = message,
                retryable = retryable,
                providerId = PROVIDER_ID,
                causeType = exception?.javaClass?.name,
            ),
        )

    private fun AiMessageRole.toOllamaRole(): String =
        when (this) {
            AiMessageRole.SYSTEM -> "system"
            AiMessageRole.USER -> "user"
            AiMessageRole.ASSISTANT -> "assistant"
            AiMessageRole.TOOL -> "tool"
        }

    companion object {
        val PROVIDER_ID = AiProviderId("ollama")
        const val DEFAULT_MAX_OUTPUT_TOKENS = 1024
    }
}

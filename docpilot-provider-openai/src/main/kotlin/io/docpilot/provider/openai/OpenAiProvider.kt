package io.docpilot.provider.openai

import io.docpilot.core.api.AiProvider
import io.docpilot.core.model.ai.*
import java.io.IOException
import java.net.ConnectException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException

class OpenAiProvider(
    private val configuration: OpenAiConfiguration,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(configuration.requestTimeout)
        .build(),
) : AiProvider {

    override val descriptor = AiProviderDescriptor(
        id = PROVIDER_ID,
        displayName = "OpenAI",
        version = "0.1.0",
        executionLocation = AiExecutionLocation.REMOTE,
        capabilities = setOf(
            AiCapability.TEXT_GENERATION,
            AiCapability.STRUCTURED_OUTPUT,
        ),
    )

    override fun generate(request: AiRequest): AiGenerationResult {
        if (request.messages.any { it.role == AiMessageRole.TOOL }) {
            return failure(
                AiErrorCode.INVALID_REQUEST,
                "OpenAI provider does not yet support TOOL messages.",
                false,
            )
        }

        val body = OpenAiJson.requestBody(
            model = request.modelId.value,
            messages = request.messages.map { it.role.toOpenAiRole() to it.content },
            temperature = request.temperature,
            maxOutputTokens = request.maxOutputTokens,
            jsonResponse = request.responseFormat == AiResponseFormat.JSON,
        )

        val builder = HttpRequest.newBuilder()
            .uri(configuration.baseUri.resolve("/v1/responses"))
            .timeout(configuration.requestTimeout)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${configuration.apiKey}")

        configuration.organizationId?.let { builder.header("OpenAI-Organization", it) }
        configuration.projectId?.let { builder.header("OpenAI-Project", it) }

        return try {
            val response = httpClient.send(
                builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            parseResponse(response.statusCode(), response.body(), request.modelId)
        } catch (exception: HttpTimeoutException) {
            failure(AiErrorCode.TIMEOUT, "OpenAI request timed out.", true, exception)
        } catch (exception: ConnectException) {
            failure(AiErrorCode.UNAVAILABLE, "Cannot connect to OpenAI at ${configuration.baseUri}.", true, exception)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            failure(AiErrorCode.PROVIDER_FAILURE, "OpenAI request was interrupted.", true, exception)
        } catch (exception: IOException) {
            failure(AiErrorCode.UNAVAILABLE, "OpenAI communication failed: ${exception.message ?: "I/O error"}", true, exception)
        } catch (exception: RuntimeException) {
            failure(AiErrorCode.PROVIDER_FAILURE, "Invalid OpenAI response: ${exception.message ?: "Unknown error"}", false, exception)
        }
    }

    private fun parseResponse(
        statusCode: Int,
        body: String,
        requestedModel: AiModelId,
    ): AiGenerationResult {
        if (statusCode !in 200..299) {
            val providerCode = OpenAiJson.errorCode(body)
            val message = OpenAiJson.errorMessage(body) ?: "HTTP $statusCode"
            return failure(
                code = mapError(statusCode, providerCode),
                message = "OpenAI error: $message",
                retryable = statusCode == 408 || statusCode == 429 || statusCode >= 500,
            )
        }

        val content = OpenAiJson.stringField(body, "output_text")
            ?.takeIf(String::isNotBlank)
            ?: return failure(
                AiErrorCode.PROVIDER_FAILURE,
                "OpenAI response does not contain output_text.",
                false,
            )

        val model = OpenAiJson.stringField(body, "model")
            ?.let(::AiModelId)
            ?: requestedModel
        val status = OpenAiJson.stringField(body, "status")?.lowercase()
        val incompleteReason = OpenAiJson.nestedStringField(
            body,
            "incomplete_details",
            "reason",
        )
        val finishReason = when {
            status == "completed" -> AiFinishReason.STOP
            incompleteReason == "max_output_tokens" -> AiFinishReason.LENGTH
            incompleteReason == "content_filter" -> AiFinishReason.CONTENT_FILTER
            status == "failed" -> AiFinishReason.ERROR
            else -> AiFinishReason.UNKNOWN
        }
        val inputTokens = OpenAiJson.intField(body, "input_tokens")
        val outputTokens = OpenAiJson.intField(body, "output_tokens")

        return AiGenerationResult.Success(
            AiResponse(
                providerId = PROVIDER_ID,
                modelId = model,
                content = content,
                finishReason = finishReason,
                usage = if (inputTokens != null && outputTokens != null) {
                    AiUsage(inputTokens, outputTokens)
                } else null,
                metadata = mapOf("baseUri" to configuration.baseUri.toString()),
            ),
        )
    }

    private fun mapError(status: Int, providerCode: String?): AiErrorCode = when {
        status == 400 && providerCode?.contains("model", ignoreCase = true) == true -> AiErrorCode.MODEL_NOT_SUPPORTED
        status == 400 -> AiErrorCode.INVALID_REQUEST
        status == 401 || status == 403 -> AiErrorCode.AUTHENTICATION
        status == 404 -> AiErrorCode.MODEL_NOT_SUPPORTED
        status == 408 -> AiErrorCode.TIMEOUT
        status == 429 -> AiErrorCode.RATE_LIMITED
        status >= 500 -> AiErrorCode.UNAVAILABLE
        else -> AiErrorCode.PROVIDER_FAILURE
    }

    private fun failure(
        code: AiErrorCode,
        message: String,
        retryable: Boolean,
        exception: Throwable? = null,
    ) = AiGenerationResult.Failure(
        AiError(
            code = code,
            message = message,
            retryable = retryable,
            providerId = PROVIDER_ID,
            causeType = exception?.javaClass?.name,
        ),
    )

    private fun AiMessageRole.toOpenAiRole(): String = when (this) {
        AiMessageRole.SYSTEM -> "system"
        AiMessageRole.USER -> "user"
        AiMessageRole.ASSISTANT -> "assistant"
        AiMessageRole.TOOL -> error("TOOL role is not supported.")
    }

    companion object {
        val PROVIDER_ID = AiProviderId("openai")
    }
}

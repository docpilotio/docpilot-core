package io.docpilot.provider.openai

import io.docpilot.core.api.AiProvider
import io.docpilot.core.model.ai.*
import java.io.IOException
import java.net.ConnectException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.charset.StandardCharsets

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
                HttpResponse.BodyHandlers.ofInputStream(),
            )
            val responseBytes = response.body().use {
                it.readNBytes(configuration.maxResponseBytes + 1)
            }
            if (responseBytes.size > configuration.maxResponseBytes) {
                failure(
                    AiErrorCode.PROVIDER_FAILURE,
                    "OpenAI response exceeded the configured size limit.",
                    false,
                )
            } else {
                parseResponse(
                    response.statusCode(),
                    responseBytes.toString(StandardCharsets.UTF_8),
                    request,
                    response.headers().firstValue("x-request-id").orElse(null),
                )
            }
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
        request: AiRequest,
        requestId: String?,
    ): AiGenerationResult {
        if (statusCode !in 200..299) {
            val error = parseObjectOrNull(body)?.objectValue("error")
            val providerCode = error?.string("code")
            return failure(
                code = mapError(statusCode, providerCode),
                message = safeErrorMessage(statusCode, providerCode, requestId),
                retryable = statusCode == 408 || statusCode == 429 || statusCode >= 500,
            )
        }

        val root = parseObjectOrNull(body)
            ?: return failure(
                AiErrorCode.PROVIDER_FAILURE,
                "OpenAI returned an invalid success response${requestIdSuffix(requestId)}.",
                false,
            )
        val responseId = root.string("id")
            ?: return failure(
                AiErrorCode.PROVIDER_FAILURE,
                "OpenAI success response is missing its id${requestIdSuffix(requestId)}.",
                false,
            )
        val status = root.string("status")?.lowercase()
            ?: return failure(
                AiErrorCode.PROVIDER_FAILURE,
                "OpenAI success response is missing its status${requestIdSuffix(requestId)}.",
                false,
            )
        val incompleteReason = root.objectValue("incomplete_details")?.string("reason")
        if (status != "completed") {
            val finishReason = when (incompleteReason) {
                "max_output_tokens" -> AiFinishReason.LENGTH
                "content_filter" -> AiFinishReason.CONTENT_FILTER
                else -> AiFinishReason.ERROR
            }
            return failure(
                AiErrorCode.PROVIDER_FAILURE,
                "OpenAI response was $status (${finishReason.name.lowercase()})${requestIdSuffix(requestId)}.",
                status == "incomplete" && incompleteReason == "max_output_tokens",
            )
        }

        val texts = mutableListOf<String>()
        var refused = false
        root.array("output").forEach { outputValue ->
            val output = outputValue as? JsonValue.Object ?: return@forEach
            if (output.string("type") != "message") return@forEach
            output.array("content").forEach { contentValue ->
                val content = contentValue as? JsonValue.Object ?: return@forEach
                when (content.string("type")) {
                    "output_text" -> content.string("text")?.let(texts::add)
                    "refusal" -> refused = true
                }
            }
        }
        if (refused) {
            return failure(
                AiErrorCode.PROVIDER_FAILURE,
                "OpenAI refused the request${requestIdSuffix(requestId)}.",
                false,
            )
        }
        val content = texts.joinToString("").takeIf(String::isNotBlank)
            ?: return failure(
                AiErrorCode.PROVIDER_FAILURE,
                "OpenAI response did not contain text output${requestIdSuffix(requestId)}.",
                false,
            )
        if (request.responseFormat == AiResponseFormat.JSON) {
            val json = try { OpenAiJson.parse(content) } catch (_: RuntimeException) { null }
            if (json !is JsonValue.Object) {
                return failure(
                    AiErrorCode.PROVIDER_FAILURE,
                    "OpenAI JSON response was not a valid JSON object${requestIdSuffix(requestId)}.",
                    false,
                )
            }
        }

        val model = root.string("model")
            ?.let(::AiModelId)
            ?: request.modelId
        val usage = root.objectValue("usage")
        val inputTokens = usage?.int("input_tokens")
        val outputTokens = usage?.int("output_tokens")
        val metadata = linkedMapOf("responseId" to responseId)
        requestId?.let { metadata["requestId"] = it }

        return AiGenerationResult.Success(
            AiResponse(
                providerId = PROVIDER_ID,
                modelId = model,
                content = content,
                finishReason = AiFinishReason.STOP,
                usage = if (inputTokens != null && outputTokens != null) {
                    AiUsage(inputTokens, outputTokens)
                } else null,
                metadata = metadata,
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

    private fun parseObjectOrNull(body: String): JsonValue.Object? =
        try { OpenAiJson.parse(body) as? JsonValue.Object } catch (_: RuntimeException) { null }

    private fun safeErrorMessage(status: Int, providerCode: String?, requestId: String?): String =
        buildString {
            append("OpenAI request failed with HTTP ").append(status)
            providerCode?.takeIf(SAFE_ERROR_CODE::matches)?.let { append(" (code: ").append(it).append(')') }
            append(requestIdSuffix(requestId)).append('.')
        }

    private fun requestIdSuffix(requestId: String?): String =
        requestId?.takeIf(SAFE_REQUEST_ID::matches)?.let { ", request ID $it" }.orEmpty()

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
        private val SAFE_ERROR_CODE = Regex("[A-Za-z0-9_.-]{1,100}")
        private val SAFE_REQUEST_ID = Regex("[A-Za-z0-9_.-]{1,200}")
    }
}

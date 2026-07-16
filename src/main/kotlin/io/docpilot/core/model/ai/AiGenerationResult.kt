package io.docpilot.core.model.ai

sealed interface AiGenerationResult {
    data class Success(
        val response: AiResponse,
    ) : AiGenerationResult

    data class Failure(
        val error: AiError,
    ) : AiGenerationResult
}

data class AiError(
    val code: AiErrorCode,
    val message: String,
    val retryable: Boolean,
    val providerId: AiProviderId? = null,
    val causeType: String? = null,
) {
    init {
        require(message.isNotBlank()) {
            "AI error message must not be blank."
        }
        require(causeType == null || causeType.isNotBlank()) {
            "AI error causeType must be null or non-blank."
        }
    }
}

enum class AiErrorCode {
    PROVIDER_NOT_FOUND,
    MODEL_NOT_SUPPORTED,
    INVALID_REQUEST,
    AUTHENTICATION,
    RATE_LIMITED,
    UNAVAILABLE,
    TIMEOUT,
    PROVIDER_FAILURE,
    UNKNOWN,
}

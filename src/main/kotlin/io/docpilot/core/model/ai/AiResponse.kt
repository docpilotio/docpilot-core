package io.docpilot.core.model.ai

data class AiResponse(
    val providerId: AiProviderId,
    val modelId: AiModelId,
    val content: String,
    val finishReason: AiFinishReason,
    val usage: AiUsage? = null,
    val warnings: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
) {
    init {
        require(content.isNotBlank()) {
            "AI response content must not be blank."
        }
        require(warnings.none(String::isBlank)) {
            "AI response warnings must not be blank."
        }
        require(metadata.keys.none(String::isBlank)) {
            "AI response metadata keys must not be blank."
        }
    }
}

data class AiUsage(
    val inputTokens: Int,
    val outputTokens: Int,
) {
    init {
        require(inputTokens >= 0) {
            "AI usage inputTokens must not be negative."
        }
        require(outputTokens >= 0) {
            "AI usage outputTokens must not be negative."
        }
    }

    val totalTokens: Int
        get() = inputTokens + outputTokens
}

enum class AiFinishReason {
    STOP,
    LENGTH,
    TOOL_CALL,
    CONTENT_FILTER,
    ERROR,
    UNKNOWN,
}

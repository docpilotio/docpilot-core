package io.docpilot.core.model.ai

data class AiRequest(
    val modelId: AiModelId,
    val messages: List<AiMessage>,
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
    val responseFormat: AiResponseFormat = AiResponseFormat.TEXT,
    val metadata: Map<String, String> = emptyMap(),
) {
    init {
        require(messages.isNotEmpty()) {
            "AI request must contain at least one message."
        }
        require(temperature == null || temperature in 0.0..2.0) {
            "AI request temperature must be between 0.0 and 2.0."
        }
        require(maxOutputTokens == null || maxOutputTokens > 0) {
            "AI request maxOutputTokens must be greater than zero."
        }
        require(metadata.keys.none(String::isBlank)) {
            "AI request metadata keys must not be blank."
        }
    }
}

data class AiMessage(
    val role: AiMessageRole,
    val content: String,
) {
    init {
        require(content.isNotBlank()) {
            "AI message content must not be blank."
        }
    }
}

enum class AiMessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL,
}

enum class AiResponseFormat {
    TEXT,
    JSON,
}

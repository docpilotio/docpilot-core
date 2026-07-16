package io.docpilot.core.model.ai

@JvmInline
value class AiProviderId(
    val value: String,
) : Comparable<AiProviderId> {
    init {
        require(value.matches(ID_PATTERN)) {
            "AI provider ID must use lowercase letters, numbers, dots, or hyphens."
        }
    }

    override fun compareTo(other: AiProviderId): Int =
        value.compareTo(other.value)

    override fun toString(): String = value

    private companion object {
        val ID_PATTERN = Regex("[a-z0-9]+(?:[.-][a-z0-9]+)*")
    }
}

@JvmInline
value class AiModelId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) {
            "AI model ID must not be blank."
        }
    }

    override fun toString(): String = value
}

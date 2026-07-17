package io.docpilot.core.incremental.prompt

/** Token allowance available only to selected prompt context. */
data class PromptContextBudget(
    val maxTokens: Int,
) {
    init {
        require(maxTokens > 0) { "Prompt context maxTokens must be positive." }
    }
}

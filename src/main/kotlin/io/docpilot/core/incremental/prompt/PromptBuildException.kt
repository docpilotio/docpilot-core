package io.docpilot.core.incremental.prompt

/** Failure while constructing a deterministic prompt plan. */
sealed class PromptBuildException(
    message: String,
) : IllegalArgumentException(message) {

    class InsufficientTokenBudget(
        val availableTokens: Int,
        val requiredTokens: Int,
    ) : PromptBuildException(
        "Prompt token budget $availableTokens is smaller than the required minimum $requiredTokens.",
    )
}

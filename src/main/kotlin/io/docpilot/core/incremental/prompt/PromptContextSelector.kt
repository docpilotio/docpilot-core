package io.docpilot.core.incremental.prompt

/** Selects only context relevant to one generation job. */
fun interface PromptContextSelector {
    fun select(
        request: PromptBuildRequest,
        budget: PromptContextBudget,
    ): PromptContextSelection
}

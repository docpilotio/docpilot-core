package io.docpilot.core.incremental.prompt

/** Bounded context and its deterministic estimate. */
data class PromptContextSelection(
    val context: PromptContext,
    val estimatedTokens: Int,
    val warnings: List<PromptBuildWarning>,
) {
    init {
        require(estimatedTokens >= 0) { "Prompt context token estimate must not be negative." }
        require(
            warnings == warnings.sortedWith(
                compareBy<PromptBuildWarning> { it.code.name }.thenBy { it.message },
            ),
        ) {
            "Prompt warnings must use deterministic order."
        }
        require(warnings.distinct().size == warnings.size) {
            "Prompt warnings must be unique."
        }
    }
}

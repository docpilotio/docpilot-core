package io.docpilot.core.incremental.prompt

/** Structured prompt representation before provider-specific adaptation. */
data class PromptPlan(
    val systemInstruction: String,
    val taskInstruction: String,
    val context: PromptContext,
    val constraints: List<PromptConstraint>,
    val outputContract: PromptOutputContract,
    val estimatedInputTokens: Int,
    val inputTokenBudget: Int,
    val warnings: List<PromptBuildWarning>,
) {
    init {
        require(systemInstruction.isNotBlank()) { "System instruction must not be blank." }
        require(taskInstruction.isNotBlank()) { "Task instruction must not be blank." }
        require(constraints.isNotEmpty()) { "Prompt plan must include constraints." }
        require(constraints == constraints.sortedBy { it.id }) {
            "Prompt constraints must be sorted by id."
        }
        require(constraints.map { it.id }.distinct().size == constraints.size) {
            "Prompt constraint ids must be unique."
        }
        require(estimatedInputTokens > 0) {
            "Prompt estimatedInputTokens must be positive."
        }
        require(inputTokenBudget > 0) { "Prompt inputTokenBudget must be positive." }
        require(estimatedInputTokens <= inputTokenBudget) {
            "Prompt plan exceeds its input token budget."
        }
        require(
            warnings == warnings.sortedWith(
                compareBy<PromptBuildWarning> { it.code.name }.thenBy { it.message },
            ),
        ) {
            "Prompt warnings must use deterministic order."
        }
    }
}

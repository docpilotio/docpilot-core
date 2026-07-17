package io.docpilot.core.incremental.prompt

/** Stable, independently testable generation constraint. */
data class PromptConstraint(
    val id: String,
    val instruction: String,
) {
    init {
        require(ID_PATTERN.matches(id)) {
            "Prompt constraint id must use lowercase kebab-case."
        }
        require(instruction.isNotBlank()) {
            "Prompt constraint instruction must not be blank."
        }
    }

    companion object {
        private val ID_PATTERN = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
    }
}

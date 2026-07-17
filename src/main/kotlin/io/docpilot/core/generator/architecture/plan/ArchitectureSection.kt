package io.docpilot.core.generator.architecture.plan

/**
 * One focused generation unit in a multi-step architecture plan.
 */
data class ArchitectureSection(
    val id: ArchitectureSectionId,
    val title: String,
    val instruction: String,
    val order: Int,
    val maxOutputTokens: Int,
) {
    init {
        require(title.isNotBlank()) {
            "Architecture section title must not be blank."
        }
        require(instruction.isNotBlank()) {
            "Architecture section instruction must not be blank."
        }
        require(order > 0) {
            "Architecture section order must be greater than zero."
        }
        require(maxOutputTokens > 0) {
            "Architecture section maxOutputTokens must be greater than zero."
        }
    }
}

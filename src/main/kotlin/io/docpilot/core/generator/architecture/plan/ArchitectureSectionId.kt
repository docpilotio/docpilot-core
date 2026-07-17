package io.docpilot.core.generator.architecture.plan

/**
 * Stable identifier for one architecture-document section.
 */
@JvmInline
value class ArchitectureSectionId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) {
            "Architecture section id must not be blank."
        }
        require(ID_PATTERN.matches(value)) {
            "Architecture section id must use lowercase kebab-case."
        }
    }

    override fun toString(): String = value

    companion object {
        private val ID_PATTERN = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
    }
}

package io.docpilot.core.incremental.prompt

import io.docpilot.core.generator.architecture.plan.ArchitectureSectionId

/** Provider-neutral description of the required response shape. */
data class PromptOutputContract(
    val format: PromptOutputFormat,
    val sectionId: ArchitectureSectionId,
    val includeHeading: Boolean,
    val allowAdditionalSections: Boolean,
)

enum class PromptOutputFormat {
    MARKDOWN_SECTION,
    MARKDOWN_DOCUMENT,
    JSON,
}

package io.docpilot.core.incremental.prompt

import io.docpilot.core.incremental.ProjectChangeSet
import io.docpilot.core.incremental.planning.GenerationJob
import io.docpilot.core.model.knowledge.KnowledgeBuildResult

/** Inputs required to build one provider-neutral incremental prompt plan. */
data class PromptBuildRequest(
    val job: GenerationJob,
    val knowledge: KnowledgeBuildResult,
    val changes: ProjectChangeSet,
    val previousSectionContent: String? = null,
) {
    init {
        require(previousSectionContent == null || previousSectionContent.isNotBlank()) {
            "previousSectionContent must be null or non-blank."
        }
    }
}

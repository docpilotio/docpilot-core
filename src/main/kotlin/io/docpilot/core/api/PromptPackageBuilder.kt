package io.docpilot.core.api

import io.docpilot.core.model.knowledge.KnowledgeBuildResult
import io.docpilot.core.model.prompt.PromptPackage

/**
 * Builds deterministic AI-input artifacts from verified knowledge.
 */
fun interface PromptPackageBuilder {
    fun build(
        knowledge: KnowledgeBuildResult,
    ): PromptPackage
}

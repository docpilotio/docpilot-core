package io.docpilot.core.incremental.knowledge

import io.docpilot.core.incremental.ProjectChangeSet
import io.docpilot.core.model.knowledge.KnowledgeBuildResult

/** Identifies the bounded knowledge subset affected by a project change set. */
fun interface IncrementalKnowledgeAnalyzer {
    fun analyze(
        knowledge: KnowledgeBuildResult,
        changes: ProjectChangeSet,
    ): IncrementalKnowledgeImpact
}

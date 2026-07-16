package io.docpilot.core.api

import io.docpilot.core.model.knowledge.KnowledgeBuildResult
import io.docpilot.core.model.knowledge.KnowledgeGraph
import io.docpilot.core.model.source.SourceIndex

/**
 * Builds deterministic software knowledge from a source index.
 */
interface KnowledgeGraphBuilder {

    fun build(sourceIndex: SourceIndex): KnowledgeGraph =
        buildWithEvidence(sourceIndex).graph

    fun buildWithEvidence(
        sourceIndex: SourceIndex,
    ): KnowledgeBuildResult
}

package io.docpilot.core.api

import io.docpilot.core.model.knowledge.KnowledgeGraph
import io.docpilot.core.model.source.SourceIndex

/**
 * Builds a deterministic software knowledge graph from a source index.
 */
fun interface KnowledgeGraphBuilder {
    fun build(sourceIndex: SourceIndex): KnowledgeGraph
}

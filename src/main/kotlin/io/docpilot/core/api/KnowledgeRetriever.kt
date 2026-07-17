package io.docpilot.core.api

import io.docpilot.core.model.knowledge.KnowledgeBuildResult
import io.docpilot.core.model.knowledge.KnowledgeQuery
import io.docpilot.core.model.knowledge.KnowledgeResult

/**
 * Retrieves a deterministic, evidence-backed subset of built knowledge.
 */
fun interface KnowledgeRetriever {
    fun retrieve(
        knowledge: KnowledgeBuildResult,
        query: KnowledgeQuery,
    ): KnowledgeResult
}

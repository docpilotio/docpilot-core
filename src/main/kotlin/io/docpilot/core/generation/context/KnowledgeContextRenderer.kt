package io.docpilot.core.generation.context

import io.docpilot.core.model.knowledge.KnowledgeResult

/**
 * Converts retrieved knowledge into deterministic, bounded prompt context.
 */
fun interface KnowledgeContextRenderer {

    fun render(
        knowledge: KnowledgeResult,
        policy: KnowledgeContextPolicy,
    ): RenderedKnowledgeContext
}

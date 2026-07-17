package io.docpilot.core.incremental.prompt

import io.docpilot.core.model.knowledge.KnowledgeNodeKind

/** Bounded knowledge-node projection used by a prompt plan. */
data class PromptKnowledge(
    val id: String,
    val name: String,
    val kind: KnowledgeNodeKind,
    val attributes: Map<String, String>,
) {
    init {
        require(id.isNotBlank()) { "Prompt knowledge id must not be blank." }
        require(name.isNotBlank()) { "Prompt knowledge name must not be blank." }
        require(attributes.keys.none(String::isBlank)) {
            "Prompt knowledge attribute keys must not be blank."
        }
    }
}

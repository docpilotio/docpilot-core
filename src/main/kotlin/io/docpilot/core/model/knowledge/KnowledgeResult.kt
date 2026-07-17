package io.docpilot.core.model.knowledge

import io.docpilot.core.model.evidence.Evidence

/**
 * Ordered knowledge nodes with their incident edges and referenced evidence.
 */
data class KnowledgeResult(
    val nodes: List<KnowledgeNode>,
    val edges: List<KnowledgeEdge>,
    val evidence: List<Evidence>,
) {
    init {
        require(nodes.map { it.id }.distinct().size == nodes.size) {
            "Retrieved knowledge node IDs must be unique."
        }
        require(edges.map { it.id }.distinct().size == edges.size) {
            "Retrieved knowledge edge IDs must be unique."
        }
        require(evidence.map { it.id }.distinct().size == evidence.size) {
            "Retrieved evidence IDs must be unique."
        }
    }
}

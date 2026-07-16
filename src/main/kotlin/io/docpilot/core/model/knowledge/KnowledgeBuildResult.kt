package io.docpilot.core.model.knowledge

import io.docpilot.core.model.evidence.EvidenceCollection

/**
 * Combined deterministic output of knowledge construction.
 */
data class KnowledgeBuildResult(
    val graph: KnowledgeGraph,
    val evidence: EvidenceCollection,
) {
    init {
        val evidenceIds =
            evidence.items.mapTo(mutableSetOf()) { it.id.value }

        require(
            graph.nodes
                .flatMap { it.evidenceRefs }
                .all { it in evidenceIds },
        ) {
            "Every knowledge node evidence reference must exist."
        }

        require(
            graph.edges
                .flatMap { it.evidenceRefs }
                .all { it in evidenceIds },
        ) {
            "Every knowledge edge evidence reference must exist."
        }
    }
}

package io.docpilot.core.incremental.knowledge

/**
 * Deterministic knowledge subset affected by project source changes.
 *
 * RFC-0029 identifies affected knowledge. Mapping that knowledge to document
 * sections is intentionally deferred to RFC-0030.
 */
data class IncrementalKnowledgeImpact(
    val changedRelativePaths: List<String>,
    val directlyAffectedNodeIds: List<String>,
    val affectedNodeIds: List<String>,
    val affectedEdgeIds: List<String>,
    val affectedEvidenceIds: List<String>,
) {
    init {
        requireSortedUnique(changedRelativePaths, "changedRelativePaths")
        requireSortedUnique(directlyAffectedNodeIds, "directlyAffectedNodeIds")
        requireSortedUnique(affectedNodeIds, "affectedNodeIds")
        requireSortedUnique(affectedEdgeIds, "affectedEdgeIds")
        requireSortedUnique(affectedEvidenceIds, "affectedEvidenceIds")
        require(affectedNodeIds.containsAll(directlyAffectedNodeIds)) {
            "Affected nodes must contain every directly affected node."
        }
    }

    val hasImpact: Boolean
        get() = changedRelativePaths.isNotEmpty()

    companion object {
        val EMPTY = IncrementalKnowledgeImpact(
            changedRelativePaths = emptyList(),
            directlyAffectedNodeIds = emptyList(),
            affectedNodeIds = emptyList(),
            affectedEdgeIds = emptyList(),
            affectedEvidenceIds = emptyList(),
        )

        private fun requireSortedUnique(values: List<String>, name: String) {
            require(values.none(String::isBlank)) { "$name must not contain blank values." }
            require(values.distinct().size == values.size) { "$name must contain unique values." }
            require(values == values.sorted()) { "$name must be sorted." }
        }
    }
}

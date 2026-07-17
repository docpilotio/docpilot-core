package io.docpilot.core.model.knowledge

/**
 * Deterministic filters for retrieving a bounded knowledge subgraph.
 */
data class KnowledgeQuery(
    val name: String? = null,
    val kind: KnowledgeNodeKind? = null,
    val relativePath: String? = null,
    val limit: Int = DEFAULT_LIMIT,
) {
    init {
        require(name == null || name.isNotBlank()) {
            "Knowledge query name must be null or non-blank."
        }
        require(relativePath == null || relativePath.isNotBlank()) {
            "Knowledge query relativePath must be null or non-blank."
        }
        require(name != null || kind != null || relativePath != null) {
            "Knowledge query must contain at least one filter."
        }
        require(limit > 0) {
            "Knowledge query limit must be greater than zero."
        }
    }

    companion object {
        const val DEFAULT_LIMIT: Int = 20
    }
}

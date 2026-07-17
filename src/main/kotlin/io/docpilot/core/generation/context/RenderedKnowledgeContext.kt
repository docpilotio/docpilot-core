package io.docpilot.core.generation.context

/**
 * Result of deterministic knowledge-context rendering.
 */
data class RenderedKnowledgeContext(
    val content: String,
    val includedNodes: Int,
    val includedEdges: Int,
    val includedEvidence: Int,
    val omittedNodes: Int,
    val omittedEdges: Int,
    val omittedEvidence: Int,
    val characterBudgetReached: Boolean,
) {
    init {
        require(content.isNotBlank()) {
            "Rendered knowledge context must not be blank."
        }
        require(includedNodes >= 0)
        require(includedEdges >= 0)
        require(includedEvidence >= 0)
        require(omittedNodes >= 0)
        require(omittedEdges >= 0)
        require(omittedEvidence >= 0)
    }
}

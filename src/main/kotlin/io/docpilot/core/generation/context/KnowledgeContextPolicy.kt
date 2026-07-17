package io.docpilot.core.generation.context

/**
 * Provider-independent limits applied while converting retrieved knowledge
 * into prompt context.
 */
data class KnowledgeContextPolicy(
    val maxNodes: Int = DEFAULT_MAX_NODES,
    val maxEdges: Int = DEFAULT_MAX_EDGES,
    val maxEvidence: Int = DEFAULT_MAX_EVIDENCE,
    val maxEvidenceSummaryCharacters: Int =
        DEFAULT_MAX_EVIDENCE_SUMMARY_CHARACTERS,
    val maxCharacters: Int = DEFAULT_MAX_CHARACTERS,
) {
    init {
        require(maxNodes > 0) {
            "Knowledge context maxNodes must be greater than zero."
        }
        require(maxEdges > 0) {
            "Knowledge context maxEdges must be greater than zero."
        }
        require(maxEvidence > 0) {
            "Knowledge context maxEvidence must be greater than zero."
        }
        require(maxEvidenceSummaryCharacters > 0) {
            "Knowledge context maxEvidenceSummaryCharacters must be greater than zero."
        }
        require(maxCharacters > 0) {
            "Knowledge context maxCharacters must be greater than zero."
        }
    }

    companion object {
        const val DEFAULT_MAX_NODES: Int = 20
        const val DEFAULT_MAX_EDGES: Int = 40
        const val DEFAULT_MAX_EVIDENCE: Int = 40
        const val DEFAULT_MAX_EVIDENCE_SUMMARY_CHARACTERS: Int = 500
        const val DEFAULT_MAX_CHARACTERS: Int = 24_000
    }
}

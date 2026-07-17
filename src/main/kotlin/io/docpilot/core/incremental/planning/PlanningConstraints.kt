package io.docpilot.core.incremental.planning

/** Provider-independent limits used while planning prompt context. */
data class PlanningConstraints(
    val totalContextTokens: Int = 8_192,
    val minimumContextTokensPerJob: Int = 256,
    val maximumContextTokensPerJob: Int = 2_048,
) {
    init {
        require(totalContextTokens > 0) { "totalContextTokens must be positive." }
        require(minimumContextTokensPerJob > 0) {
            "minimumContextTokensPerJob must be positive."
        }
        require(maximumContextTokensPerJob >= minimumContextTokensPerJob) {
            "maximumContextTokensPerJob must be at least the minimum."
        }
    }
}

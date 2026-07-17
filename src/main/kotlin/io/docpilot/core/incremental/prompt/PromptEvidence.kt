package io.docpilot.core.incremental.prompt

/** Source evidence selected for one generation job. */
data class PromptEvidence(
    val id: String,
    val sourcePath: String,
    val lineStart: Int?,
    val symbol: String?,
    val summary: String,
) {
    init {
        require(id.isNotBlank()) { "Prompt evidence id must not be blank." }
        require(sourcePath.isNotBlank()) { "Prompt evidence sourcePath must not be blank." }
        require(lineStart == null || lineStart > 0) {
            "Prompt evidence lineStart must be positive when present."
        }
        require(symbol == null || symbol.isNotBlank()) {
            "Prompt evidence symbol must be null or non-blank."
        }
        require(summary.isNotBlank()) { "Prompt evidence summary must not be blank." }
    }
}

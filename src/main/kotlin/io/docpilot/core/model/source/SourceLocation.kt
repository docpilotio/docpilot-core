package io.docpilot.core.model.source

/**
 * Source-code location used as evidence for an extracted symbol.
 *
 * Line and column numbers are 1-based when present.
 */
data class SourceLocation(
    val relativePath: String,
    val lineStart: Int? = null,
    val columnStart: Int? = null,
    val lineEnd: Int? = null,
    val columnEnd: Int? = null,
) {
    init {
        require(relativePath.isNotBlank()) {
            "relativePath must not be blank."
        }

        require(lineStart == null || lineStart > 0) {
            "lineStart must be greater than zero when present."
        }

        require(columnStart == null || columnStart > 0) {
            "columnStart must be greater than zero when present."
        }

        require(lineEnd == null || lineEnd > 0) {
            "lineEnd must be greater than zero when present."
        }

        require(columnEnd == null || columnEnd > 0) {
            "columnEnd must be greater than zero when present."
        }

        require(
            lineStart == null ||
                lineEnd == null ||
                lineEnd >= lineStart
        ) {
            "lineEnd must not be before lineStart."
        }
    }
}

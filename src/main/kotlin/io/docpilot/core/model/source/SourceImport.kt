package io.docpilot.core.model.source

/**
 * Import declaration found in a source file.
 */
data class SourceImport(
    val qualifiedName: String,
    val alias: String? = null,
    val wildcard: Boolean = false,
) {
    init {
        require(qualifiedName.isNotBlank()) {
            "qualifiedName must not be blank."
        }

        require(alias == null || alias.isNotBlank()) {
            "alias must be null or non-blank."
        }
    }
}

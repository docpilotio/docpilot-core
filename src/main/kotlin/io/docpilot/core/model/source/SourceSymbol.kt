package io.docpilot.core.model.source

/**
 * One declared symbol extracted from a source file.
 */
data class SourceSymbol(
    val name: String,
    val kind: SourceSymbolKind,
    val visibility: SourceVisibility = SourceVisibility.DEFAULT,
    val location: SourceLocation? = null,
    val annotations: List<String> = emptyList(),
    val children: List<SourceSymbol> = emptyList(),
) {
    init {
        require(name.isNotBlank()) {
            "name must not be blank."
        }
    }
}

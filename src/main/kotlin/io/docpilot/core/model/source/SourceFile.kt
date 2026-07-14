package io.docpilot.core.model.source

/**
 * Language-neutral representation of one source file.
 */
data class SourceFile(
    val relativePath: String,
    val language: SourceLanguage,
    val packageName: String? = null,
    val imports: List<SourceImport> = emptyList(),
    val symbols: List<SourceSymbol> = emptyList(),
) {
    init {
        require(relativePath.isNotBlank()) {
            "relativePath must not be blank."
        }
    }
}

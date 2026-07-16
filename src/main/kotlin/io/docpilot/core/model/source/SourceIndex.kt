package io.docpilot.core.model.source

data class SourceIndex(
    val files: List<SourceFile>,
    val failures: List<SourceIndexFailure> = emptyList(),
) {
    val totalFileCount: Int get() = files.size
    val totalSymbolCount: Int get() = files.sumOf { it.symbols.size }
}

data class SourceIndexFailure(
    val relativePath: String,
    val message: String,
) {
    init {
        require(relativePath.isNotBlank()) { "relativePath must not be blank." }
        require(message.isNotBlank()) { "message must not be blank." }
    }
}

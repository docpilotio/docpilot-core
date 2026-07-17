package io.docpilot.core.model.source

/** One declared symbol extracted from a source file. */
data class SourceSymbol(
    val name: String,
    val kind: SourceSymbolKind,
    val visibility: SourceVisibility = SourceVisibility.DEFAULT,
    val location: SourceLocation? = null,
    val annotations: List<String> = emptyList(),
    val children: List<SourceSymbol> = emptyList(),
    val id: String = "",
    val qualifiedName: String? = null,
    val modifiers: Set<SourceModifier> = emptySet(),
    val signature: String? = null,
    val parentSymbolId: String? = null,
    val parameters: List<SourceParameter> = emptyList(),
    val type: String? = null,
    val receiverType: String? = null,
    val mutable: Boolean? = null,
    val hasInitializer: Boolean? = null,
    val typeParameters: List<String> = emptyList(),
    val superTypes: List<String> = emptyList(),
) {
    init { require(name.isNotBlank()) { "name must not be blank." } }
}

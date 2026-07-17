package io.docpilot.core.model.source

data class SourceParameter(
    val name: String,
    val type: String? = null,
    val hasDefaultValue: Boolean = false,
    val annotations: List<String> = emptyList(),
    val modifiers: Set<SourceModifier> = emptySet(),
    val location: SourceLocation? = null,
) {
    init { require(name.isNotBlank()) { "name must not be blank." } }
}

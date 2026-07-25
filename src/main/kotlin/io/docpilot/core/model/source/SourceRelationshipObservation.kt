package io.docpilot.core.model.source

/** Scanner-proven direct supertype semantics. Ambiguous syntax must not create this model. */
data class SourceSuperTypeReference(
    val qualifiedName: String,
    val kind: SourceSuperTypeKind,
    val location: SourceLocation,
) {
    init {
        require(qualifiedName.isNotBlank()) { "Supertype qualifiedName must not be blank." }
    }
}

enum class SourceSuperTypeKind { EXTENDS, IMPLEMENTS }

/** One statically observed direct call site. */
data class SourceCall(
    val targetQualifiedName: String,
    val location: SourceLocation,
    val targetSignature: String? = null,
) {
    init {
        require(targetQualifiedName.isNotBlank()) { "Call target qualifiedName must not be blank." }
    }
}

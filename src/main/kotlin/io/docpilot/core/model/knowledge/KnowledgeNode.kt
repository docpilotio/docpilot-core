package io.docpilot.core.model.knowledge

data class KnowledgeNode(
    val id: String,
    val name: String,
    val kind: KnowledgeNodeKind,
    val attributes: Map<String, String> = emptyMap(),
    val evidenceRefs: Set<String> = emptySet(),
    val confidence: Double = 1.0,
) {
    init {
        require(id.isNotBlank()) { "Knowledge node ID must not be blank." }
        require(name.isNotBlank()) { "Knowledge node name must not be blank." }
        require(attributes.keys.none(String::isBlank)) {
            "Knowledge node attribute keys must not be blank."
        }
        require(evidenceRefs.none(String::isBlank)) {
            "Knowledge node evidence references must not be blank."
        }
        require(confidence in 0.0..1.0) {
            "Knowledge node confidence must be between 0.0 and 1.0."
        }
    }
}

enum class KnowledgeNodeKind {
    PROJECT,
    MODULE,
    PACKAGE,
    FILE,
    CLASS,
    INTERFACE,
    OBJECT,
    ENUM_CLASS,
    ANNOTATION_CLASS,
    FUNCTION,
    PROPERTY,
    CONSTRUCTOR,
    ENUM_ENTRY,
    TYPE_ALIAS,
    EXTERNAL_TYPE,
    UNKNOWN,
}

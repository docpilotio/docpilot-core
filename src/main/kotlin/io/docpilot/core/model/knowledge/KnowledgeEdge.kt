package io.docpilot.core.model.knowledge

data class KnowledgeEdge(
    val id: String,
    val sourceNodeId: String,
    val targetNodeId: String,
    val relationship: RelationshipType,
    val attributes: Map<String, String> = emptyMap(),
    val evidenceRefs: Set<String> = emptySet(),
    val confidence: Double = 1.0,
) {
    init {
        require(id.isNotBlank()) { "Knowledge edge ID must not be blank." }
        require(sourceNodeId.isNotBlank()) { "sourceNodeId must not be blank." }
        require(targetNodeId.isNotBlank()) { "targetNodeId must not be blank." }
        require(attributes.keys.none(String::isBlank)) {
            "Knowledge edge attribute keys must not be blank."
        }
        require(evidenceRefs.none(String::isBlank)) {
            "Knowledge edge evidence references must not be blank."
        }
        require(confidence in 0.0..1.0) {
            "Knowledge edge confidence must be between 0.0 and 1.0."
        }
    }
}

enum class RelationshipType {
    CONTAINS,
    DECLARES,
    IMPORTS,
    DEPENDS_ON,
    EXTENDS,
    IMPLEMENTS,
    CALLS,
    RETURNS,
    USES,
    REFERENCES,
    UNKNOWN,
}

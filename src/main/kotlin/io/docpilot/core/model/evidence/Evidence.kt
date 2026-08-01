package io.docpilot.core.model.evidence

/**
 * Deterministic source evidence supporting a knowledge fact.
 */
data class Evidence(
    val id: EvidenceId,
    val type: EvidenceType,
    val location: EvidenceLocation,
    val summary: String,
    val attributes: Map<String, String> = emptyMap(),
) {
    init {
        require(summary.isNotBlank()) {
            "Evidence summary must not be blank."
        }
        require(attributes.keys.none(String::isBlank)) {
            "Evidence attribute keys must not be blank."
        }
    }
}

@JvmInline
value class EvidenceId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) {
            "Evidence ID must not be blank."
        }
    }

    override fun toString(): String = value
}

enum class EvidenceType {
    SOURCE_FILE,
    PACKAGE_DECLARATION,
    IMPORT_DECLARATION,
    SYMBOL_DECLARATION,
    RELATIONSHIP,
    COMPOSE_ROUTE_DECLARATION,
    COMPOSE_NAVIGATION_REGISTRATION,
    COMPOSE_FUNCTION_REFERENCE,
    COMPOSE_NAVIGATION_GRAPH,
    COMPOSE_NAVIGATION_ARGUMENT,
    COMPOSE_NAVIGATION_ARGUMENT_LINK,
    UNKNOWN,
}

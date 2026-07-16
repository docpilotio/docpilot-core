package io.docpilot.core.model.evidence

/**
 * Ordered evidence collection with unique identifiers.
 */
data class EvidenceCollection(
    val items: List<Evidence>,
) {
    init {
        require(
            items.map { it.id }.distinct().size == items.size,
        ) {
            "Evidence IDs must be unique."
        }
    }

    val size: Int
        get() = items.size

    fun find(id: EvidenceId): Evidence? =
        items.firstOrNull { it.id == id }
}

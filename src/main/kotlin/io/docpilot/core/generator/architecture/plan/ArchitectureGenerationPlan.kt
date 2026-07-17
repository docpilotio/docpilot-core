package io.docpilot.core.generator.architecture.plan

/**
 * Deterministic ordered plan for multi-step architecture generation.
 */
data class ArchitectureGenerationPlan(
    val sections: List<ArchitectureSection>,
) {
    init {
        require(sections.isNotEmpty()) {
            "Architecture generation plan must contain at least one section."
        }

        val ids = sections.map { it.id }
        require(ids.distinct().size == ids.size) {
            "Architecture generation plan section ids must be unique."
        }

        val orders = sections.map { it.order }
        require(orders.distinct().size == orders.size) {
            "Architecture generation plan section orders must be unique."
        }

        require(sections == sections.sortedBy { it.order }) {
            "Architecture generation plan sections must be sorted by order."
        }
    }

    val totalMaxOutputTokens: Int =
        sections.sumOf { it.maxOutputTokens }
}

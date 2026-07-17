package io.docpilot.core.incremental.planning

import io.docpilot.core.generator.architecture.plan.ArchitectureSection
import io.docpilot.core.generator.architecture.plan.ArchitectureSectionId

/** One provider-independent unit of incremental documentation work. */
data class GenerationJob(
    val section: ArchitectureSection,
    val priority: GenerationPriority,
    val reasons: Set<GenerationReason>,
    val affectedNodeIds: List<String>,
    val affectedEvidenceIds: List<String>,
    val dependencies: List<ArchitectureSectionId>,
    val contextTokenBudget: Int,
) {
    init {
        require(reasons.isNotEmpty()) { "Generation job must have at least one reason." }
        require(affectedNodeIds == affectedNodeIds.distinct().sorted()) {
            "affectedNodeIds must be sorted and unique."
        }
        require(affectedEvidenceIds == affectedEvidenceIds.distinct().sorted()) {
            "affectedEvidenceIds must be sorted and unique."
        }
        require(dependencies.distinct().size == dependencies.size) {
            "Generation job dependencies must be unique."
        }
        require(section.id !in dependencies) {
            "Generation job must not depend on itself."
        }
        require(contextTokenBudget > 0) { "contextTokenBudget must be positive." }
    }
}

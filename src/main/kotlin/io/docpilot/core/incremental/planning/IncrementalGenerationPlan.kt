package io.docpilot.core.incremental.planning

import io.docpilot.core.generator.architecture.plan.ArchitectureSectionId

/** Deterministic, dependency-safe execution plan. */
data class IncrementalGenerationPlan(
    val jobs: List<GenerationJob>,
    val totalContextTokenBudget: Int,
) {
    init {
        val ids = jobs.map { it.section.id }
        require(ids.distinct().size == ids.size) { "Generation job section ids must be unique." }
        require(totalContextTokenBudget >= 0) { "totalContextTokenBudget must not be negative." }
        require(jobs.sumOf { it.contextTokenBudget } <= totalContextTokenBudget) {
            "Generation jobs exceed the total context token budget."
        }

        val positions = ids.withIndex().associate { it.value to it.index }
        jobs.forEachIndexed { index, job ->
            require(job.dependencies.all { dependency ->
                val dependencyPosition = positions[dependency]
                dependencyPosition != null && dependencyPosition < index
            }) {
                "Every dependency must appear before its dependent job."
            }
        }
    }

    val isEmpty: Boolean get() = jobs.isEmpty()
    val sectionIds: List<ArchitectureSectionId> get() = jobs.map { it.section.id }

    companion object {
        val EMPTY = IncrementalGenerationPlan(emptyList(), 0)
    }
}

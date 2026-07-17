package io.docpilot.core.generator.architecture.plan

import io.docpilot.core.generator.architecture.ArchitectureGenerationRequest

/**
 * Produces a deterministic sequence of focused architecture sections.
 */
fun interface ArchitectureGenerationPlanner {

    fun plan(
        request: ArchitectureGenerationRequest,
    ): ArchitectureGenerationPlan
}

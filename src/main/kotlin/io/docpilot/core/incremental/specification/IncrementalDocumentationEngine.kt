package io.docpilot.core.incremental.specification

import io.docpilot.core.model.ProjectSpecification

public data class IncrementalDocumentationResult(
    public val diff: SpecificationDiff,
    public val plan: IncrementalUpdatePlan,
)

/** Orchestrates DIR comparison and planning without changing Builder or Renderer responsibilities. */
public class IncrementalDocumentationEngine(
    private val differ: SpecificationDiffer = DefaultSpecificationDiffer(),
    private val planner: IncrementalSpecificationPlanner = DefaultIncrementalSpecificationPlanner(),
) {
    public fun analyze(
        previous: ProjectSpecification,
        current: ProjectSpecification,
    ): IncrementalDocumentationResult {
        val diff = differ.diff(previous, current)
        return IncrementalDocumentationResult(diff, planner.plan(diff, previous, current))
    }
}

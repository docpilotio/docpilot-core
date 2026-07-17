package io.docpilot.core.incremental.planning

import io.docpilot.core.generator.architecture.plan.ArchitectureGenerationPlan
import io.docpilot.core.incremental.ProjectChangeSet
import io.docpilot.core.incremental.knowledge.IncrementalKnowledgeImpact

/** Converts source and knowledge impact into executable generation jobs. */
fun interface IncrementalGenerationPlanner {
    fun plan(
        architecturePlan: ArchitectureGenerationPlan,
        changes: ProjectChangeSet,
        impact: IncrementalKnowledgeImpact,
        constraints: PlanningConstraints,
    ): IncrementalGenerationPlan
}

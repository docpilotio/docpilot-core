package io.docpilot.core.incremental.specification

import io.docpilot.core.model.ProjectSpecification

public fun interface IncrementalSpecificationPlanner {
    public fun plan(diff: SpecificationDiff, previous: ProjectSpecification, current: ProjectSpecification): IncrementalUpdatePlan
}

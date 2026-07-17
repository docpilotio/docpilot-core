package io.docpilot.core.incremental.specification

import io.docpilot.core.model.ProjectSpecification

/** Compares two ProjectSpecification snapshots without invoking builders or renderers. */
public fun interface SpecificationDiffer {
    public fun diff(previous: ProjectSpecification, current: ProjectSpecification): SpecificationDiff
}

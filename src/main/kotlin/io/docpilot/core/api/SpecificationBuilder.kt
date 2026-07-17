package io.docpilot.core.api

import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.specification.SpecificationBuildRequest

public fun interface SpecificationBuilder {
    public fun build(request: SpecificationBuildRequest): ProjectSpecification
}

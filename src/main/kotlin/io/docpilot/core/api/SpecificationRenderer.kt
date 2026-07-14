package io.docpilot.core.api

import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.RenderedArtifact

/**
 * Converts an approved project specification into one or more artifacts.
 */
public fun interface SpecificationRenderer {
    public fun render(specification: ProjectSpecification): List<RenderedArtifact>
}

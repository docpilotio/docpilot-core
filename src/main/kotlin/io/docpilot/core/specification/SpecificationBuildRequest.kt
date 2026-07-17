package io.docpilot.core.specification

import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.knowledge.KnowledgeBuildResult
import io.docpilot.core.model.source.SourceIndex

public data class SpecificationBuildRequest(
    public val project: ProjectDescriptor,
    public val knowledge: KnowledgeBuildResult,
    public val sourceIndex: SourceIndex? = null,
)

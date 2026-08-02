package io.docpilot.core.api

import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.RenderedArtifact

@JvmInline
public value class DocumentationArtifactId(public val value: String) {
    init {
        require(value.isNotBlank()) { "Documentation artifact id must not be blank" }
    }
}

public enum class DocumentationArtifactKind {
    PROJECT_OVERVIEW,
    MODULE,
    PACKAGE,
    COMPONENT,
    RELATIONSHIP,
    EVIDENCE,
    ARCHITECTURE_OVERVIEW,
    FEATURE_CATALOG,
    FEATURE_DETAIL,
    CONTRACT_CATALOG,
    CONTRACT_DETAIL,
    MODULE_ARCHITECTURE,
    DOMAIN_MODEL,
    DATABASE_SCHEMA,
    EXTERNAL_API_CONTRACT,
    TEST_STRATEGY,
    INDEX,
}

public data class DocumentationArtifactDescriptor(
    public val artifactId: DocumentationArtifactId,
    public val relativePath: String,
    public val mediaType: String,
    public val kind: DocumentationArtifactKind,
    public val scopeIds: List<String>,
    public val dependencyArtifactIds: List<DocumentationArtifactId> = emptyList(),
)

/**
 * Renderer contract which separates cheap artifact discovery from content rendering.
 */
public interface SelectiveSpecificationRenderer : SpecificationRenderer {
    public fun describe(specification: ProjectSpecification): List<DocumentationArtifactDescriptor>

    public fun render(
        specification: ProjectSpecification,
        artifactIds: Set<DocumentationArtifactId>,
    ): List<RenderedArtifact>

    override fun render(specification: ProjectSpecification): List<RenderedArtifact> =
        render(specification, describe(specification).mapTo(linkedSetOf()) { it.artifactId })
}

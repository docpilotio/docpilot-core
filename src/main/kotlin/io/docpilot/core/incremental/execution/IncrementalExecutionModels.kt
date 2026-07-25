package io.docpilot.core.incremental.execution

import io.docpilot.core.incremental.specification.IncrementalUpdatePlan
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.RenderedArtifact

/** High-level outcome of an incremental documentation execution. */
public enum class IncrementalExecutionMode {
    NO_CHANGES,
    INCREMENTAL_UPDATE,
    FULL_REGENERATION,
    FAILED,
}

/** Concrete output operation selected for a documentation artifact. */
public enum class DocumentationArtifactOperation {
    CREATE,
    UPDATE,
    DELETE,
    KEEP,
}

public data class ExistingDocumentationArtifact(
    public val relativePath: String,
    public val mediaType: String,
    public val content: String,
    public val ownership: DocumentationArtifactOwnership = DocumentationArtifactOwnership.DOCPILOT,
)

public data class DocumentationArtifactAction(
    public val relativePath: String,
    public val operation: DocumentationArtifactOperation,
    public val mediaType: String? = null,
)

public enum class IncrementalFallbackReason {
    PREVIOUS_SPECIFICATION_MISSING,
    SCHEMA_VERSION_MISMATCH,
    EXISTING_DOCUMENTATION_MISSING,
}

public data class IncrementalDocumentationExecutionRequest(
    public val previousSpecification: ProjectSpecification?,
    public val currentSpecification: ProjectSpecification,
    public val updatePlan: IncrementalUpdatePlan,
    public val existingArtifacts: List<ExistingDocumentationArtifact> = emptyList(),
)

public data class IncrementalDocumentationExecutionResult(
    public val mode: IncrementalExecutionMode,
    public val artifactActions: List<DocumentationArtifactAction> = emptyList(),
    public val renderedArtifacts: List<RenderedArtifact> = emptyList(),
    public val fallbackReason: IncrementalFallbackReason? = null,
    public val warnings: List<String> = emptyList(),
    public val artifactPlanSha256: String? = null,
    public val orphanedArtifacts: List<OrphanedDocumentationArtifact> = emptyList(),
    public val errorMessage: String? = null,
    public val writePerformed: Boolean = false,
) {
    public val createdArtifacts: List<String>
        get() = pathsFor(DocumentationArtifactOperation.CREATE)

    public val updatedArtifacts: List<String>
        get() = pathsFor(DocumentationArtifactOperation.UPDATE)

    public val deletedArtifacts: List<String>
        get() = pathsFor(DocumentationArtifactOperation.DELETE)

    public val keptArtifacts: List<String>
        get() = pathsFor(DocumentationArtifactOperation.KEEP)

    private fun pathsFor(operation: DocumentationArtifactOperation): List<String> =
        artifactActions.filter { it.operation == operation }.map { it.relativePath }
}

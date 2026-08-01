package io.docpilot.cli.command

import io.docpilot.core.api.DocumentationArtifactId
import io.docpilot.core.incremental.execution.DocumentationArtifactOperation

internal enum class DocumentationGenerationMode { PREVIEW, APPLY }
internal enum class DocumentationGenerationStatus { PREVIEW_READY, APPLIED, NO_CHANGES, BLOCKED, CONFLICT, FAILED }

internal data class DocumentationGenerationOptions(
    val projectRoot: java.nio.file.Path,
    val outputRoot: java.nio.file.Path,
    val profile: String,
    val mode: DocumentationGenerationMode,
    val full: Boolean,
    val artifactIds: Set<String>,
    val documentTypes: Set<String>,
    val expectedPlanSha256: String?,
    val json: Boolean,
)

internal data class DocumentationArtifactResult(
    val artifactId: DocumentationArtifactId,
    val documentType: String,
    val relativePath: String,
    val operation: DocumentationArtifactOperation,
    val contentSha256: String,
    val reason: String,
)

internal data class DocumentationGenerationResult(
    val status: DocumentationGenerationStatus,
    val mode: DocumentationGenerationMode,
    val projectId: String?,
    val outputRoot: String,
    val profile: String,
    val snapshotStatus: String,
    val planSha256: String?,
    val artifacts: List<DocumentationArtifactResult> = emptyList(),
    val diagnostics: List<String> = emptyList(),
    val snapshotWritten: Boolean = false,
) {
    val exitCode: Int get() = when (status) {
        DocumentationGenerationStatus.PREVIEW_READY,
        DocumentationGenerationStatus.APPLIED,
        DocumentationGenerationStatus.NO_CHANGES -> 0
        DocumentationGenerationStatus.BLOCKED -> 6
        DocumentationGenerationStatus.CONFLICT -> 5
        DocumentationGenerationStatus.FAILED -> 8
    }
}

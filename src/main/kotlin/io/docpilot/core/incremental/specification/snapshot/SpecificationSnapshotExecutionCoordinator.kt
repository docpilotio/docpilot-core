package io.docpilot.core.incremental.specification.snapshot

import io.docpilot.core.incremental.execution.ExistingDocumentationArtifact
import io.docpilot.core.incremental.execution.IncrementalDocumentationExecutionRequest
import io.docpilot.core.incremental.execution.IncrementalDocumentationExecutionResult
import io.docpilot.core.incremental.execution.IncrementalDocumentationExecutor
import io.docpilot.core.incremental.execution.IncrementalExecutionMode
import io.docpilot.core.incremental.specification.IncrementalDocumentationEngine
import io.docpilot.core.incremental.specification.IncrementalUpdatePlan
import io.docpilot.core.model.ProjectSpecification

public enum class SnapshotExecutionFailureStage { SNAPSHOT_LOAD, DOCUMENTATION_EXECUTION, SNAPSHOT_SAVE }

public data class SpecificationSnapshotExecutionResult(
    public val execution: IncrementalDocumentationExecutionResult,
    public val snapshotLoadResult: SpecificationSnapshotLoadResult,
    public val snapshotSaved: Boolean,
    public val failureStage: SnapshotExecutionFailureStage? = null,
    public val errorMessage: String? = null,
)

public class SpecificationSnapshotExecutionCoordinator(
    private val repository: SpecificationSnapshotRepository,
    private val engine: IncrementalDocumentationEngine,
    private val executor: IncrementalDocumentationExecutor,
) {
    public fun execute(
        currentSpecification: ProjectSpecification,
        existingArtifacts: List<ExistingDocumentationArtifact> = emptyList(),
    ): SpecificationSnapshotExecutionResult {
        val load = repository.load(currentSpecification.project.id)
        if (load is SpecificationSnapshotLoadResult.Invalid &&
            load.reason == SnapshotValidationFailure.UNSUPPORTED_VERSION
        ) {
            return SpecificationSnapshotExecutionResult(
                execution = IncrementalDocumentationExecutionResult(
                    mode = IncrementalExecutionMode.FAILED,
                    errorMessage = load.message,
                ),
                snapshotLoadResult = load,
                snapshotSaved = false,
                failureStage = SnapshotExecutionFailureStage.SNAPSHOT_LOAD,
                errorMessage = load.message,
            )
        }
        val previous = (load as? SpecificationSnapshotLoadResult.Valid)?.snapshot?.specification
        val plan = if (previous == null || previous.schemaVersion != currentSpecification.schemaVersion) {
            IncrementalUpdatePlan.EMPTY
        } else {
            engine.analyze(previous, currentSpecification).plan
        }

        val execution = executor.execute(
            IncrementalDocumentationExecutionRequest(
                previousSpecification = previous,
                currentSpecification = currentSpecification,
                updatePlan = plan,
                existingArtifacts = existingArtifacts,
            ),
        )
        if (execution.mode == IncrementalExecutionMode.FAILED) {
            return SpecificationSnapshotExecutionResult(
                execution = execution,
                snapshotLoadResult = load,
                snapshotSaved = false,
                failureStage = SnapshotExecutionFailureStage.DOCUMENTATION_EXECUTION,
                errorMessage = execution.errorMessage,
            )
        }
        if (execution.mode == IncrementalExecutionMode.NO_CHANGES && load is SpecificationSnapshotLoadResult.Valid) {
            return SpecificationSnapshotExecutionResult(execution, load, snapshotSaved = false)
        }
        return try {
            repository.save(currentSpecification)
            SpecificationSnapshotExecutionResult(execution, load, snapshotSaved = true)
        } catch (e: Exception) {
            SpecificationSnapshotExecutionResult(
                execution = execution.copy(
                    mode = IncrementalExecutionMode.FAILED,
                    errorMessage = e.message ?: "Specification snapshot save failed",
                ),
                snapshotLoadResult = load,
                snapshotSaved = false,
                failureStage = SnapshotExecutionFailureStage.SNAPSHOT_SAVE,
                errorMessage = e.message ?: "Specification snapshot save failed",
            )
        }
    }
}

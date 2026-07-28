package io.docpilot.core.evolution

import io.docpilot.core.incremental.execution.DocumentationArtifactPlanVerifier
import io.docpilot.core.incremental.specification.snapshot.JsonSpecificationSnapshotCodec
import io.docpilot.core.incremental.specification.snapshot.SpecificationSnapshotFormat
import io.docpilot.core.incremental.specification.snapshot.SpecificationSnapshotLoadResult
import io.docpilot.core.reconciliation.ReconciliationVerifier
import io.docpilot.core.specification.RelationshipProjectionVerifier

internal data class EvolutionInputValidation(
    val blockedState: EvolutionCoverageState?,
    val findings: List<EvolutionCoverageFinding>,
)

internal class EvolutionInputValidator {
    fun validate(request: DocumentationEvolutionRequest): EvolutionInputValidation {
        val findings = mutableListOf<EvolutionCoverageFinding>()

        if (request.beforeSnapshot.snapshotFormatVersion != SpecificationSnapshotFormat.CURRENT_VERSION ||
            request.afterSnapshot.snapshotFormatVersion != SpecificationSnapshotFormat.CURRENT_VERSION ||
            request.beforeSnapshot.dirSchemaVersion != request.afterSnapshot.dirSchemaVersion ||
            request.beforeSnapshot.projectIdentity.projectId != request.afterSnapshot.projectIdentity.projectId ||
            request.beforeSnapshot.specification.project.id != request.afterSnapshot.specification.project.id
        ) {
            findings += EvolutionCoverageFinding(
                EvolutionCoverageFindingKind.INCOMPATIBLE_SNAPSHOT,
                request.afterSnapshot.projectIdentity.projectId,
                "Before and after snapshots are not format, schema, and project compatible.",
                required = true,
            )
            return EvolutionInputValidation(EvolutionCoverageState.BLOCKED_INCOMPATIBLE_FORMAT, findings)
        }

        listOf("before" to request.beforeSnapshot, "after" to request.afterSnapshot).forEach { (label, snapshot) ->
            if (!verifySnapshot(snapshot)) {
                findings += EvolutionCoverageFinding(
                    EvolutionCoverageFindingKind.INVALID_SNAPSHOT_INTEGRITY,
                    label,
                    "The $label specification snapshot failed canonical integrity verification.",
                    required = true,
                )
            }
        }
        if (findings.isNotEmpty()) {
            return EvolutionInputValidation(EvolutionCoverageState.BLOCKED_INTEGRITY_FAILURE, findings)
        }

        val planValid = DocumentationArtifactPlanVerifier().verify(
            request.artifactPlan,
            request.afterSnapshot.specification,
            request.beforeCatalog,
            request.afterCatalog,
            request.existingArtifacts,
        )
        if (!planValid) {
            findings += EvolutionCoverageFinding(
                EvolutionCoverageFindingKind.INVALID_ARTIFACT_PLAN,
                request.artifactPlan.planSha256,
                "The RFC-0052 artifact plan or its catalog/inventory Evidence failed integrity verification.",
                required = true,
            )
        }

        val relationshipVerifier = RelationshipProjectionVerifier()
        listOf(
            "before" to request.beforeRelationshipReport,
            "after" to request.afterRelationshipReport,
        ).forEach { (label, report) ->
            if (report != null && !relationshipVerifier.verify(report)) {
                findings += EvolutionCoverageFinding(
                    EvolutionCoverageFindingKind.INVALID_RELATIONSHIP_REPORT,
                    label,
                    "The $label RFC-0053 relationship projection report failed integrity verification.",
                    required = true,
                )
            }
        }

        val duplicateManifestIds = listOf(
            "before" to request.beforeOwnershipManifests,
            "after" to request.afterOwnershipManifests,
        ).filter { (_, manifests) -> manifests.map { it.artifactId }.distinct().size != manifests.size }
        duplicateManifestIds.forEach { (label, _) ->
            findings += EvolutionCoverageFinding(
                EvolutionCoverageFindingKind.INVALID_OWNERSHIP_MANIFEST,
                label,
                "Duplicate ownership manifest artifact identity.",
                required = true,
            )
        }
        listOf("before" to request.beforeArtifactStates, "after" to request.afterArtifactStates).forEach { (label, states) ->
            if (states.map { it.artifactId }.distinct().size != states.size ||
                states.map { it.relativePath }.distinct().size != states.size
            ) {
                findings += EvolutionCoverageFinding(
                    EvolutionCoverageFindingKind.INVALID_ARTIFACT_PLAN,
                    label,
                    "Duplicate artifact-state identity or path.",
                    required = true,
                )
            }
        }

        val reconciliationVerifier = ReconciliationVerifier()
        if (request.reconciliationPlan != null && !reconciliationVerifier.verify(request.reconciliationPlan)) {
            findings += EvolutionCoverageFinding(
                EvolutionCoverageFindingKind.INVALID_RECONCILIATION_EVIDENCE,
                request.reconciliationPlan.planSha256,
                "The RFC-0055 reconciliation plan failed integrity verification.",
                required = true,
            )
        }
        if (request.reconciliationResult != null) {
            val operationIds = request.reconciliationPlan?.operations.orEmpty().mapTo(hashSetOf()) { it.operationId }
            val resultOperationIds = request.reconciliationResult.appliedOperationIds +
                request.reconciliationResult.retainedOperationIds
            if (!reconciliationVerifier.verify(request.reconciliationResult) ||
                request.reconciliationPlan == null ||
                request.reconciliationResult.planSha256 != request.reconciliationPlan.planSha256 ||
                resultOperationIds.any { it !in operationIds }
            ) {
                findings += EvolutionCoverageFinding(
                    EvolutionCoverageFindingKind.INVALID_RECONCILIATION_EVIDENCE,
                    request.reconciliationResult.planSha256,
                    "The RFC-0055 reconciliation result is invalid or is not bound to the supplied plan.",
                    required = true,
                )
            }
        }
        (request.beforeOwnershipManifests + request.afterOwnershipManifests).forEach { manifest ->
            if (!reconciliationVerifier.verify(manifest)) {
                findings += EvolutionCoverageFinding(
                    EvolutionCoverageFindingKind.INVALID_OWNERSHIP_MANIFEST,
                    manifest.artifactId.value,
                    "Ownership manifest failed integrity or safe-path verification.",
                    required = true,
                )
            }
        }

        return if (findings.isEmpty()) {
            EvolutionInputValidation(null, emptyList())
        } else {
            EvolutionInputValidation(EvolutionCoverageState.BLOCKED_INTEGRITY_FAILURE, findings)
        }
    }

    private fun verifySnapshot(snapshot: io.docpilot.core.incremental.specification.snapshot.StoredSpecificationSnapshot): Boolean {
        val codec = JsonSpecificationSnapshotCodec()
        val encoded = codec.encode(snapshot.specification)
        val decoded = codec.decode(encoded, snapshot.projectIdentity.projectId)
        return decoded is SpecificationSnapshotLoadResult.Valid &&
            decoded.snapshot.snapshotFormatVersion == snapshot.snapshotFormatVersion &&
            decoded.snapshot.dirSchemaVersion == snapshot.dirSchemaVersion &&
            decoded.snapshot.projectIdentity == snapshot.projectIdentity &&
            decoded.snapshot.integrity == snapshot.integrity &&
            snapshot.specification.schemaVersion == snapshot.dirSchemaVersion &&
            snapshot.specification.project.id == snapshot.projectIdentity.projectId
    }
}

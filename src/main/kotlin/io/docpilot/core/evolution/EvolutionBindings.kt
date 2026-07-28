package io.docpilot.core.evolution

import io.docpilot.core.api.DocumentationArtifactId
import io.docpilot.core.incremental.execution.DocumentationArtifactOperation
import io.docpilot.core.reconciliation.DocumentationOwnershipManifest
import io.docpilot.core.reconciliation.ReconciliationOperationKind

internal data class EvolutionBindingResult(
    val changes: List<DocumentationEvolutionChange>,
    val impacts: List<EvolutionArtifactImpact>,
)

internal class EvolutionBindingEngine {
    fun bind(
        request: DocumentationEvolutionRequest,
        specificationChanges: List<DocumentationEvolutionChange>,
    ): EvolutionBindingResult {
        val changes = specificationChanges.toMutableList()
        val impacts = bindArtifactImpacts(request, specificationChanges, changes)
        changes += ownershipChanges(request.beforeOwnershipManifests, request.afterOwnershipManifests)
        changes += reconciliationChanges(request)
        return EvolutionBindingResult(
            changes = changes.distinctBy { it.changeId }.sortedBy { it.changeId },
            impacts = impacts.sortedBy { it.artifactId },
        )
    }

    private fun bindArtifactImpacts(
        request: DocumentationEvolutionRequest,
        specificationChanges: List<DocumentationEvolutionChange>,
        allChanges: MutableList<DocumentationEvolutionChange>,
    ): List<EvolutionArtifactImpact> {
        val changesBySubject = specificationChanges.groupBy { it.subjectId }
        val beforeStateById = request.beforeArtifactStates.associateBy { it.artifactId.value }
        val afterStateById = request.afterArtifactStates.associateBy { it.artifactId.value }
        val existingByPath = request.existingArtifacts.associateBy { it.relativePath }
        val reconciliationAfter = request.reconciliationResult?.afterDocumentShaByPath.orEmpty()
        val retainedIds = request.reconciliationResult?.retainedOperationIds.orEmpty().toSet()
        val operationById = request.reconciliationPlan?.operations.orEmpty().associateBy { it.operationId }
        val retainedArtifacts = retainedIds.mapNotNull { operationById[it]?.artifactId?.value }.toSet()

        return request.artifactPlan.actions.mapNotNull { action ->
            val retained = action.artifactId.value in retainedArtifacts
            if (action.operation == DocumentationArtifactOperation.KEEP && !retained) return@mapNotNull null
            val causal = action.sourceChangeIds.flatMap { changesBySubject[it].orEmpty() }
                .map { it.changeId }.distinct().sorted()
            val beforeHash = beforeStateById[action.artifactId.value]?.contentSha256
                ?: existingByPath[action.relativePath]?.content?.let(EvolutionCanonicalizer::sha256)
            val afterHash = if (retained) {
                reconciliationAfter[action.relativePath] ?: afterStateById[action.artifactId.value]?.contentSha256
            } else {
                afterStateById[action.artifactId.value]?.contentSha256 ?: reconciliationAfter[action.relativePath]
            }
            val effectiveOperation = if (retained) DocumentationArtifactOperation.KEEP else action.operation
            val impact = EvolutionArtifactImpact(
                artifactId = action.artifactId.value,
                relativePath = action.relativePath,
                operation = effectiveOperation,
                selectionReasons = action.reasons.map { it.name }.distinct().sorted(),
                sourceChangeIds = action.sourceChangeIds.distinct().sorted(),
                causalChangeIds = causal,
                beforeArtifactSha256 = beforeHash,
                afterArtifactSha256 = afterHash,
            )
            val changeKind = when {
                retained -> EvolutionChangeKind.ARTIFACT_RETAINED
                action.operation == DocumentationArtifactOperation.CREATE -> EvolutionChangeKind.ARTIFACT_CREATED
                action.operation == DocumentationArtifactOperation.UPDATE -> EvolutionChangeKind.ARTIFACT_UPDATED
                else -> return@mapNotNull impact
            }
            val fields = buildList {
                add("operation:${effectiveOperation.name}")
                addAll(action.reasons.map { "reason:${it.name}" })
            }.sorted()
            val artifactChange = derivedChange(
                subjectId = action.artifactId.value,
                subjectKind = EvolutionSubjectKind.ARTIFACT,
                kind = changeKind,
                beforeSha = beforeHash,
                afterSha = afterHash,
                changedFields = fields,
                evidenceRefs = causal.flatMap { id -> specificationChanges.singleOrNull { it.changeId == id }?.evidenceRefs.orEmpty() },
                predecessors = causal,
                affectedArtifacts = listOf(action.artifactId.value),
                confidence = if (retained) EvolutionConfidenceClass.USER_AUTHORIZED else EvolutionConfidenceClass.CORE_DERIVED,
            )
            allChanges += artifactChange
            impact
        }
    }

    private fun ownershipChanges(
        before: List<DocumentationOwnershipManifest>,
        after: List<DocumentationOwnershipManifest>,
    ): List<DocumentationEvolutionChange> {
        val old = before.associateBy { it.artifactId.value }
        val current = after.associateBy { it.artifactId.value }
        return (old.keys + current.keys).distinct().sorted().mapNotNull { artifactId ->
            val previous = old[artifactId]
            val next = current[artifactId]
            if (previous?.manifestSha256 == next?.manifestSha256) return@mapNotNull null
            derivedChange(
                subjectId = artifactId,
                subjectKind = EvolutionSubjectKind.OWNERSHIP,
                kind = EvolutionChangeKind.OWNERSHIP_CHANGED,
                beforeSha = previous?.manifestSha256,
                afterSha = next?.manifestSha256,
                changedFields = listOf(
                    "ownership:${previous?.ownership?.name.orEmpty()}->${next?.ownership?.name.orEmpty()}",
                ),
                evidenceRefs = (previous?.evidenceRefs.orEmpty() + next?.evidenceRefs.orEmpty()),
                affectedArtifacts = listOf(artifactId),
                confidence = EvolutionConfidenceClass.OBSERVED,
            )
        }
    }

    private fun reconciliationChanges(request: DocumentationEvolutionRequest): List<DocumentationEvolutionChange> {
        val plan = request.reconciliationPlan ?: return emptyList()
        val result = request.reconciliationResult
        val changes = mutableListOf<DocumentationEvolutionChange>()
        plan.conflicts.sortedBy { it.conflictId }.forEach { conflict ->
            changes += derivedChange(
                subjectId = conflict.conflictId,
                subjectKind = EvolutionSubjectKind.RECONCILIATION_OPERATION,
                kind = EvolutionChangeKind.RECONCILIATION_CONFLICTED,
                beforeSha = null,
                afterSha = null,
                changedFields = listOf("conflict:${conflict.kind.name}"),
                evidenceRefs = conflict.evidenceRefs,
                affectedArtifacts = listOf(conflict.artifactId.value),
                confidence = EvolutionConfidenceClass.CORE_DERIVED,
            )
        }
        if (result == null) return changes
        val applied = result.appliedOperationIds.toSet()
        val retained = result.retainedOperationIds.toSet()
        val explanationByOperation = plan.explanationReport.explanations
            .flatMap { explanation -> explanation.causedOperationIds.map { it to explanation } }
            .toMap()
        plan.operations.sortedBy { it.operationId }.forEach { operation ->
            val explanation = explanationByOperation[operation.operationId]
            val userAuthorized = operation.requiresDecision ||
                operation.kind == ReconciliationOperationKind.KEEP_USER_CONTENT ||
                operation.operationId in retained
            if (userAuthorized && (operation.operationId in applied || operation.operationId in retained)) {
                changes += derivedChange(
                    subjectId = operation.operationId,
                    subjectKind = EvolutionSubjectKind.USER_DECISION,
                    kind = EvolutionChangeKind.USER_DECISION_APPLIED,
                    beforeSha = operation.expectedCurrentSha256,
                    afterSha = result.afterDocumentShaByPath[operation.relativePath],
                    changedFields = listOf(
                        "disposition:${if (operation.operationId in retained) "RETAINED" else "APPLIED"}",
                        "operation:${operation.kind.name}",
                    ),
                    evidenceRefs = (operation.evidenceRefs + explanation?.evidenceRefs.orEmpty()),
                    affectedArtifacts = listOf(operation.artifactId.value),
                    confidence = EvolutionConfidenceClass.USER_AUTHORIZED,
                )
            }
        }
        return changes
    }

    private fun derivedChange(
        subjectId: String,
        subjectKind: EvolutionSubjectKind,
        kind: EvolutionChangeKind,
        beforeSha: String?,
        afterSha: String?,
        changedFields: List<String>,
        evidenceRefs: List<String> = emptyList(),
        predecessors: List<String> = emptyList(),
        affectedArtifacts: List<String> = emptyList(),
        confidence: EvolutionConfidenceClass,
    ): DocumentationEvolutionChange {
        val fields = changedFields.distinct().sorted()
        val id = EvolutionCanonicalizer.stableChangeId(
            subjectId,
            subjectKind,
            kind,
            beforeSha,
            afterSha,
            null,
            null,
            fields,
        )
        return DocumentationEvolutionChange(
            changeId = id,
            subjectId = subjectId,
            subjectKind = subjectKind,
            kind = kind,
            beforeSha256 = beforeSha,
            afterSha256 = afterSha,
            changedFields = fields,
            evidenceRefs = evidenceRefs.distinct().sorted(),
            causalPredecessorIds = predecessors.distinct().sorted(),
            affectedArtifactIds = affectedArtifacts.distinct().sorted(),
            confidenceClass = confidence,
            coverageState = EvolutionCoverageState.COMPLETE,
        )
    }
}

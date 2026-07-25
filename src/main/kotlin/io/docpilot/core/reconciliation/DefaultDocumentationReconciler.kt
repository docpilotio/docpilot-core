package io.docpilot.core.reconciliation

import io.docpilot.core.incremental.execution.DocumentationArtifactOperation
import io.docpilot.core.incremental.specification.review.DocumentationChangeKind
import io.docpilot.core.incremental.specification.review.DocumentationReviewDisposition
import io.docpilot.core.incremental.specification.ai.AiDocumentationPatchOperation

public class DefaultDocumentationReconciler : DocumentationReconciler {
    override fun preview(request: DocumentationReconciliationRequest): DocumentationReconciliationPlan {
        require(request.projectId.isNotBlank()) { "Project id must not be blank." }
        require(request.documents.distinctBy { it.artifactId }.size == request.documents.size) {
            "Duplicate reconciliation artifact ids."
        }
        require(request.documents.distinctBy { it.relativePath }.size == request.documents.size) {
            "Duplicate reconciliation paths."
        }
        val operations = mutableListOf<ReconciliationOperation>()
        val conflicts = mutableListOf<ReconciliationConflict>()
        val explanations = mutableListOf<DecisionExplanation>()
        val removalTargets = authorizedRemovalTargets(request)
        val plannedById = request.artifactPlan?.actions?.associateBy { it.artifactId }
        request.artifactPlan?.let { plan ->
            require(plan.actions.map { it.artifactId }.distinct().size == plan.actions.size) {
                "Incremental artifact plan contains duplicate artifact ids."
            }
        }

        request.documents
            .filter { input ->
                val action = plannedById?.get(input.artifactId)
                plannedById == null || action?.operation != DocumentationArtifactOperation.KEEP ||
                    input.reviewedBase?.let(ReconciliationIntegrity::sha256) !=
                    input.current?.let(ReconciliationIntegrity::sha256)
            }
            .sortedWith(compareBy({ it.relativePath }, { it.artifactId.value })).forEach { input ->
            require(ReconciliationIntegrity.safePath(input.relativePath)) {
                "Unsafe reconciliation path: ${input.relativePath}"
            }
            val baseSha = input.reviewedBase?.let(ReconciliationIntegrity::sha256)
            val currentSha = input.current?.let(ReconciliationIntegrity::sha256)
            val candidateSha = ReconciliationIntegrity.sha256(input.candidate)
            val evidence = input.manifest?.evidenceRefs.orEmpty().distinct().sorted()
            fun conflict(kind: ReconciliationConflictKind, rule: String, message: String) {
                val conflictId = "conflict:${input.artifactId.value}:${kind.name}"
                conflicts += ReconciliationConflict(
                    conflictId, input.artifactId, input.relativePath, kind, message, evidence,
                )
                val operationId = "operation:${input.artifactId.value}:CONFLICT"
                operations += ReconciliationOperation(
                    operationId, input.artifactId, input.relativePath,
                    ReconciliationOperationKind.CONFLICT, DocumentationOwnership.CONFLICTED,
                    currentSha, null, null, null, false, listOf(rule), evidence,
                )
                explanations += explanation(
                    conflictId, input, kind.name, listOf(rule), evidence, baseSha, currentSha,
                    candidateSha, listOf(operationId),
                )
            }

            if (input.current == null) {
                val manifest = generatedManifest(input, candidateSha, baseSha)
                val operation = operation(
                    input, ReconciliationOperationKind.CREATE_ARTIFACT,
                    DocumentationOwnership.DOCPILOT_OWNED, null, input.candidate, manifest,
                    false, "CURRENT_PATH_MISSING_CREATE_GENERATED", evidence,
                )
                operations += operation
                explanations += explanationFor(input, operation, baseSha, currentSha, candidateSha)
                return@forEach
            }
            val manifest = input.manifest
            if (manifest == null) {
                conflict(
                    ReconciliationConflictKind.UNKNOWN_OWNERSHIP,
                    "UNKNOWN_PATH_HAS_NO_OWNERSHIP_EVIDENCE",
                    "Existing document has no ownership manifest.",
                )
                return@forEach
            }
            if (!ReconciliationIntegrity.verifyManifest(manifest)) {
                conflict(
                    ReconciliationConflictKind.MANIFEST_TAMPERED,
                    "OWNERSHIP_MANIFEST_INTEGRITY_FAILED",
                    "Ownership manifest integrity verification failed.",
                )
                return@forEach
            }
            if (manifest.artifactId != input.artifactId || manifest.relativePath != input.relativePath) {
                conflict(
                    ReconciliationConflictKind.OWNERSHIP_EVIDENCE_MISMATCH,
                    "OWNERSHIP_MANIFEST_IDENTITY_MISMATCH",
                    "Ownership manifest does not match the artifact identity and path.",
                )
                return@forEach
            }
            if (manifest.mediaType != input.mediaType) {
                conflict(
                    ReconciliationConflictKind.MEDIA_TYPE_MISMATCH,
                    "OWNERSHIP_MANIFEST_MEDIA_TYPE_MISMATCH",
                    "Ownership manifest media type does not match.",
                )
                return@forEach
            }
            if (input.reviewedBase == null || manifest.reviewedBaseSha256 != baseSha) {
                conflict(
                    ReconciliationConflictKind.REVIEWED_BASE_MISSING,
                    "REVIEWED_BASE_MISSING_OR_MISMATCHED",
                    "Reviewed base is missing or does not match the ownership manifest.",
                )
                return@forEach
            }

            when (manifest.ownership) {
                DocumentationOwnership.USER_OWNED -> {
                    val operation = operation(
                        input, ReconciliationOperationKind.KEEP_USER_CONTENT,
                        manifest.ownership, currentSha, input.current, manifest, false,
                        "USER_OWNED_CONTENT_BYTE_PRESERVED", evidence,
                    )
                    operations += operation
                    explanations += explanationFor(input, operation, baseSha, currentSha, candidateSha)
                }
                DocumentationOwnership.DOCPILOT_OWNED ->
                    reconcileOwned(input, manifest, baseSha!!, currentSha, candidateSha, evidence, operations,
                        explanations, ::conflict)
                DocumentationOwnership.SHARED_MANAGED ->
                    reconcileShared(input, manifest, baseSha!!, currentSha, candidateSha, evidence,
                        removalTargets, operations,
                        explanations, ::conflict)
                DocumentationOwnership.UNKNOWN,
                DocumentationOwnership.CONFLICTED,
                -> conflict(
                    ReconciliationConflictKind.UNKNOWN_OWNERSHIP,
                    "OWNERSHIP_STATE_NOT_APPLICABLE",
                    "Ownership state does not permit reconciliation.",
                )
            }
        }

        val sortedOperations = operations.sortedWith(compareBy({ it.relativePath }, { it.operationId }))
        val sortedConflicts = conflicts.sortedWith(compareBy({ it.relativePath }, { it.kind.ordinal }, { it.conflictId }))
        val provisional = planPayload(request.projectId, sortedOperations, sortedConflicts)
        val planSha = ReconciliationIntegrity.sha256(provisional)
        val sortedExplanations = explanations.sortedBy { it.decisionId }
        val reportSha = ReconciliationIntegrity.sha256(explanationPayload(planSha, null, sortedExplanations))
        return DocumentationReconciliationPlan(
            projectId = request.projectId,
            operations = sortedOperations,
            conflicts = sortedConflicts,
            planSha256 = planSha,
            explanationReport = ReconciliationExplanationReport(
                planSha256 = planSha,
                resultSha256 = null,
                explanations = sortedExplanations,
                reportSha256 = reportSha,
            ),
        )
    }

    override fun apply(
        plan: DocumentationReconciliationPlan,
        decisions: List<DocumentationReconciliationDecision>,
        aiProposals: List<AiReconciliationProposal>,
        store: ReconciliationDocumentStore,
    ): ReconciliationApplyResult {
        store.findResult(plan.planSha256)?.let { return ReconciliationApplyResult.AlreadyApplied(it) }
        if (!plan.applicable) return ReconciliationApplyResult.Conflict("Reconciliation plan has unresolved conflicts.")
        if (ReconciliationIntegrity.sha256(planPayload(plan.projectId, plan.operations, plan.conflicts)) !=
            plan.planSha256
        ) return ReconciliationApplyResult.Conflict("Reconciliation plan integrity mismatch.")
        if (plan.operations.any { operation ->
                operation.resultManifest?.let { !ReconciliationIntegrity.verifyManifest(it) } == true
            }
        ) return ReconciliationApplyResult.Conflict("Reconciliation result manifest integrity mismatch.")
        if (!verifyExplanationReport(plan.explanationReport, plan.planSha256, null)) {
            return ReconciliationApplyResult.Conflict("Reconciliation explanation integrity mismatch.")
        }
        if (!store.savePlan(plan)) {
            return ReconciliationApplyResult.Failed("Reconciliation plan could not be persisted.")
        }
        val decisionsById = decisions.associateBy { it.operationId }
        if (decisionsById.size != decisions.size) {
            return ReconciliationApplyResult.Conflict("Duplicate reconciliation decisions.")
        }
        val required = plan.operations.filter { it.requiresDecision }.map { it.operationId }
        val pending = required.filterNot(decisionsById::containsKey).sorted()
        if (pending.isNotEmpty()) return ReconciliationApplyResult.Pending(pending)
        if (decisionsById.keys.any { id -> plan.operations.none { it.operationId == id } }) {
            return ReconciliationApplyResult.Conflict("Decision targets an unauthorized operation.")
        }

        val proposalsById = aiProposals.associateBy { it.proposalId }
        val documents = linkedMapOf<String, String>()
        val manifests = linkedMapOf<String, DocumentationOwnershipManifest>()
        val expected = linkedMapOf<String, String?>()
        val applied = mutableListOf<String>()
        val retained = mutableListOf<String>()
        plan.operations.forEach { operation ->
            val current = store.read(operation.relativePath)
            val currentSha = current?.let(ReconciliationIntegrity::sha256)
            if (currentSha != operation.expectedCurrentSha256) {
                return ReconciliationApplyResult.Conflict("Current document changed after Preview: ${operation.relativePath}")
            }
            val storedManifestSha = store.manifestSha256(operation.artifactId.value)
            if (storedManifestSha != null && storedManifestSha != operation.expectedManifestSha256) {
                return ReconciliationApplyResult.Conflict(
                    "Ownership manifest changed after Preview: ${operation.artifactId.value}",
                )
            }
            val decision = decisionsById[operation.operationId]
            val selectedContent = when (decision?.disposition) {
                ReconciliationDecisionDisposition.KEEP_CURRENT,
                ReconciliationDecisionDisposition.REJECT,
                -> current
                ReconciliationDecisionDisposition.ACCEPT_AI_PROPOSAL -> {
                    val proposal = decision.proposalId?.let(proposalsById::get)
                        ?: return ReconciliationApplyResult.Conflict("Accepted AI proposal is missing.")
                    if (proposal.artifactId != operation.artifactId ||
                        proposal.expectedCurrentSha256 != operation.expectedCurrentSha256 ||
                        proposal.expectedCandidateSha256 != operation.resultSha256
                    ) return ReconciliationApplyResult.Conflict("AI proposal is stale or unauthorized.")
                    proposal.proposedContent
                }
                ReconciliationDecisionDisposition.ACCEPT_GENERATED,
                null,
                -> operation.resultContent
            }
            expected[operation.relativePath] = operation.expectedCurrentSha256
            if (selectedContent != null && selectedContent != current) {
                documents[operation.relativePath] = selectedContent
                val selectedSha = ReconciliationIntegrity.sha256(selectedContent)
                val manifest = operation.resultManifest?.copy(
                    reviewedBaseSha256 = selectedSha,
                    manifestSha256 = "",
                )?.let(ReconciliationIntegrity::signManifest)
                if (manifest != null) manifests[operation.artifactId.value] = manifest
                applied += operation.operationId
            } else {
                retained += operation.operationId
            }
        }
        val afterSha = plan.operations.associate { operation ->
            val content = documents[operation.relativePath] ?: store.read(operation.relativePath).orEmpty()
            operation.relativePath to ReconciliationIntegrity.sha256(content)
        }.toSortedMap()
        val resultBody = buildString {
            append(plan.planSha256).append('\n')
            append(applied.sorted()).append('\n').append(retained.sorted()).append('\n').append(afterSha)
        }
        val resultSha = ReconciliationIntegrity.sha256(resultBody)
        val explanations = plan.explanationReport.explanations
        val finalExplanation = ReconciliationExplanationReport(
            planSha256 = plan.planSha256,
            resultSha256 = resultSha,
            explanations = explanations,
            reportSha256 = ReconciliationIntegrity.sha256(
                explanationPayload(plan.planSha256, resultSha, explanations),
            ),
        )
        val result = DocumentationReconciliationResult(
            planSha256 = plan.planSha256,
            appliedOperationIds = applied.sorted(),
            retainedOperationIds = retained.sorted(),
            afterDocumentShaByPath = afterSha,
            resultSha256 = resultSha,
            explanationReport = finalExplanation,
        )
        return if (store.applyAtomically(expected, documents, manifests, result)) {
            ReconciliationApplyResult.Applied(result)
        } else {
            ReconciliationApplyResult.Conflict("Atomic reconciliation store rejected stale state.")
        }
    }

    private fun reconcileOwned(
        input: ReconciliationDocumentInput,
        manifest: DocumentationOwnershipManifest,
        baseSha: String,
        currentSha: String?,
        candidateSha: String,
        evidence: List<String>,
        operations: MutableList<ReconciliationOperation>,
        explanations: MutableList<DecisionExplanation>,
        conflict: (ReconciliationConflictKind, String, String) -> Unit,
    ) {
        val operation = when {
            currentSha == baseSha && candidateSha == baseSha -> operation(
                input, ReconciliationOperationKind.KEEP, manifest.ownership, currentSha, input.current,
                manifest, false, "CURRENT_AND_CANDIDATE_MATCH_BASE", evidence,
            )
            currentSha == baseSha -> operation(
                input, ReconciliationOperationKind.UPDATE_OWNED_ARTIFACT, manifest.ownership, currentSha,
                input.candidate, updatedManifest(manifest, candidateSha), true,
                "CURRENT_MATCHES_REVIEWED_BASE", evidence,
            )
            candidateSha == baseSha -> operation(
                input, ReconciliationOperationKind.KEEP_USER_CONTENT, manifest.ownership, currentSha,
                input.current, manifest, true, "CANDIDATE_UNCHANGED_USER_DRIFT_RETAINED", evidence,
            )
            currentSha == candidateSha -> operation(
                input, ReconciliationOperationKind.KEEP, manifest.ownership, currentSha, input.current,
                updatedManifest(manifest, candidateSha), false, "CURRENT_AND_CANDIDATE_CONVERGED", evidence,
            )
            else -> {
                conflict(
                    ReconciliationConflictKind.OVERLAPPING_USER_AND_GENERATED_EDIT,
                    "CURRENT_AND_CANDIDATE_DIVERGED",
                    "Owned document changed differently in current and generated candidate.",
                )
                return
            }
        }
        operations += operation
        explanations += explanationFor(input, operation, baseSha, currentSha, candidateSha)
    }

    private fun reconcileShared(
        input: ReconciliationDocumentInput,
        manifest: DocumentationOwnershipManifest,
        baseSha: String,
        currentSha: String?,
        candidateSha: String,
        evidence: List<String>,
        authorizedRemovalTargets: Set<String>,
        operations: MutableList<ReconciliationOperation>,
        explanations: MutableList<DecisionExplanation>,
        conflict: (ReconciliationConflictKind, String, String) -> Unit,
    ) {
        val merged = try {
            val baseBlocks = ManagedBlockDocument.parse(input.reviewedBase!!).associateBy { it.id }
            val currentBlocks = ManagedBlockDocument.parse(input.current!!).associateBy { it.id }
            val expectedIds = manifest.managedBlocks.mapTo(sortedSetOf()) { it.blockId }
            require(baseBlocks.keys == expectedIds && currentBlocks.keys == expectedIds) {
                "Managed block ownership manifest does not match document blocks."
            }
            manifest.managedBlocks.forEach { owned ->
                require(ReconciliationIntegrity.sha256(baseBlocks.getValue(owned.blockId).body) ==
                    owned.reviewedBaseContentSha256
                ) { "Managed block reviewed base hash mismatch: ${owned.blockId}" }
            }
            ManagedBlockDocument.merge(
                input.reviewedBase!!,
                input.current!!,
                input.candidate,
                manifest.managedBlocks.filter { it.targetId in authorizedRemovalTargets }.mapTo(hashSetOf()) {
                    it.blockId
                },
            )
        } catch (error: IllegalArgumentException) {
            conflict(
                ReconciliationConflictKind.MANAGED_BLOCK_MARKER_INVALID,
                "MANAGED_BLOCK_MARKER_INVALID",
                error.message ?: "Managed block markers are invalid.",
            )
            return
        } catch (error: IllegalStateException) {
            conflict(
                ReconciliationConflictKind.OVERLAPPING_USER_AND_GENERATED_EDIT,
                "CURRENT_AND_CANDIDATE_DIVERGED",
                error.message ?: "Managed block changed in both inputs.",
            )
            return
        }
        val removed = ManagedBlockDocument.parse(input.current!!).map { it.id }.toSet() -
            ManagedBlockDocument.parse(merged).map { it.id }.toSet()
        val kind = when {
            merged == input.current -> ReconciliationOperationKind.KEEP
            removed.isNotEmpty() -> ReconciliationOperationKind.REMOVE_MANAGED_BLOCKS
            else -> ReconciliationOperationKind.UPDATE_MANAGED_BLOCKS
        }
        val resultSha = ReconciliationIntegrity.sha256(merged)
        val operation = operation(
            input, kind, manifest.ownership, currentSha, merged,
            updatedManagedManifest(manifest, merged, resultSha),
            kind != ReconciliationOperationKind.KEEP,
            if (kind == ReconciliationOperationKind.KEEP) "MANAGED_BLOCKS_UNCHANGED"
            else "UNMANAGED_REGION_BYTE_PRESERVED",
            evidence,
        )
        operations += operation
        explanations += explanationFor(input, operation, baseSha, currentSha, candidateSha)
    }

    private fun operation(
        input: ReconciliationDocumentInput,
        kind: ReconciliationOperationKind,
        ownership: DocumentationOwnership,
        currentSha: String?,
        content: String?,
        manifest: DocumentationOwnershipManifest?,
        requiresDecision: Boolean,
        rule: String,
        evidence: List<String>,
    ): ReconciliationOperation = ReconciliationOperation(
        operationId = "operation:${input.artifactId.value}:${kind.name}",
        artifactId = input.artifactId,
        relativePath = input.relativePath,
        kind = kind,
        ownership = ownership,
        expectedCurrentSha256 = currentSha,
        resultContent = content,
        resultSha256 = content?.let(ReconciliationIntegrity::sha256),
        resultManifest = manifest,
        requiresDecision = requiresDecision,
        ruleIds = listOf(rule),
        evidenceRefs = evidence,
        expectedManifestSha256 = input.manifest?.manifestSha256,
    )

    private fun generatedManifest(
        input: ReconciliationDocumentInput,
        candidateSha: String,
        baseSha: String?,
    ): DocumentationOwnershipManifest = ReconciliationIntegrity.signManifest(
        DocumentationOwnershipManifest(
            artifactId = input.artifactId,
            relativePath = input.relativePath,
            mediaType = input.mediaType,
            ownership = DocumentationOwnership.DOCPILOT_OWNED,
            reviewedBaseSha256 = baseSha ?: candidateSha,
            rendererIdentity = "docpilot-core",
            evidenceRefs = listOf("CURRENT_PATH_MISSING_CREATE_GENERATED"),
            manifestSha256 = "",
        ),
    )

    private fun updatedManifest(
        manifest: DocumentationOwnershipManifest,
        resultSha: String,
    ): DocumentationOwnershipManifest = ReconciliationIntegrity.signManifest(
        manifest.copy(reviewedBaseSha256 = resultSha, manifestSha256 = ""),
    )

    private fun updatedManagedManifest(
        manifest: DocumentationOwnershipManifest,
        content: String,
        resultSha: String,
    ): DocumentationOwnershipManifest {
        val blocks = ManagedBlockDocument.parse(content).associateBy { it.id }
        return ReconciliationIntegrity.signManifest(
            manifest.copy(
                reviewedBaseSha256 = resultSha,
                managedBlocks = manifest.managedBlocks.filter { it.blockId in blocks }.map { owned ->
                    val hash = ReconciliationIntegrity.sha256(blocks.getValue(owned.blockId).body)
                    owned.copy(reviewedBaseContentSha256 = hash, lastAppliedContentSha256 = hash)
                },
                manifestSha256 = "",
            ),
        )
    }

    private fun explanationFor(
        input: ReconciliationDocumentInput,
        operation: ReconciliationOperation,
        baseSha: String?,
        currentSha: String?,
        candidateSha: String,
    ): DecisionExplanation = explanation(
        operation.operationId,
        input,
        operation.kind.name,
        operation.ruleIds,
        operation.evidenceRefs,
        baseSha,
        currentSha,
        candidateSha,
        listOf(operation.operationId),
    )

    private fun explanation(
        id: String,
        input: ReconciliationDocumentInput,
        outcome: String,
        rules: List<String>,
        evidence: List<String>,
        baseSha: String?,
        currentSha: String?,
        candidateSha: String,
        operationIds: List<String>,
    ) = DecisionExplanation(
        decisionId = id,
        subjectId = input.artifactId.value,
        outcome = outcome,
        ruleIds = rules.sorted(),
        evidenceRefs = evidence.sorted(),
        baseSha256 = baseSha,
        currentSha256 = currentSha,
        candidateSha256 = candidateSha,
        causedOperationIds = operationIds.sorted(),
    )

    private fun planPayload(
        projectId: String,
        operations: List<ReconciliationOperation>,
        conflicts: List<ReconciliationConflict>,
    ): String = buildString {
        append(projectId).append('\n')
        operations.sortedBy { it.operationId }.forEach {
            append(it.operationId).append('|').append(it.artifactId.value).append('|')
                .append(it.relativePath).append('|').append(it.kind.name).append('|')
                .append(it.ownership.name).append('|').append(it.expectedCurrentSha256 ?: "").append('|')
                .append(it.resultSha256 ?: "").append('|')
                .append(it.resultManifest?.manifestSha256 ?: "").append('|')
                .append(it.expectedManifestSha256 ?: "").append('|')
                .append(it.requiresDecision).append('|')
                .append(it.ruleIds.sorted()).append('|').append(it.evidenceRefs.sorted()).append('\n')
        }
        conflicts.sortedBy { it.conflictId }.forEach {
            append(it.conflictId).append('|').append(it.kind.name).append('|')
                .append(it.relativePath).append('|').append(it.evidenceRefs.sorted()).append('\n')
        }
    }

    private fun explanationPayload(
        planSha: String,
        resultSha: String?,
        explanations: List<DecisionExplanation>,
    ): String = buildString {
        append(planSha).append('|').append(resultSha ?: "").append('\n')
        explanations.sortedBy { it.decisionId }.forEach {
            append(it.decisionId).append('|').append(it.subjectId).append('|').append(it.outcome).append('|')
                .append(it.ruleIds).append('|').append(it.evidenceRefs).append('|')
                .append(it.baseSha256 ?: "").append('|').append(it.currentSha256 ?: "").append('|')
                .append(it.candidateSha256).append('|').append(it.causedOperationIds).append('\n')
        }
    }

    private fun verifyExplanationReport(
        report: ReconciliationExplanationReport,
        planSha: String,
        resultSha: String?,
    ): Boolean = report.formatVersion == 1 &&
        report.planSha256 == planSha &&
        report.resultSha256 == resultSha &&
        report.reportSha256 == ReconciliationIntegrity.sha256(
            explanationPayload(planSha, resultSha, report.explanations),
        )

    private fun authorizedRemovalTargets(request: DocumentationReconciliationRequest): Set<String> {
        val bundle = request.removalReviewBundle ?: return emptySet()
        require(bundle.projectIdentity.projectId == request.projectId) {
            "Removal Review Bundle project identity mismatch."
        }
        require(bundle.proposal.isComplete) { "Removal Review Bundle is incomplete." }
        val decisions = bundle.decisions.associateBy { it.targetId }
        require(decisions.size == bundle.proposal.entries.size) {
            "Removal Review Bundle does not contain complete decisions."
        }
        return bundle.proposal.entries.filter { entry ->
            entry.documentationChangeKind == DocumentationChangeKind.REMOVE &&
                entry.operation == AiDocumentationPatchOperation.REMOVE &&
                decisions[entry.targetId]?.disposition == DocumentationReviewDisposition.ACCEPTED
        }.mapTo(sortedSetOf()) { it.targetId }
    }
}

package io.docpilot.core.incremental.specification.review

import io.docpilot.core.incremental.specification.IncrementalUpdateAction
import io.docpilot.core.incremental.specification.IncrementalUpdateTarget
import io.docpilot.core.incremental.specification.ai.AiDocumentationMerger
import io.docpilot.core.incremental.specification.ai.AiDocumentationPatch
import io.docpilot.core.incremental.specification.ai.ManagedBlockAiDocumentationMerger
import io.docpilot.core.model.ProjectSpecification

public interface DocumentationDiffReviewer {
    public fun propose(request: DocumentationReviewRequest): DocumentationReviewProposal

    public fun apply(
        existingDocumentation: String,
        proposal: DocumentationReviewProposal,
        decisions: List<DocumentationReviewDecision>,
    ): DocumentationReviewResult
}

public class DefaultDocumentationDiffReviewer(
    private val blockReader: ManagedDocumentationBlockReader = HtmlCommentManagedDocumentationBlockReader(),
    private val merger: AiDocumentationMerger = ManagedBlockAiDocumentationMerger(),
) : DocumentationDiffReviewer {
    override fun propose(request: DocumentationReviewRequest): DocumentationReviewProposal {
        val actions = request.updatePlan.actions
        require(actions.map { it.id }.distinct().size == actions.size) {
            "Incremental update plan contains duplicate target ids."
        }
        require(request.patches.map { it.targetId }.distinct().size == request.patches.size) {
            "AI documentation patches contain duplicate target ids."
        }

        val actionById = actions.associateBy { it.id }
        val unexpectedPatchIds = request.patches.map { it.targetId }.filterNot(actionById::containsKey).sorted()
        require(unexpectedPatchIds.isEmpty()) {
            "Documentation review contains patches outside the incremental update plan: ${unexpectedPatchIds.joinToString()}"
        }

        val existingBlocks = blockReader.read(request.existingDocumentation)
        val patchById = request.patches.associateBy { it.targetId }
        val entries = request.patches.map { patch ->
            val action = actionById.getValue(patch.targetId)
            val existingMarkdown = existingBlocks[patch.targetId]
            DocumentationReviewEntry(
                targetId = patch.targetId,
                parentId = action.parentId,
                target = action.target,
                specificationChangeKind = action.changeKind,
                documentationChangeKind = documentationChangeKind(existingMarkdown, patch.markdown),
                existingMarkdown = existingMarkdown,
                proposedMarkdown = patch.markdown.trim(),
                evidenceIds = evidenceIds(
                    action,
                    request.previousSpecification,
                    request.currentSpecification,
                ),
            )
        }.sortedWith(DocumentationReviewProposal.ENTRY_ORDER)

        val missingPatchTargetIds = actions.map { it.id }
            .filterNot(patchById::containsKey)
            .sorted()

        return DocumentationReviewProposal(entries, missingPatchTargetIds)
    }

    override fun apply(
        existingDocumentation: String,
        proposal: DocumentationReviewProposal,
        decisions: List<DocumentationReviewDecision>,
    ): DocumentationReviewResult {
        require(decisions.map { it.targetId }.distinct().size == decisions.size) {
            "Documentation review contains duplicate decisions."
        }

        val entryById = proposal.entries.associateBy { it.targetId }
        val unknownDecisionIds = decisions.map { it.targetId }.filterNot(entryById::containsKey).sorted()
        require(unknownDecisionIds.isEmpty()) {
            "Documentation review contains decisions for unknown targets: ${unknownDecisionIds.joinToString()}"
        }

        val decisionById = decisions.associateBy { it.targetId }
        val pendingTargetIds = proposal.entries.map { it.targetId }
            .filterNot(decisionById::containsKey)
            .sorted()
        val acceptedTargetIds = decisions
            .filter { it.disposition == DocumentationReviewDisposition.ACCEPTED }
            .map { it.targetId }
            .sorted()
        val rejectedTargetIds = decisions
            .filter { it.disposition == DocumentationReviewDisposition.REJECTED }
            .map { it.targetId }
            .sorted()

        if (pendingTargetIds.isNotEmpty() || proposal.missingPatchTargetIds.isNotEmpty()) {
            return DocumentationReviewResult(
                status = DocumentationReviewApplyStatus.PENDING_REVIEW,
                mergedDocumentation = existingDocumentation,
                acceptedTargetIds = acceptedTargetIds,
                rejectedTargetIds = rejectedTargetIds,
                pendingTargetIds = pendingTargetIds,
                missingPatchTargetIds = proposal.missingPatchTargetIds,
            )
        }

        val acceptedPatches = proposal.entries
            .filter { decisionById.getValue(it.targetId).disposition == DocumentationReviewDisposition.ACCEPTED }
            .filter { it.documentationChangeKind != DocumentationChangeKind.NO_CHANGE }
            .map { AiDocumentationPatch(it.targetId, it.proposedMarkdown) }

        val mergedDocumentation = if (acceptedPatches.isEmpty()) {
            existingDocumentation
        } else {
            merger.merge(existingDocumentation, acceptedPatches)
        }

        return DocumentationReviewResult(
            status = DocumentationReviewApplyStatus.APPLIED,
            mergedDocumentation = mergedDocumentation,
            acceptedTargetIds = acceptedTargetIds,
            rejectedTargetIds = rejectedTargetIds,
        )
    }

    private fun documentationChangeKind(
        existingMarkdown: String?,
        proposedMarkdown: String,
    ): DocumentationChangeKind = when {
        existingMarkdown == null -> DocumentationChangeKind.CREATE
        normalize(existingMarkdown) == normalize(proposedMarkdown) -> DocumentationChangeKind.NO_CHANGE
        else -> DocumentationChangeKind.UPDATE
    }

    private fun normalize(markdown: String): String = markdown.trim().replace("\r\n", "\n")

    private fun evidenceIds(
        action: IncrementalUpdateAction,
        previous: ProjectSpecification,
        current: ProjectSpecification,
    ): List<String> = buildSet {
        addAll(findEvidenceIds(action.target, action.id, previous))
        addAll(findEvidenceIds(action.target, action.id, current))
    }.sorted()

    private fun findEvidenceIds(
        target: IncrementalUpdateTarget,
        id: String,
        specification: ProjectSpecification,
    ): Set<String> = when (target) {
        IncrementalUpdateTarget.PACKAGE -> specification.packages.firstOrNull { it.id == id }?.evidenceRefs
        IncrementalUpdateTarget.TYPE -> specification.components.firstOrNull { it.id == id }?.evidenceRefs
        IncrementalUpdateTarget.API -> specification.components
            .asSequence()
            .flatMap { it.apis.asSequence() }
            .firstOrNull { it.id == id }
            ?.evidenceRefs
        IncrementalUpdateTarget.PROPERTY -> specification.components
            .asSequence()
            .flatMap { it.properties.asSequence() }
            .firstOrNull { it.id == id }
            ?.evidenceRefs
    } ?: emptySet()
}

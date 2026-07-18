package io.docpilot.core.incremental.specification.review

import io.docpilot.core.incremental.specification.ChangeKind
import io.docpilot.core.incremental.specification.IncrementalUpdatePlan
import io.docpilot.core.incremental.specification.IncrementalUpdateTarget
import io.docpilot.core.incremental.specification.ai.AiDocumentationPatch
import io.docpilot.core.model.ProjectSpecification

public data class DocumentationReviewRequest(
    public val previousSpecification: ProjectSpecification,
    public val currentSpecification: ProjectSpecification,
    public val updatePlan: IncrementalUpdatePlan,
    public val existingDocumentation: String,
    public val patches: List<AiDocumentationPatch>,
)

public enum class DocumentationChangeKind {
    CREATE,
    UPDATE,
    NO_CHANGE,
}

public data class DocumentationReviewEntry(
    public val targetId: String,
    public val parentId: String? = null,
    public val target: IncrementalUpdateTarget,
    public val specificationChangeKind: ChangeKind,
    public val documentationChangeKind: DocumentationChangeKind,
    public val existingMarkdown: String? = null,
    public val proposedMarkdown: String,
    public val evidenceIds: List<String> = emptyList(),
) {
    init {
        require(targetId.isNotBlank()) { "Documentation review target id must not be blank." }
        require(parentId == null || parentId.isNotBlank()) {
            "Documentation review parent id must be null or non-blank."
        }
        require(proposedMarkdown.isNotBlank()) {
            "Documentation review proposed Markdown must not be blank for target: $targetId"
        }
        require(evidenceIds.none(String::isBlank)) {
            "Documentation review evidence ids must not be blank."
        }
        require(evidenceIds == evidenceIds.distinct().sorted()) {
            "Documentation review evidence ids must be unique and sorted."
        }
        when (documentationChangeKind) {
            DocumentationChangeKind.CREATE -> require(existingMarkdown == null) {
                "Created documentation must not have existing Markdown."
            }
            DocumentationChangeKind.UPDATE -> require(existingMarkdown != null) {
                "Updated documentation must have existing Markdown."
            }
            DocumentationChangeKind.NO_CHANGE -> require(existingMarkdown != null) {
                "Unchanged documentation must have existing Markdown."
            }
        }
    }
}

public data class DocumentationReviewProposal(
    public val entries: List<DocumentationReviewEntry> = emptyList(),
    public val missingPatchTargetIds: List<String> = emptyList(),
) {
    init {
        require(entries == entries.sortedWith(ENTRY_ORDER)) {
            "Documentation review entries must use deterministic order."
        }
        require(entries.map { it.targetId }.distinct().size == entries.size) {
            "Documentation review entries must have unique target ids."
        }
        require(missingPatchTargetIds.none(String::isBlank)) {
            "Missing patch target ids must not be blank."
        }
        require(missingPatchTargetIds == missingPatchTargetIds.distinct().sorted()) {
            "Missing patch target ids must be unique and sorted."
        }
    }

    public val isComplete: Boolean
        get() = missingPatchTargetIds.isEmpty()

    public companion object {
        public val ENTRY_ORDER: Comparator<DocumentationReviewEntry> =
            compareBy<DocumentationReviewEntry> { it.target.ordinal }
                .thenBy { it.parentId ?: "" }
                .thenBy { it.targetId }
    }
}

public enum class DocumentationReviewDisposition {
    ACCEPTED,
    REJECTED,
}

public data class DocumentationReviewDecision(
    public val targetId: String,
    public val disposition: DocumentationReviewDisposition,
    public val comment: String? = null,
) {
    init {
        require(targetId.isNotBlank()) { "Documentation review decision target id must not be blank." }
        require(comment == null || comment.isNotBlank()) {
            "Documentation review decision comment must be null or non-blank."
        }
    }
}

public enum class DocumentationReviewApplyStatus {
    PENDING_REVIEW,
    APPLIED,
}

public data class DocumentationReviewResult(
    public val status: DocumentationReviewApplyStatus,
    public val mergedDocumentation: String,
    public val acceptedTargetIds: List<String> = emptyList(),
    public val rejectedTargetIds: List<String> = emptyList(),
    public val pendingTargetIds: List<String> = emptyList(),
    public val missingPatchTargetIds: List<String> = emptyList(),
) {
    init {
        listOf(acceptedTargetIds, rejectedTargetIds, pendingTargetIds, missingPatchTargetIds).forEach { ids ->
            require(ids.none(String::isBlank)) { "Documentation review result ids must not be blank." }
            require(ids == ids.distinct().sorted()) {
                "Documentation review result ids must be unique and sorted."
            }
        }
        val decidedIds = acceptedTargetIds.toSet() intersect rejectedTargetIds.toSet()
        require(decidedIds.isEmpty()) { "A target cannot be both accepted and rejected." }
        if (status == DocumentationReviewApplyStatus.APPLIED) {
            require(pendingTargetIds.isEmpty() && missingPatchTargetIds.isEmpty()) {
                "Applied documentation review must not have pending or missing targets."
            }
        }
    }
}

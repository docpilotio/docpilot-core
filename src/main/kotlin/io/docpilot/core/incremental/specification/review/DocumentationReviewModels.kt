package io.docpilot.core.incremental.specification.review

import io.docpilot.core.incremental.specification.ChangeKind
import io.docpilot.core.incremental.specification.IncrementalUpdatePlan
import io.docpilot.core.incremental.specification.IncrementalUpdateTarget
import io.docpilot.core.incremental.specification.ai.AiDocumentationPatch
import io.docpilot.core.incremental.specification.ai.AiDocumentationPatchOperation
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
    REMOVE,
    NO_CHANGE,
}

public data class DocumentationReviewEntry(
    public val targetId: String,
    public val parentId: String? = null,
    public val target: IncrementalUpdateTarget,
    public val specificationChangeKind: ChangeKind,
    public val documentationChangeKind: DocumentationChangeKind,
    public val operation: AiDocumentationPatchOperation = AiDocumentationPatchOperation.UPSERT,
    public val existingMarkdown: String? = null,
    public val proposedMarkdown: String,
    public val evidenceIds: List<String> = emptyList(),
) {
    init {
        require(targetId.isNotBlank()) { "Documentation review target id must not be blank." }
        require(parentId == null || parentId.isNotBlank()) {
            "Documentation review parent id must be null or non-blank."
        }
        require(evidenceIds.none(String::isBlank)) {
            "Documentation review evidence ids must not be blank."
        }
        require(evidenceIds == evidenceIds.distinct().sorted()) {
            "Documentation review evidence ids must be unique and sorted."
        }
        when (operation) {
            AiDocumentationPatchOperation.UPSERT -> require(proposedMarkdown.isNotBlank()) {
                "Documentation review UPSERT Markdown must not be blank for target: $targetId"
            }
            AiDocumentationPatchOperation.REMOVE -> require(proposedMarkdown.isEmpty()) {
                "Documentation review REMOVE Markdown must be empty for target: $targetId"
            }
        }
        when (documentationChangeKind) {
            DocumentationChangeKind.CREATE -> require(
                operation == AiDocumentationPatchOperation.UPSERT && existingMarkdown == null,
            ) {
                "Created documentation must be an UPSERT without existing Markdown."
            }
            DocumentationChangeKind.UPDATE -> require(
                operation == AiDocumentationPatchOperation.UPSERT && existingMarkdown != null,
            ) {
                "Updated documentation must be an UPSERT with existing Markdown."
            }
            DocumentationChangeKind.REMOVE -> require(
                operation == AiDocumentationPatchOperation.REMOVE &&
                    specificationChangeKind == ChangeKind.REMOVED &&
                    existingMarkdown != null,
            ) {
                "Removed documentation must be an explicit REMOVE for an existing removed target."
            }
            DocumentationChangeKind.NO_CHANGE -> require(
                operation == AiDocumentationPatchOperation.UPSERT && existingMarkdown != null,
            ) {
                "Unchanged documentation must be an UPSERT with existing Markdown."
            }
        }
    }
}

public data class DocumentationReviewProposal(
    public val entries: List<DocumentationReviewEntry> = emptyList(),
    public val missingPatchTargetIds: List<String> = emptyList(),
    public val reviewedDocumentationSha256: String,
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
        require(reviewedDocumentationSha256.matches(Regex("[0-9a-f]{64}"))) {
            "Reviewed documentation SHA-256 must be lowercase hexadecimal."
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

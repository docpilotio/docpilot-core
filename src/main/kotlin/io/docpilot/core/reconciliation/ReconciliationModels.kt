package io.docpilot.core.reconciliation

import io.docpilot.core.api.DocumentationArtifactId
import io.docpilot.core.incremental.execution.DocumentationArtifactPlan
import io.docpilot.core.incremental.specification.review.StoredReviewBundle

public enum class DocumentationOwnership {
    DOCPILOT_OWNED,
    USER_OWNED,
    SHARED_MANAGED,
    UNKNOWN,
    CONFLICTED,
}

public data class ManagedBlockOwnership(
    public val blockId: String,
    public val targetId: String,
    public val reviewedBaseContentSha256: String,
    public val lastAppliedContentSha256: String,
) {
    init {
        require(blockId.isNotBlank() && targetId.isNotBlank()) { "Managed block identity must not be blank." }
    }
}

public data class DocumentationOwnershipManifest(
    public val formatVersion: Int = 1,
    public val artifactId: DocumentationArtifactId,
    public val relativePath: String,
    public val mediaType: String,
    public val ownership: DocumentationOwnership,
    public val reviewedBaseSha256: String?,
    public val managedBlocks: List<ManagedBlockOwnership> = emptyList(),
    public val rendererIdentity: String,
    public val evidenceRefs: List<String>,
    public val manifestSha256: String,
)

public data class ReconciliationDocumentInput(
    public val artifactId: DocumentationArtifactId,
    public val relativePath: String,
    public val mediaType: String = "text/markdown",
    public val reviewedBase: String?,
    public val current: String?,
    public val candidate: String,
    public val manifest: DocumentationOwnershipManifest?,
)

public data class AiReconciliationProposal(
    public val proposalId: String,
    public val artifactId: DocumentationArtifactId,
    public val expectedBaseSha256: String?,
    public val expectedCurrentSha256: String?,
    public val expectedCandidateSha256: String,
    public val proposedContent: String,
    public val evidenceRefs: List<String>,
)

public data class DocumentationReconciliationRequest(
    public val projectId: String,
    public val documents: List<ReconciliationDocumentInput>,
    public val aiProposals: List<AiReconciliationProposal> = emptyList(),
    public val artifactPlan: DocumentationArtifactPlan? = null,
    public val removalReviewBundle: StoredReviewBundle? = null,
)

public enum class ReconciliationOperationKind {
    CREATE_ARTIFACT,
    UPDATE_OWNED_ARTIFACT,
    UPDATE_MANAGED_BLOCKS,
    REMOVE_MANAGED_BLOCKS,
    KEEP,
    KEEP_USER_CONTENT,
    CONFLICT,
}

public enum class ReconciliationConflictKind {
    UNKNOWN_OWNERSHIP,
    OWNERSHIP_EVIDENCE_MISMATCH,
    MANIFEST_TAMPERED,
    PATH_COLLISION,
    MEDIA_TYPE_MISMATCH,
    MANAGED_BLOCK_MARKER_INVALID,
    REVIEWED_BASE_MISSING,
    OVERLAPPING_USER_AND_GENERATED_EDIT,
    STALE_PLAN,
}

public data class ReconciliationConflict(
    public val conflictId: String,
    public val artifactId: DocumentationArtifactId,
    public val relativePath: String,
    public val kind: ReconciliationConflictKind,
    public val message: String,
    public val evidenceRefs: List<String>,
)

public data class ReconciliationOperation(
    public val operationId: String,
    public val artifactId: DocumentationArtifactId,
    public val relativePath: String,
    public val kind: ReconciliationOperationKind,
    public val ownership: DocumentationOwnership,
    public val expectedCurrentSha256: String?,
    public val resultContent: String?,
    public val resultSha256: String?,
    public val resultManifest: DocumentationOwnershipManifest?,
    public val requiresDecision: Boolean,
    public val ruleIds: List<String>,
    public val evidenceRefs: List<String>,
    public val expectedManifestSha256: String? = null,
)

public data class DecisionExplanation(
    public val decisionId: String,
    public val subjectId: String,
    public val outcome: String,
    public val ruleIds: List<String>,
    public val evidenceRefs: List<String>,
    public val baseSha256: String?,
    public val currentSha256: String?,
    public val candidateSha256: String,
    public val causedOperationIds: List<String>,
)

public data class ReconciliationExplanationReport(
    public val formatVersion: Int = 1,
    public val planSha256: String,
    public val resultSha256: String?,
    public val explanations: List<DecisionExplanation>,
    public val reportSha256: String,
)

public data class DocumentationReconciliationPlan(
    public val formatVersion: Int = 1,
    public val projectId: String,
    public val operations: List<ReconciliationOperation>,
    public val conflicts: List<ReconciliationConflict>,
    public val planSha256: String,
    public val explanationReport: ReconciliationExplanationReport,
) {
    public val applicable: Boolean get() = conflicts.isEmpty()
}

public enum class ReconciliationDecisionDisposition {
    ACCEPT_GENERATED,
    KEEP_CURRENT,
    ACCEPT_AI_PROPOSAL,
    REJECT,
}

public data class DocumentationReconciliationDecision(
    public val operationId: String,
    public val disposition: ReconciliationDecisionDisposition,
    public val proposalId: String? = null,
)

public data class DocumentationReconciliationResult(
    public val formatVersion: Int = 1,
    public val planSha256: String,
    public val appliedOperationIds: List<String>,
    public val retainedOperationIds: List<String>,
    public val afterDocumentShaByPath: Map<String, String>,
    public val resultSha256: String,
    public val explanationReport: ReconciliationExplanationReport,
)

public sealed interface ReconciliationApplyResult {
    public data class Applied(public val result: DocumentationReconciliationResult) : ReconciliationApplyResult
    public data class AlreadyApplied(public val result: DocumentationReconciliationResult) : ReconciliationApplyResult
    public data class Conflict(public val message: String) : ReconciliationApplyResult
    public data class Pending(public val operationIds: List<String>) : ReconciliationApplyResult
    public data class Failed(public val message: String) : ReconciliationApplyResult
}

public interface ReconciliationDocumentStore {
    public fun read(relativePath: String): String?
    public fun manifestSha256(artifactId: String): String? = null
    public fun savePlan(plan: DocumentationReconciliationPlan): Boolean = true
    public fun findPlan(planSha256: String): DocumentationReconciliationPlan? = null
    public fun applyAtomically(
        expectedCurrentShaByPath: Map<String, String?>,
        documents: Map<String, String>,
        manifests: Map<String, DocumentationOwnershipManifest>,
        result: DocumentationReconciliationResult,
    ): Boolean
    public fun findResult(planSha256: String): DocumentationReconciliationResult?
}

public interface DocumentationReconciler {
    public fun preview(request: DocumentationReconciliationRequest): DocumentationReconciliationPlan
    public fun apply(
        plan: DocumentationReconciliationPlan,
        decisions: List<DocumentationReconciliationDecision>,
        aiProposals: List<AiReconciliationProposal>,
        store: ReconciliationDocumentStore,
    ): ReconciliationApplyResult
}

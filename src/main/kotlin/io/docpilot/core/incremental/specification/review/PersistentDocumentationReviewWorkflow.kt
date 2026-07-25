package io.docpilot.core.incremental.specification.review

import io.docpilot.core.incremental.specification.ai.AiIncrementalDocumentationGenerator
import io.docpilot.core.incremental.specification.ai.AiIncrementalGenerationRequest
import io.docpilot.core.incremental.specification.ai.AiIncrementalGenerationStatus
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

public sealed interface PersistentReviewPreparationResult {
    public data class Saved(public val bundle: StoredReviewBundle) : PersistentReviewPreparationResult
    public data object NoChanges : PersistentReviewPreparationResult
    public data class Failed(public val message: String) : PersistentReviewPreparationResult
}

public sealed interface PersistentReviewUpdateResult {
    public data class Saved(public val bundle: StoredReviewBundle) : PersistentReviewUpdateResult
    public data class Conflict(
        public val expectedPayloadSha256: String,
        public val actualPayloadSha256: String,
    ) : PersistentReviewUpdateResult
    public data class Failed(public val message: String) : PersistentReviewUpdateResult
}

public data class ResumableReviewApplyRequest(
    public val expectedProjectId: String,
    public val proposalId: String,
    public val currentDocumentation: String,
    public val expectedPayloadSha256: String? = null,
)

public enum class ResumableReviewConflict { BUNDLE_CHANGED, STALE_DOCUMENTATION }

public sealed interface ResumableReviewApplyResult {
    public data class Applied(
        public val proposalId: String,
        public val sourcePayloadSha256: String,
        public val mergedDocumentation: String,
        public val mergedDocumentationSha256: String,
        public val acceptedTargetIds: List<String>,
        public val rejectedTargetIds: List<String>,
    ) : ResumableReviewApplyResult
    public data class Pending(
        public val proposalId: String,
        public val pendingTargetIds: List<String>,
        public val missingPatchTargetIds: List<String>,
    ) : ResumableReviewApplyResult
    public data class InvalidBundle(
        public val reason: ReviewBundleValidationFailure,
        public val message: String,
    ) : ResumableReviewApplyResult
    public data class Conflict(
        public val reason: ResumableReviewConflict,
        public val message: String,
    ) : ResumableReviewApplyResult
}

public interface PersistentDocumentationReviewWorkflow {
    public fun prepareAndSave(request: AiIncrementalGenerationRequest): PersistentReviewPreparationResult
    public fun recordDecisions(
        expectedProjectId: String,
        proposalId: String,
        expectedPayloadSha256: String,
        decisions: List<DocumentationReviewDecision>,
    ): PersistentReviewUpdateResult
    public fun resumeApply(request: ResumableReviewApplyRequest): ResumableReviewApplyResult
}

public class DefaultPersistentDocumentationReviewWorkflow(
    private val generator: AiIncrementalDocumentationGenerator,
    private val repository: ReviewBundleRepository,
    private val codec: JsonReviewBundleCodec = JsonReviewBundleCodec(),
    private val reviewer: DocumentationDiffReviewer = DefaultDocumentationDiffReviewer(),
) : PersistentDocumentationReviewWorkflow {
    override fun prepareAndSave(request: AiIncrementalGenerationRequest): PersistentReviewPreparationResult {
        val generation = generator.generate(request)
        if (generation.status == AiIncrementalGenerationStatus.NO_CHANGES) return PersistentReviewPreparationResult.NoChanges
        if (generation.status == AiIncrementalGenerationStatus.FAILED) {
            return PersistentReviewPreparationResult.Failed(
                generation.errorMessage ?: "AI incremental generation failed.",
            )
        }
        return try {
            val proposal = reviewer.propose(
                DocumentationReviewRequest(
                    request.previousSpecification,
                    request.currentSpecification,
                    request.updatePlan,
                    request.existingDocumentation,
                    generation.patches,
                ),
            )
            val bundle = codec.create(request.previousSpecification, request.currentSpecification, proposal)
            when (val saved = repository.saveNew(bundle)) {
                is ReviewBundleSaveResult.Saved -> PersistentReviewPreparationResult.Saved(saved.bundle)
                ReviewBundleSaveResult.AlreadyExists -> PersistentReviewPreparationResult.Failed(
                    "Review bundle already exists: ${bundle.proposalId}",
                )
                is ReviewBundleSaveResult.Conflict -> PersistentReviewPreparationResult.Failed("Unexpected save conflict.")
                is ReviewBundleSaveResult.Invalid -> PersistentReviewPreparationResult.Failed(saved.message)
                is ReviewBundleSaveResult.Failed -> PersistentReviewPreparationResult.Failed(saved.message)
            }
        } catch (error: Exception) {
            PersistentReviewPreparationResult.Failed(error.message ?: "Unable to prepare persistent review.")
        }
    }

    override fun recordDecisions(
        expectedProjectId: String,
        proposalId: String,
        expectedPayloadSha256: String,
        decisions: List<DocumentationReviewDecision>,
    ): PersistentReviewUpdateResult {
        if (decisions.isEmpty()) return PersistentReviewUpdateResult.Failed("Decision update must not be empty.")
        if (decisions.map { it.targetId }.distinct().size != decisions.size) {
            return PersistentReviewUpdateResult.Failed("Decision update contains duplicate targets.")
        }
        val loaded = repository.load(expectedProjectId, proposalId)
        if (loaded !is ReviewBundleLoadResult.Valid) {
            return PersistentReviewUpdateResult.Failed("Review bundle is missing or invalid.")
        }
        if (loaded.bundle.integrity.payloadSha256 != expectedPayloadSha256) {
            return PersistentReviewUpdateResult.Conflict(
                expectedPayloadSha256,
                loaded.bundle.integrity.payloadSha256,
            )
        }
        return try {
            val merged = (loaded.bundle.decisions.associateBy { it.targetId } +
                decisions.associateBy { it.targetId }).values.sortedBy { it.targetId }
            val updated = codec.withDecisions(loaded.bundle, merged)
            when (val saved = repository.replace(updated, expectedPayloadSha256)) {
                is ReviewBundleSaveResult.Saved -> PersistentReviewUpdateResult.Saved(saved.bundle)
                is ReviewBundleSaveResult.Conflict ->
                    PersistentReviewUpdateResult.Conflict(saved.expectedPayloadSha256, saved.actualPayloadSha256)
                is ReviewBundleSaveResult.Invalid -> PersistentReviewUpdateResult.Failed(saved.message)
                is ReviewBundleSaveResult.Failed -> PersistentReviewUpdateResult.Failed(saved.message)
                ReviewBundleSaveResult.AlreadyExists -> PersistentReviewUpdateResult.Failed("Unexpected save collision.")
            }
        } catch (error: Exception) {
            PersistentReviewUpdateResult.Failed(error.message ?: "Unable to record review decisions.")
        }
    }

    override fun resumeApply(request: ResumableReviewApplyRequest): ResumableReviewApplyResult {
        val loaded = repository.load(request.expectedProjectId, request.proposalId)
        if (loaded is ReviewBundleLoadResult.Invalid) {
            return ResumableReviewApplyResult.InvalidBundle(loaded.reason, loaded.message)
        }
        if (loaded !is ReviewBundleLoadResult.Valid) {
            return ResumableReviewApplyResult.InvalidBundle(
                ReviewBundleValidationFailure.CORRUPTED,
                "Review bundle was not found.",
            )
        }
        val bundle = loaded.bundle
        if (request.expectedPayloadSha256 != null &&
            request.expectedPayloadSha256 != bundle.integrity.payloadSha256
        ) {
            return ResumableReviewApplyResult.Conflict(
                ResumableReviewConflict.BUNDLE_CHANGED,
                "Review bundle changed after it was selected for apply.",
            )
        }
        if (sha256(request.currentDocumentation) != bundle.proposal.reviewedDocumentationSha256) {
            return ResumableReviewApplyResult.Conflict(
                ResumableReviewConflict.STALE_DOCUMENTATION,
                "Documentation changed after review preparation.",
            )
        }
        val decisionIds = bundle.decisions.map { it.targetId }.toSet()
        val pending = bundle.proposal.entries.map { it.targetId }.filterNot(decisionIds::contains).sorted()
        if (!bundle.proposal.isComplete || pending.isNotEmpty()) {
            return ResumableReviewApplyResult.Pending(
                bundle.proposalId,
                pending,
                bundle.proposal.missingPatchTargetIds,
            )
        }
        return try {
            val result = reviewer.apply(request.currentDocumentation, bundle.proposal, bundle.decisions)
            ResumableReviewApplyResult.Applied(
                bundle.proposalId,
                bundle.integrity.payloadSha256,
                result.mergedDocumentation,
                sha256(result.mergedDocumentation),
                result.acceptedTargetIds,
                result.rejectedTargetIds,
            )
        } catch (error: Exception) {
            ResumableReviewApplyResult.InvalidBundle(
                ReviewBundleValidationFailure.INVALID_PROPOSAL,
                error.message ?: "Unable to apply review bundle.",
            )
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

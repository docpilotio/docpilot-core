package io.docpilot.core.incremental.specification.review

public sealed interface ReviewAuditVerificationResult {
    public data class Verified(public val aggregate: ReviewLifecycleAggregate) : ReviewAuditVerificationResult
    public data class Invalid(public val message: String) : ReviewAuditVerificationResult
}

public class ReviewLifecycleService(
    private val repository: ReviewLifecycleRepository,
    private val codec: ReviewLifecycleCodec = ReviewLifecycleCodec(),
) {
    public fun supersede(
        current: ReviewLifecycleMetadata,
        replacementProposalId: String,
    ): ReviewLifecycleResult<ReviewLifecycleAggregate> {
        if (current.state != ReviewLifecycleState.ACTIVE) {
            return ReviewLifecycleResult.Failure(ReviewLifecycleFailure.CONFLICT, "Only an active review can be superseded.")
        }
        require(replacementProposalId.matches(Regex("review:[0-9a-f]{64}")))
        require(replacementProposalId != current.proposalId)
        return repository.transition(current, ReviewLifecycleState.SUPERSEDED, replacementProposalId)
    }

    public fun archive(current: ReviewLifecycleMetadata): ReviewLifecycleResult<ReviewLifecycleAggregate> {
        if (current.state == ReviewLifecycleState.APPLYING ||
            current.state == ReviewLifecycleState.RECOVERY_REQUIRED ||
            current.state == ReviewLifecycleState.ARCHIVED
        ) {
            return ReviewLifecycleResult.Failure(ReviewLifecycleFailure.CONFLICT, "Lifecycle cannot be archived in its current state.")
        }
        return repository.transition(current, ReviewLifecycleState.ARCHIVED, archivedFrom = current.state)
    }

    public fun verifyOffline(
        expectedProjectId: String,
        expectedProposalId: String,
        expectedBundlePayloadSha256: String,
    ): ReviewAuditVerificationResult = when (val loaded = repository.load(expectedProjectId, expectedProposalId)) {
        is ReviewLifecycleResult.Failure -> ReviewAuditVerificationResult.Invalid(loaded.message)
        is ReviewLifecycleResult.Success -> {
            val aggregate = loaded.value
            val metadata = aggregate.metadata
            when {
                !codec.verify(metadata) -> ReviewAuditVerificationResult.Invalid("Lifecycle integrity mismatch.")
                metadata.observedBundlePayloadSha256 != expectedBundlePayloadSha256 ->
                    ReviewAuditVerificationResult.Invalid("Bundle payload identity mismatch.")
                aggregate.receipt != null && !codec.verify(aggregate.receipt) ->
                    ReviewAuditVerificationResult.Invalid("Receipt integrity mismatch.")
                aggregate.receipt != null &&
                    aggregate.receipt.bundlePayloadSha256 != expectedBundlePayloadSha256 ->
                    ReviewAuditVerificationResult.Invalid("Receipt does not bind the expected bundle.")
                else -> ReviewAuditVerificationResult.Verified(aggregate)
            }
        }
    }
}

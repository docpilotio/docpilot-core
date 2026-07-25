package io.docpilot.core.incremental.specification.review

public sealed interface LifecycleApplyResult {
    public data class Applied(public val receipt: ApplyReceipt, public val generation: Long) : LifecycleApplyResult
    public data class AlreadyApplied(public val receipt: ApplyReceipt, public val generation: Long) : LifecycleApplyResult
    public data class Pending(public val targetIds: List<String>) : LifecycleApplyResult
    public data class Conflict(public val message: String) : LifecycleApplyResult
    public data class RecoveryRequired(public val message: String) : LifecycleApplyResult
    public data class Failed(public val message: String) : LifecycleApplyResult
}

public class ReviewLifecycleApplyWorkflow(
    private val bundles: ReviewBundleRepository,
    private val lifecycles: ReviewLifecycleRepository,
    private val codec: ReviewLifecycleCodec = ReviewLifecycleCodec(),
    private val reviewer: DocumentationDiffReviewer = DefaultDocumentationDiffReviewer(),
) {
    public fun initialize(bundle: StoredReviewBundle): ReviewLifecycleResult<ReviewLifecycleAggregate> =
        lifecycles.initialize(bundle)

    public fun recover(
        expectedProjectId: String,
        proposalId: String,
        documentation: DocumentationResource,
    ): LifecycleApplyResult {
        val aggregate = when (val loaded = lifecycles.load(expectedProjectId, proposalId)) {
            is ReviewLifecycleResult.Success -> loaded.value
            is ReviewLifecycleResult.Failure -> return LifecycleApplyResult.Failed(loaded.message)
        }
        if (aggregate.metadata.state == ReviewLifecycleState.APPLIED) {
            val receipt = requireNotNull(aggregate.receipt)
            return if (codec.sha256(documentation.read()) == receipt.resultDocumentationSha256) {
                LifecycleApplyResult.AlreadyApplied(receipt, aggregate.metadata.generation)
            } else LifecycleApplyResult.Conflict("Documentation changed after the recorded apply.")
        }
        if (aggregate.metadata.state != ReviewLifecycleState.APPLYING) {
            return LifecycleApplyResult.Conflict("No recoverable apply transaction exists.")
        }
        val artifacts = when (val loaded = lifecycles.loadTransaction(proposalId)) {
            is ReviewLifecycleResult.Success -> loaded.value
            is ReviewLifecycleResult.Failure -> return markRecoveryRequired(aggregate.metadata, loaded.message)
        }
        val (transaction, receipt) = artifacts
        val actual = codec.sha256(documentation.read())
        return when (actual) {
            transaction.resultDocumentationSha256 -> {
                when (val committed = lifecycles.commitApply(aggregate.metadata, receipt)) {
                    is ReviewLifecycleResult.Success ->
                        LifecycleApplyResult.Applied(receipt, committed.value.metadata.generation)
                    is ReviewLifecycleResult.Failure -> markRecoveryRequired(aggregate.metadata, committed.message)
                }
            }
            transaction.inputDocumentationSha256 -> {
                if (transaction.phase != ReviewApplyTransactionPhase.PREPARED) {
                    markRecoveryRequired(aggregate.metadata, "Journal says document was replaced, but input remains.")
                } else {
                    when (val rolledBack = lifecycles.transition(aggregate.metadata, ReviewLifecycleState.ACTIVE)) {
                        is ReviewLifecycleResult.Success ->
                            LifecycleApplyResult.Conflict("Prepared transaction rolled back; apply may be retried.")
                        is ReviewLifecycleResult.Failure -> markRecoveryRequired(aggregate.metadata, rolledBack.message)
                    }
                }
            }
            else -> markRecoveryRequired(aggregate.metadata, "Documentation matches neither transaction boundary.")
        }
    }

    public fun apply(
        expectedProjectId: String,
        proposalId: String,
        expectedBundlePayloadSha256: String,
        expectedLifecycleGeneration: Long,
        documentation: DocumentationResource,
    ): LifecycleApplyResult {
        val loaded = bundles.load(expectedProjectId, proposalId)
        if (loaded !is ReviewBundleLoadResult.Valid) return LifecycleApplyResult.Failed("Review bundle is missing or invalid.")
        val bundle = loaded.bundle
        if (bundle.integrity.payloadSha256 != expectedBundlePayloadSha256) {
            return LifecycleApplyResult.Conflict("Review bundle changed after selection.")
        }
        val aggregate = when (val result = lifecycles.load(expectedProjectId, proposalId)) {
            is ReviewLifecycleResult.Success -> result.value
            is ReviewLifecycleResult.Failure -> return LifecycleApplyResult.Failed(result.message)
        }
        if (aggregate.metadata.state == ReviewLifecycleState.RECOVERY_REQUIRED ||
            aggregate.metadata.state == ReviewLifecycleState.APPLYING
        ) {
            return LifecycleApplyResult.RecoveryRequired("An incomplete apply transaction requires recovery.")
        }
        if (aggregate.metadata.state == ReviewLifecycleState.APPLIED) {
            val receipt = requireNotNull(aggregate.receipt)
            return if (codec.sha256(documentation.read()) == receipt.resultDocumentationSha256) {
                LifecycleApplyResult.AlreadyApplied(receipt, aggregate.metadata.generation)
            } else {
                LifecycleApplyResult.Conflict("Documentation changed after the recorded apply.")
            }
        }
        if (aggregate.metadata.state != ReviewLifecycleState.ACTIVE) {
            return LifecycleApplyResult.Conflict("Review lifecycle is not active.")
        }
        if (aggregate.metadata.generation != expectedLifecycleGeneration ||
            aggregate.metadata.observedBundlePayloadSha256 != bundle.integrity.payloadSha256
        ) {
            return LifecycleApplyResult.Conflict("Lifecycle generation or observed bundle changed.")
        }
        val decisionIds = bundle.decisions.map { it.targetId }.toSet()
        val pending = bundle.proposal.entries.map { it.targetId }.filterNot(decisionIds::contains).sorted()
        if (!bundle.proposal.isComplete || pending.isNotEmpty()) return LifecycleApplyResult.Pending(pending)
        val current = documentation.read()
        if (codec.sha256(current) != bundle.proposal.reviewedDocumentationSha256) {
            return LifecycleApplyResult.Conflict("Documentation changed after review preparation.")
        }
        return try {
            val merged = reviewer.apply(current, bundle.proposal, bundle.decisions).mergedDocumentation
            val resultSha = codec.sha256(merged)
            val receipt = codec.receipt(bundle, resultSha)
            var transaction = codec.transaction(
                bundle, aggregate.metadata.generation, resultSha, receipt.receiptId, ReviewApplyTransactionPhase.PREPARED,
            )
            val applying = when (val begun = lifecycles.beginApply(aggregate.metadata, transaction, receipt)) {
                is ReviewLifecycleResult.Success -> begun.value.metadata
                is ReviewLifecycleResult.Failure -> return LifecycleApplyResult.Failed(begun.message)
            }
            documentation.replace(current, merged)
            transaction = transaction.copy(phase = ReviewApplyTransactionPhase.DOCUMENT_REPLACED)
                .let { codec.transaction(bundle, it.expectedLifecycleGeneration, resultSha, receipt.receiptId, it.phase) }
            if (lifecycles.updateTransaction(transaction) is ReviewLifecycleResult.Failure) {
                return LifecycleApplyResult.RecoveryRequired("Documentation changed but the journal update failed.")
            }
            val committed = when (val result = lifecycles.commitApply(applying, receipt)) {
                is ReviewLifecycleResult.Success -> result.value
                is ReviewLifecycleResult.Failure ->
                    return LifecycleApplyResult.RecoveryRequired("Documentation changed but lifecycle commit failed.")
            }
            transaction = codec.transaction(
                bundle, aggregate.metadata.generation, resultSha, receipt.receiptId,
                ReviewApplyTransactionPhase.CONTROL_COMMITTED,
            )
            lifecycles.updateTransaction(transaction)
            LifecycleApplyResult.Applied(receipt, committed.metadata.generation)
        } catch (error: Exception) {
            LifecycleApplyResult.RecoveryRequired(error.message ?: "Apply transaction did not complete.")
        }
    }

    private fun markRecoveryRequired(
        current: ReviewLifecycleMetadata,
        message: String,
    ): LifecycleApplyResult {
        if (current.state != ReviewLifecycleState.RECOVERY_REQUIRED) {
            lifecycles.transition(current, ReviewLifecycleState.RECOVERY_REQUIRED)
        }
        return LifecycleApplyResult.RecoveryRequired(message)
    }
}

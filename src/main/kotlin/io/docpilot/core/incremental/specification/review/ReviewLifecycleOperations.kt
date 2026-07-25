package io.docpilot.core.incremental.specification.review

public enum class LifecycleDocumentationRelation {
    MATCHES_INPUT,
    MATCHES_RESULT,
    OTHER,
    NOT_PROVIDED,
}

public enum class LifecycleOperationAction { RECOVER, SUPERSEDE, ARCHIVE }

public enum class RecoveryDisposition {
    ROLL_FORWARD_APPLIED,
    ROLL_BACK_ACTIVE,
    ALREADY_APPLIED,
    BLOCK_RECOVERY_REQUIRED,
}

public data class ReviewLifecycleStatus(
    public val projectId: String,
    public val proposalId: String,
    public val bundlePayloadSha256: String,
    public val generation: Long,
    public val state: ReviewLifecycleState,
    public val transactionId: String?,
    public val transactionPhase: ReviewApplyTransactionPhase?,
    public val receiptId: String?,
    public val resultDocumentationSha256: String?,
    public val documentationRelation: LifecycleDocumentationRelation,
)

public data class ReviewLifecycleOperationPlan(
    public val planFormatVersion: Int = 1,
    public val action: LifecycleOperationAction,
    public val projectId: String,
    public val proposalId: String,
    public val observedBundlePayloadSha256: String,
    public val observedLifecycleGeneration: Long,
    public val observedLifecycleState: ReviewLifecycleState,
    public val expectedResultState: ReviewLifecycleState,
    public val transactionId: String? = null,
    public val receiptId: String? = null,
    public val replacementProposalId: String? = null,
    public val recoveryDisposition: RecoveryDisposition? = null,
    public val documentInputSha256: String? = null,
    public val documentResultSha256: String? = null,
    public val planSha256: String,
)

public enum class LifecycleOperationFailure {
    NOT_FOUND,
    INVALID,
    CONFLICT,
    BLOCKED,
    RECOVERY_REQUIRED,
    STORAGE_FAILURE,
}

public sealed interface LifecycleStatusResult {
    public data class Available(public val status: ReviewLifecycleStatus) : LifecycleStatusResult
    public data class Failure(
        public val reason: LifecycleOperationFailure,
        public val message: String,
    ) : LifecycleStatusResult
}

public sealed interface LifecycleVerificationResult {
    public data class Verified(public val status: ReviewLifecycleStatus) : LifecycleVerificationResult
    public data class Invalid(public val message: String) : LifecycleVerificationResult
}

public sealed interface LifecycleOperationPlanResult {
    public data class Ready(public val plan: ReviewLifecycleOperationPlan) : LifecycleOperationPlanResult
    public data class NoChange(public val plan: ReviewLifecycleOperationPlan) : LifecycleOperationPlanResult
    public data class Blocked(
        public val plan: ReviewLifecycleOperationPlan?,
        public val reason: LifecycleOperationFailure,
        public val message: String,
    ) : LifecycleOperationPlanResult
    public data class Failure(
        public val reason: LifecycleOperationFailure,
        public val message: String,
    ) : LifecycleOperationPlanResult
}

public sealed interface LifecycleOperationResult {
    public data class Changed(
        public val plan: ReviewLifecycleOperationPlan,
        public val aggregate: ReviewLifecycleAggregate,
    ) : LifecycleOperationResult
    public data class NoChange(
        public val plan: ReviewLifecycleOperationPlan,
        public val aggregate: ReviewLifecycleAggregate,
    ) : LifecycleOperationResult
    public data class Failure(
        public val reason: LifecycleOperationFailure,
        public val message: String,
        public val plan: ReviewLifecycleOperationPlan? = null,
    ) : LifecycleOperationResult
}

public data class LifecycleStatusRequest(
    public val expectedProjectId: String,
    public val proposalId: String,
    public val documentation: DocumentationResource? = null,
)

public data class LifecycleOperationRequest(
    public val expectedProjectId: String,
    public val proposalId: String,
    public val documentation: DocumentationResource? = null,
    public val replacementProposalId: String? = null,
)

public data class ConfirmLifecycleOperationRequest(
    public val operation: LifecycleOperationRequest,
    public val expectedPlanSha256: String? = null,
)

public interface ReviewLifecycleOperations {
    public fun status(request: LifecycleStatusRequest): LifecycleStatusResult
    public fun verify(request: LifecycleStatusRequest): LifecycleVerificationResult
    public fun planRecovery(request: LifecycleOperationRequest): LifecycleOperationPlanResult
    public fun confirmRecovery(request: ConfirmLifecycleOperationRequest): LifecycleOperationResult
    public fun planSupersede(request: LifecycleOperationRequest): LifecycleOperationPlanResult
    public fun confirmSupersede(request: ConfirmLifecycleOperationRequest): LifecycleOperationResult
    public fun planArchive(request: LifecycleOperationRequest): LifecycleOperationPlanResult
    public fun confirmArchive(request: ConfirmLifecycleOperationRequest): LifecycleOperationResult
}

public class DefaultReviewLifecycleOperations(
    private val bundles: ReviewBundleRepository,
    private val lifecycles: ReviewLifecycleRepository,
    private val codec: ReviewLifecycleCodec = ReviewLifecycleCodec(),
    private val service: ReviewLifecycleService = ReviewLifecycleService(lifecycles, codec),
) : ReviewLifecycleOperations {
    override fun status(request: LifecycleStatusRequest): LifecycleStatusResult {
        val context = load(request.expectedProjectId, request.proposalId)
        if (context is ContextResult.Failure) return LifecycleStatusResult.Failure(context.reason, context.message)
        context as ContextResult.Valid
        val transaction = if (context.aggregate.metadata.activeTransactionId != null) {
            when (val loaded = lifecycles.loadTransaction(request.proposalId)) {
                is ReviewLifecycleResult.Success -> loaded.value.first
                is ReviewLifecycleResult.Failure -> null
            }
        } else null
        return LifecycleStatusResult.Available(
            status(context, transaction, request.documentation),
        )
    }

    override fun verify(request: LifecycleStatusRequest): LifecycleVerificationResult {
        val context = load(request.expectedProjectId, request.proposalId)
        if (context is ContextResult.Failure) return LifecycleVerificationResult.Invalid(context.message)
        context as ContextResult.Valid
        val verified = service.verifyOffline(
            request.expectedProjectId,
            request.proposalId,
            context.bundle.integrity.payloadSha256,
        )
        if (verified is ReviewAuditVerificationResult.Invalid) {
            return LifecycleVerificationResult.Invalid(verified.message)
        }
        val transaction = if (context.aggregate.metadata.activeTransactionId != null) {
            when (val loaded = lifecycles.loadTransaction(request.proposalId)) {
                is ReviewLifecycleResult.Success -> {
                    val (journal, receipt) = loaded.value
                    if (!codec.verify(journal) || !codec.verify(receipt) ||
                        journal.transactionId != context.aggregate.metadata.activeTransactionId ||
                        journal.projectId != request.expectedProjectId ||
                        journal.proposalId != request.proposalId ||
                        journal.bundlePayloadSha256 != context.bundle.integrity.payloadSha256 ||
                        journal.receiptId != receipt.receiptId
                    ) {
                        return LifecycleVerificationResult.Invalid("Transaction evidence does not bind the lifecycle.")
                    }
                    journal
                }
                is ReviewLifecycleResult.Failure ->
                    return LifecycleVerificationResult.Invalid("Active transaction evidence is missing or invalid.")
            }
        } else null
        return LifecycleVerificationResult.Verified(status(context, transaction, request.documentation))
    }

    override fun planRecovery(request: LifecycleOperationRequest): LifecycleOperationPlanResult {
        val documentation = request.documentation
            ?: return LifecycleOperationPlanResult.Failure(
                LifecycleOperationFailure.INVALID,
                "Recovery requires a documentation resource.",
            )
        val context = load(request.expectedProjectId, request.proposalId)
        if (context is ContextResult.Failure) return context.asPlanFailure()
        context as ContextResult.Valid
        val metadata = context.aggregate.metadata
        if (metadata.state == ReviewLifecycleState.APPLIED) {
            val receipt = context.aggregate.receipt
                ?: return blocked(null, LifecycleOperationFailure.INVALID, "Applied lifecycle has no receipt.")
            val plan = plan(
                LifecycleOperationAction.RECOVER, context, ReviewLifecycleState.APPLIED,
                receiptId = receipt.receiptId,
                recoveryDisposition = if (codec.sha256(documentation.read()) == receipt.resultDocumentationSha256) {
                    RecoveryDisposition.ALREADY_APPLIED
                } else RecoveryDisposition.BLOCK_RECOVERY_REQUIRED,
                documentInputSha256 = receipt.reviewedDocumentationSha256,
                documentResultSha256 = receipt.resultDocumentationSha256,
            )
            return if (plan.recoveryDisposition == RecoveryDisposition.ALREADY_APPLIED) {
                LifecycleOperationPlanResult.NoChange(plan)
            } else blocked(plan, LifecycleOperationFailure.CONFLICT, "Documentation changed after recorded apply.")
        }
        if (metadata.state != ReviewLifecycleState.APPLYING) {
            return blocked(
                plan(
                    LifecycleOperationAction.RECOVER, context, metadata.state,
                    recoveryDisposition = RecoveryDisposition.BLOCK_RECOVERY_REQUIRED,
                ),
                if (metadata.state == ReviewLifecycleState.RECOVERY_REQUIRED) {
                    LifecycleOperationFailure.RECOVERY_REQUIRED
                } else LifecycleOperationFailure.BLOCKED,
                "No exact recoverable apply transaction exists.",
            )
        }
        val artifacts = when (val loaded = lifecycles.loadTransaction(request.proposalId)) {
            is ReviewLifecycleResult.Success -> loaded.value
            is ReviewLifecycleResult.Failure ->
                return blocked(null, LifecycleOperationFailure.INVALID, loaded.message)
        }
        val (transaction, receipt) = artifacts
        if (!codec.verify(transaction) || !codec.verify(receipt) ||
            transaction.transactionId != metadata.activeTransactionId ||
            transaction.bundlePayloadSha256 != context.bundle.integrity.payloadSha256 ||
            transaction.receiptId != receipt.receiptId
        ) {
            return blocked(null, LifecycleOperationFailure.INVALID, "Transaction evidence does not bind the lifecycle.")
        }
        val actual = codec.sha256(documentation.read())
        val disposition = when {
            actual == transaction.resultDocumentationSha256 -> RecoveryDisposition.ROLL_FORWARD_APPLIED
            actual == transaction.inputDocumentationSha256 &&
                transaction.phase == ReviewApplyTransactionPhase.PREPARED -> RecoveryDisposition.ROLL_BACK_ACTIVE
            else -> RecoveryDisposition.BLOCK_RECOVERY_REQUIRED
        }
        val resultState = when (disposition) {
            RecoveryDisposition.ROLL_FORWARD_APPLIED -> ReviewLifecycleState.APPLIED
            RecoveryDisposition.ROLL_BACK_ACTIVE -> ReviewLifecycleState.ACTIVE
            else -> ReviewLifecycleState.RECOVERY_REQUIRED
        }
        val plan = plan(
            LifecycleOperationAction.RECOVER, context, resultState,
            transaction.transactionId, receipt.receiptId, recoveryDisposition = disposition,
            documentInputSha256 = transaction.inputDocumentationSha256,
            documentResultSha256 = transaction.resultDocumentationSha256,
        )
        return if (disposition == RecoveryDisposition.BLOCK_RECOVERY_REQUIRED) {
            blocked(plan, LifecycleOperationFailure.RECOVERY_REQUIRED, "Documentation matches neither exact transaction boundary.")
        } else LifecycleOperationPlanResult.Ready(plan)
    }

    override fun confirmRecovery(request: ConfirmLifecycleOperationRequest): LifecycleOperationResult =
        confirm(request, ::planRecovery) { plan, context ->
            when (plan.recoveryDisposition) {
                RecoveryDisposition.ALREADY_APPLIED ->
                    LifecycleOperationResult.NoChange(plan, context.aggregate)
                RecoveryDisposition.ROLL_FORWARD_APPLIED -> {
                    val receipt = when (val loaded = lifecycles.loadTransaction(plan.proposalId)) {
                        is ReviewLifecycleResult.Success -> loaded.value.second
                        is ReviewLifecycleResult.Failure ->
                            return@confirm LifecycleOperationResult.Failure(
                                LifecycleOperationFailure.INVALID, loaded.message, plan,
                            )
                    }
                    mutation(plan, lifecycles.commitApply(context.aggregate.metadata, receipt))
                }
                RecoveryDisposition.ROLL_BACK_ACTIVE ->
                    mutation(plan, lifecycles.transition(context.aggregate.metadata, ReviewLifecycleState.ACTIVE))
                else -> LifecycleOperationResult.Failure(
                    LifecycleOperationFailure.RECOVERY_REQUIRED,
                    "Recovery plan is not executable.",
                    plan,
                )
            }
        }

    override fun planSupersede(request: LifecycleOperationRequest): LifecycleOperationPlanResult {
        val replacement = request.replacementProposalId
            ?: return LifecycleOperationPlanResult.Failure(
                LifecycleOperationFailure.INVALID,
                "Supersede requires a replacement proposal id.",
            )
        if (!replacement.matches(Regex("review:[0-9a-f]{64}")) || replacement == request.proposalId) {
            return LifecycleOperationPlanResult.Failure(
                LifecycleOperationFailure.INVALID,
                "Replacement proposal id is invalid.",
            )
        }
        val context = load(request.expectedProjectId, request.proposalId)
        if (context is ContextResult.Failure) return context.asPlanFailure()
        context as ContextResult.Valid
        if (context.aggregate.metadata.state != ReviewLifecycleState.ACTIVE) {
            return blocked(null, LifecycleOperationFailure.BLOCKED, "Only an active review can be superseded.")
        }
        return LifecycleOperationPlanResult.Ready(
            plan(
                LifecycleOperationAction.SUPERSEDE,
                context,
                ReviewLifecycleState.SUPERSEDED,
                replacementProposalId = replacement,
            ),
        )
    }

    override fun confirmSupersede(request: ConfirmLifecycleOperationRequest): LifecycleOperationResult =
        confirm(request, ::planSupersede) { plan, context ->
            mutation(
                plan,
                service.supersede(context.aggregate.metadata, requireNotNull(plan.replacementProposalId)),
            )
        }

    override fun planArchive(request: LifecycleOperationRequest): LifecycleOperationPlanResult {
        val context = load(request.expectedProjectId, request.proposalId)
        if (context is ContextResult.Failure) return context.asPlanFailure()
        context as ContextResult.Valid
        if (context.aggregate.metadata.state in setOf(
                ReviewLifecycleState.APPLYING,
                ReviewLifecycleState.RECOVERY_REQUIRED,
                ReviewLifecycleState.ARCHIVED,
            )
        ) {
            return blocked(null, LifecycleOperationFailure.BLOCKED, "Lifecycle cannot be archived in its current state.")
        }
        return LifecycleOperationPlanResult.Ready(
            plan(LifecycleOperationAction.ARCHIVE, context, ReviewLifecycleState.ARCHIVED),
        )
    }

    override fun confirmArchive(request: ConfirmLifecycleOperationRequest): LifecycleOperationResult =
        confirm(request, ::planArchive) { plan, context ->
            mutation(plan, service.archive(context.aggregate.metadata))
        }

    private fun confirm(
        request: ConfirmLifecycleOperationRequest,
        planner: (LifecycleOperationRequest) -> LifecycleOperationPlanResult,
        execute: (ReviewLifecycleOperationPlan, ContextResult.Valid) -> LifecycleOperationResult,
    ): LifecycleOperationResult {
        val planned = planner(request.operation)
        val plan = when (planned) {
            is LifecycleOperationPlanResult.Ready -> planned.plan
            is LifecycleOperationPlanResult.NoChange -> planned.plan
            is LifecycleOperationPlanResult.Blocked ->
                return LifecycleOperationResult.Failure(planned.reason, planned.message, planned.plan)
            is LifecycleOperationPlanResult.Failure ->
                return LifecycleOperationResult.Failure(planned.reason, planned.message)
        }
        val expected = request.expectedPlanSha256
        if (expected != null && expected != plan.planSha256) {
            return LifecycleOperationResult.Failure(
                LifecycleOperationFailure.CONFLICT,
                "Lifecycle operation plan changed after approval.",
                plan,
            )
        }
        val context = load(plan.projectId, plan.proposalId)
        if (context !is ContextResult.Valid ||
            context.aggregate.metadata.generation != plan.observedLifecycleGeneration ||
            context.aggregate.metadata.state != plan.observedLifecycleState ||
            context.bundle.integrity.payloadSha256 != plan.observedBundlePayloadSha256
        ) {
            return LifecycleOperationResult.Failure(
                LifecycleOperationFailure.CONFLICT,
                "Lifecycle operation boundary changed before confirmation.",
                plan,
            )
        }
        return execute(plan, context)
    }

    private fun mutation(
        plan: ReviewLifecycleOperationPlan,
        result: ReviewLifecycleResult<ReviewLifecycleAggregate>,
    ): LifecycleOperationResult = when (result) {
        is ReviewLifecycleResult.Success -> LifecycleOperationResult.Changed(plan, result.value)
        is ReviewLifecycleResult.Failure -> LifecycleOperationResult.Failure(
            result.reason.toOperationFailure(),
            result.message,
            plan,
        )
    }

    private fun load(projectId: String, proposalId: String): ContextResult {
        val bundle = when (val loaded = bundles.load(projectId, proposalId)) {
            is ReviewBundleLoadResult.Valid -> loaded.bundle
            ReviewBundleLoadResult.NotFound ->
                return ContextResult.Failure(LifecycleOperationFailure.NOT_FOUND, "Review bundle was not found.")
            is ReviewBundleLoadResult.Invalid ->
                return ContextResult.Failure(LifecycleOperationFailure.INVALID, loaded.message)
        }
        val aggregate = when (val loaded = lifecycles.load(projectId, proposalId)) {
            is ReviewLifecycleResult.Success -> loaded.value
            is ReviewLifecycleResult.Failure ->
                return ContextResult.Failure(loaded.reason.toOperationFailure(), loaded.message)
        }
        if (aggregate.metadata.observedBundlePayloadSha256 != bundle.integrity.payloadSha256) {
            return ContextResult.Failure(
                LifecycleOperationFailure.CONFLICT,
                "Lifecycle does not observe the selected review bundle payload.",
            )
        }
        return ContextResult.Valid(bundle, aggregate)
    }

    private fun status(
        context: ContextResult.Valid,
        transaction: ReviewApplyTransaction?,
        documentation: DocumentationResource?,
    ): ReviewLifecycleStatus {
        val receipt = context.aggregate.receipt
        val relation = if (documentation == null) {
            LifecycleDocumentationRelation.NOT_PROVIDED
        } else {
            val actual = codec.sha256(documentation.read())
            when {
                receipt != null && actual == receipt.resultDocumentationSha256 ->
                    LifecycleDocumentationRelation.MATCHES_RESULT
                transaction != null && actual == transaction.resultDocumentationSha256 ->
                    LifecycleDocumentationRelation.MATCHES_RESULT
                transaction != null && actual == transaction.inputDocumentationSha256 ->
                    LifecycleDocumentationRelation.MATCHES_INPUT
                actual == context.bundle.proposal.reviewedDocumentationSha256 ->
                    LifecycleDocumentationRelation.MATCHES_INPUT
                else -> LifecycleDocumentationRelation.OTHER
            }
        }
        return ReviewLifecycleStatus(
            context.bundle.projectIdentity.projectId,
            context.bundle.proposalId,
            context.bundle.integrity.payloadSha256,
            context.aggregate.metadata.generation,
            context.aggregate.metadata.state,
            transaction?.transactionId ?: context.aggregate.metadata.activeTransactionId,
            transaction?.phase,
            receipt?.receiptId ?: context.aggregate.metadata.applyReceiptId,
            receipt?.resultDocumentationSha256,
            relation,
        )
    }

    private fun plan(
        action: LifecycleOperationAction,
        context: ContextResult.Valid,
        expectedResultState: ReviewLifecycleState,
        transactionId: String? = null,
        receiptId: String? = null,
        replacementProposalId: String? = null,
        recoveryDisposition: RecoveryDisposition? = null,
        documentInputSha256: String? = null,
        documentResultSha256: String? = null,
    ): ReviewLifecycleOperationPlan {
        val metadata = context.aggregate.metadata
        val fields = listOf(
            "1", action.name, metadata.projectId, metadata.proposalId,
            context.bundle.integrity.payloadSha256, metadata.generation.toString(), metadata.state.name,
            expectedResultState.name, transactionId.orEmpty(), receiptId.orEmpty(),
            replacementProposalId.orEmpty(), recoveryDisposition?.name.orEmpty(),
            documentInputSha256.orEmpty(), documentResultSha256.orEmpty(),
        )
        return ReviewLifecycleOperationPlan(
            action = action,
            projectId = metadata.projectId,
            proposalId = metadata.proposalId,
            observedBundlePayloadSha256 = context.bundle.integrity.payloadSha256,
            observedLifecycleGeneration = metadata.generation,
            observedLifecycleState = metadata.state,
            expectedResultState = expectedResultState,
            transactionId = transactionId,
            receiptId = receiptId,
            replacementProposalId = replacementProposalId,
            recoveryDisposition = recoveryDisposition,
            documentInputSha256 = documentInputSha256,
            documentResultSha256 = documentResultSha256,
            planSha256 = codec.sha256(fields.joinToString("\u001f")),
        )
    }

    private fun blocked(
        plan: ReviewLifecycleOperationPlan?,
        reason: LifecycleOperationFailure,
        message: String,
    ): LifecycleOperationPlanResult.Blocked = LifecycleOperationPlanResult.Blocked(plan, reason, message)

    private sealed interface ContextResult {
        data class Valid(
            val bundle: StoredReviewBundle,
            val aggregate: ReviewLifecycleAggregate,
        ) : ContextResult
        data class Failure(
            val reason: LifecycleOperationFailure,
            val message: String,
        ) : ContextResult {
            fun asPlanFailure(): LifecycleOperationPlanResult.Failure =
                LifecycleOperationPlanResult.Failure(reason, message)
        }
    }

    private fun ReviewLifecycleFailure.toOperationFailure(): LifecycleOperationFailure = when (this) {
        ReviewLifecycleFailure.NOT_FOUND -> LifecycleOperationFailure.NOT_FOUND
        ReviewLifecycleFailure.INVALID -> LifecycleOperationFailure.INVALID
        ReviewLifecycleFailure.CONFLICT -> LifecycleOperationFailure.CONFLICT
        ReviewLifecycleFailure.IO_FAILURE -> LifecycleOperationFailure.STORAGE_FAILURE
    }
}

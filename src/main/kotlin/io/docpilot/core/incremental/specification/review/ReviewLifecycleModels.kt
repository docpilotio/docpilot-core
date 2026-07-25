package io.docpilot.core.incremental.specification.review

public object ReviewLifecycleFormat {
    public const val CURRENT_VERSION: Int = 1
    public const val DEFAULT_DIRECTORY: String = ".docpilot/reviews/control"
}

public enum class ReviewLifecycleState {
    ACTIVE,
    APPLYING,
    APPLIED,
    SUPERSEDED,
    ARCHIVED,
    RECOVERY_REQUIRED,
}

public data class ReviewLifecycleIntegrity(public val payloadSha256: String)

public data class ReviewLifecycleMetadata(
    public val formatVersion: Int = ReviewLifecycleFormat.CURRENT_VERSION,
    public val projectId: String,
    public val proposalId: String,
    public val reviewBundleFormatVersion: Int = ReviewBundleFormat.CURRENT_VERSION,
    public val observedBundlePayloadSha256: String,
    public val generation: Long,
    public val state: ReviewLifecycleState,
    public val activeTransactionId: String? = null,
    public val applyReceiptId: String? = null,
    public val supersededByProposalId: String? = null,
    public val archivedFrom: ReviewLifecycleState? = null,
    public val integrity: ReviewLifecycleIntegrity,
)

public data class ApplyReceiptOperation(
    public val targetId: String,
    public val disposition: DocumentationReviewDisposition,
)

public data class ApplyReceipt(
    public val formatVersion: Int = ReviewLifecycleFormat.CURRENT_VERSION,
    public val receiptId: String,
    public val projectId: String,
    public val proposalId: String,
    public val bundlePayloadSha256: String,
    public val reviewedDocumentationSha256: String,
    public val resultDocumentationSha256: String,
    public val acceptedTargetIds: List<String>,
    public val rejectedTargetIds: List<String>,
    public val operations: List<ApplyReceiptOperation>,
    public val integrity: ReviewLifecycleIntegrity,
)

public enum class ReviewApplyTransactionPhase {
    PREPARED,
    DOCUMENT_REPLACED,
    CONTROL_COMMITTED,
    COMPLETED,
}

public data class ReviewApplyTransaction(
    public val formatVersion: Int = ReviewLifecycleFormat.CURRENT_VERSION,
    public val transactionId: String,
    public val projectId: String,
    public val proposalId: String,
    public val expectedLifecycleGeneration: Long,
    public val bundlePayloadSha256: String,
    public val inputDocumentationSha256: String,
    public val resultDocumentationSha256: String,
    public val receiptId: String,
    public val phase: ReviewApplyTransactionPhase,
    public val integrity: ReviewLifecycleIntegrity,
)

public data class ReviewLifecycleAggregate(
    public val metadata: ReviewLifecycleMetadata,
    public val receipt: ApplyReceipt? = null,
)

public enum class ReviewLifecycleFailure {
    NOT_FOUND,
    INVALID,
    CONFLICT,
    IO_FAILURE,
}

public sealed interface ReviewLifecycleResult<out T> {
    public data class Success<T>(public val value: T) : ReviewLifecycleResult<T>
    public data class Failure(
        public val reason: ReviewLifecycleFailure,
        public val message: String,
    ) : ReviewLifecycleResult<Nothing>
}

package io.docpilot.core.incremental.specification.review

public object ReviewBundleFormat {
    public const val CURRENT_VERSION: Int = 1
    public const val DEFAULT_DIRECTORY: String = ".docpilot/reviews"
}

public data class ReviewBundleProjectIdentity(public val projectId: String) {
    init { require(projectId.isNotBlank()) { "Review bundle project id must not be blank." } }
}

public data class ReviewSpecificationIdentity(
    public val previousSpecificationSha256: String,
    public val currentSpecificationSha256: String,
) {
    init {
        require(previousSpecificationSha256.isSha256()) { "Previous specification SHA-256 is invalid." }
        require(currentSpecificationSha256.isSha256()) { "Current specification SHA-256 is invalid." }
    }
}

public data class ReviewBundleIntegrity(
    public val algorithm: String = "SHA-256",
    public val payloadSha256: String,
) {
    init {
        require(algorithm == "SHA-256") { "Unsupported review bundle integrity algorithm: $algorithm" }
        require(payloadSha256.isSha256()) { "Review bundle SHA-256 is invalid." }
    }
}

public data class StoredReviewBundle(
    public val formatVersion: Int,
    public val projectIdentity: ReviewBundleProjectIdentity,
    public val proposalId: String,
    public val specificationIdentity: ReviewSpecificationIdentity,
    public val proposal: DocumentationReviewProposal,
    public val decisions: List<DocumentationReviewDecision>,
    public val integrity: ReviewBundleIntegrity,
) {
    init {
        require(formatVersion == ReviewBundleFormat.CURRENT_VERSION) {
            "Unsupported review bundle format version: $formatVersion"
        }
        require(proposalId.matches(Regex("review:[0-9a-f]{64}"))) { "Review proposal id is invalid." }
        require(decisions == decisions.sortedBy { it.targetId }) { "Review decisions must use target-id order." }
        require(decisions.map { it.targetId }.distinct().size == decisions.size) {
            "Review decisions must have unique target ids."
        }
        val entryIds = proposal.entries.map { it.targetId }.toSet()
        require(decisions.all { it.targetId in entryIds }) { "Review decision targets must exist in the proposal." }
    }
}

public enum class ReviewBundleValidationFailure {
    CORRUPTED,
    UNSUPPORTED_VERSION,
    PROJECT_MISMATCH,
    PROPOSAL_ID_MISMATCH,
    INTEGRITY_MISMATCH,
    INVALID_PROPOSAL,
    INVALID_DECISIONS,
    SPECIFICATION_IDENTITY_MISMATCH,
}

public sealed interface ReviewBundleLoadResult {
    public data object NotFound : ReviewBundleLoadResult
    public data class Valid(public val bundle: StoredReviewBundle) : ReviewBundleLoadResult
    public data class Invalid(
        public val reason: ReviewBundleValidationFailure,
        public val message: String,
    ) : ReviewBundleLoadResult
}

public sealed interface ReviewBundleSaveResult {
    public data class Saved(public val bundle: StoredReviewBundle) : ReviewBundleSaveResult
    public data object AlreadyExists : ReviewBundleSaveResult
    public data class Conflict(
        public val expectedPayloadSha256: String,
        public val actualPayloadSha256: String,
    ) : ReviewBundleSaveResult
    public data class Invalid(public val message: String) : ReviewBundleSaveResult
    public data class Failed(public val message: String) : ReviewBundleSaveResult
}

internal fun String.isSha256(): Boolean = matches(Regex("[0-9a-f]{64}"))

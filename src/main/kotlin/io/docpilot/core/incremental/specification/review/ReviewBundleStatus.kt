package io.docpilot.core.incremental.specification.review

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

public enum class ReviewBundleStatus {
    PENDING_REVIEW,
    READY_TO_APPLY,
    STALE_DOCUMENTATION,
}

public enum class DocumentationFreshness { MATCH, STALE, NOT_CHECKED }

public data class ReviewBundleStatusResult(
    public val status: ReviewBundleStatus,
    public val proposalComplete: Boolean,
    public val entryCount: Int,
    public val acceptedCount: Int,
    public val rejectedCount: Int,
    public val pendingTargetIds: List<String>,
    public val missingPatchTargetIds: List<String>,
    public val documentationFreshness: DocumentationFreshness,
)

public class ReviewBundleStatusEvaluator {
    public fun evaluate(
        bundle: StoredReviewBundle,
        documentation: String? = null,
    ): ReviewBundleStatusResult {
        val decisionById = bundle.decisions.associateBy { it.targetId }
        val pending = bundle.proposal.entries.map { it.targetId }.filterNot(decisionById::containsKey).sorted()
        val freshness = when {
            documentation == null -> DocumentationFreshness.NOT_CHECKED
            sha256(documentation) == bundle.proposal.reviewedDocumentationSha256 -> DocumentationFreshness.MATCH
            else -> DocumentationFreshness.STALE
        }
        val status = when {
            freshness == DocumentationFreshness.STALE -> ReviewBundleStatus.STALE_DOCUMENTATION
            !bundle.proposal.isComplete || pending.isNotEmpty() -> ReviewBundleStatus.PENDING_REVIEW
            else -> ReviewBundleStatus.READY_TO_APPLY
        }
        return ReviewBundleStatusResult(
            status,
            bundle.proposal.isComplete,
            bundle.proposal.entries.size,
            bundle.decisions.count { it.disposition == DocumentationReviewDisposition.ACCEPTED },
            bundle.decisions.count { it.disposition == DocumentationReviewDisposition.REJECTED },
            pending,
            bundle.proposal.missingPatchTargetIds,
            freshness,
        )
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

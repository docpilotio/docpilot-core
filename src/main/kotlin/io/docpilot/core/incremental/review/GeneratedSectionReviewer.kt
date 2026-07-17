package io.docpilot.core.incremental.review

fun interface GeneratedSectionReviewer {
    fun review(request: SectionReviewRequest, modelId: io.docpilot.core.model.ai.AiModelId): SectionReviewResult
}

fun interface DeterministicSectionValidator {
    fun validate(request: SectionReviewRequest): List<ReviewIssue>
}

fun interface ReviewDecisionPolicy {
    fun decide(issues: List<ReviewIssue>): ReviewDecision
}

class DefaultReviewDecisionPolicy : ReviewDecisionPolicy {
    override fun decide(issues: List<ReviewIssue>): ReviewDecision = when {
        issues.any { it.severity == ReviewIssueSeverity.ERROR } -> ReviewDecision.REJECTED
        issues.any { it.severity == ReviewIssueSeverity.WARNING } -> ReviewDecision.ACCEPTED_WITH_WARNINGS
        else -> ReviewDecision.ACCEPTED
    }
}

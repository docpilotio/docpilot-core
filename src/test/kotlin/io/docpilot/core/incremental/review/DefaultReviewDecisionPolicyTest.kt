package io.docpilot.core.incremental.review

import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultReviewDecisionPolicyTest {
    private val policy = DefaultReviewDecisionPolicy()

    @Test
    fun `error rejects regardless of score`() {
        assertEquals(
            ReviewDecision.REJECTED,
            policy.decide(listOf(issue(ReviewIssueSeverity.ERROR))),
        )
    }

    @Test
    fun `warning accepts with warnings`() {
        assertEquals(
            ReviewDecision.ACCEPTED_WITH_WARNINGS,
            policy.decide(listOf(issue(ReviewIssueSeverity.WARNING))),
        )
    }

    @Test
    fun `no issues accepts`() {
        assertEquals(ReviewDecision.ACCEPTED, policy.decide(emptyList()))
    }

    private fun issue(severity: ReviewIssueSeverity) = ReviewIssue(
        severity,
        ReviewIssueType.UNSUPPORTED_CLAIM,
        "message",
    )
}

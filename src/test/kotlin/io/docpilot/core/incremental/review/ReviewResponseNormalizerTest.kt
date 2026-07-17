package io.docpilot.core.incremental.review

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReviewResponseNormalizerTest {
    @Test
    fun `normalizes structured review response and sorts issues`() {
        val result = DefaultReviewResponseNormalizer().normalize(
            """
            DECISION: REJECTED
            SCORES: evidenceSupport=20;consistency=70;completeness=80;contractCompliance=100;overall=55
            ISSUE: WARNING|INCOMPLETE_COVERAGE|Missing failure behavior|-
            ISSUE: ERROR|UNSUPPORTED_CLAIM|Atomic behavior is not supported|ev-2,ev-1
            FEEDBACK: Remove the unsupported atomicity claim.
            """.trimIndent(),
        )
        assertEquals(ReviewDecision.REJECTED, result.decision)
        assertEquals(2, result.issues.size)
        assertEquals(ReviewIssueSeverity.WARNING, result.issues.first().severity)
        assertEquals(listOf("ev-1", "ev-2"), result.issues.last().evidenceIds)
    }

    @Test
    fun `rejects out of range score`() {
        assertFailsWith<IllegalArgumentException> {
            DefaultReviewResponseNormalizer().normalize(
                """
                DECISION: ACCEPTED
                SCORES: evidenceSupport=101;consistency=100;completeness=100;contractCompliance=100;overall=100
                FEEDBACK: -
                """.trimIndent(),
            )
        }
    }
}

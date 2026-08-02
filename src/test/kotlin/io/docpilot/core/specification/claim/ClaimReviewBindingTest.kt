package io.docpilot.core.specification.claim

import io.docpilot.core.incremental.specification.review.DocumentationReviewDecision
import io.docpilot.core.incremental.specification.review.DocumentationReviewDisposition
import kotlin.test.Test
import kotlin.test.assertEquals

class ClaimReviewBindingTest {
    private val specification = ClaimFixtures.specification()
    private val claim = ClaimFactory.deterministic(
        specification, ClaimSubject(ClaimSubjectKind.ENTITY, "component:sample"), "sample-assertion", "Sample.",
        evidenceRefs = setOf(ClaimFixtures.highEvidence.id),
    )

    @Test
    fun `decisionTargetId returns the claim id value`() {
        assertEquals(claim.id.value, ClaimReviewBinding.decisionTargetId(claim))
    }

    @Test
    fun `claim id is a valid review decision targetId with no format change`() {
        val decision = DocumentationReviewDecision(
            targetId = ClaimReviewBinding.decisionTargetId(claim),
            disposition = DocumentationReviewDisposition.ACCEPTED,
        )
        assertEquals(claim.id.value, decision.targetId)
    }
}

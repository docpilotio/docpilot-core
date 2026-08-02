package io.docpilot.core.specification.claim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClaimEvidenceBinderTest {
    private val specification = ClaimFixtures.specification()

    @Test
    fun `unresolvedTargets returns exactly the non-resolving subset`() {
        val refs = setOf(ClaimFixtures.highEvidence.id, ClaimFixtures.contract.id, "garbage:ref")
        val unresolved = ClaimEvidenceBinder.unresolvedTargets(specification, refs)
        assertEquals(setOf("garbage:ref"), unresolved)
    }

    @Test
    fun `resolveRefs throws naming the missing refs`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            ClaimEvidenceBinder.resolveRefs(specification, setOf("garbage:ref"), emptySet(), "Claim")
        }
        assertTrue(exception.message.orEmpty().contains("garbage:ref"))
    }
}

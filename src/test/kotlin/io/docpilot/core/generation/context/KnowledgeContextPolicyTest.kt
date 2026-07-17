package io.docpilot.core.generation.context

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KnowledgeContextPolicyTest {

    @Test
    fun `defaults are conservative and positive`() {
        val policy = KnowledgeContextPolicy()

        assertEquals(20, policy.maxNodes)
        assertEquals(40, policy.maxEdges)
        assertEquals(40, policy.maxEvidence)
        assertEquals(500, policy.maxEvidenceSummaryCharacters)
        assertEquals(24_000, policy.maxCharacters)
    }

    @Test
    fun `all limits must be greater than zero`() {
        assertFailsWith<IllegalArgumentException> {
            KnowledgeContextPolicy(maxNodes = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            KnowledgeContextPolicy(maxEdges = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            KnowledgeContextPolicy(maxEvidence = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            KnowledgeContextPolicy(
                maxEvidenceSummaryCharacters = 0,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            KnowledgeContextPolicy(maxCharacters = 0)
        }
    }
}

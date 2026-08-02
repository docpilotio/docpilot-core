package io.docpilot.core.specification.finding

import io.docpilot.core.specification.claim.ClaimFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class FindingFactoryTest {
    private val specification = ClaimFixtures.specification()
    private val subjectStableId = "component:sample"

    private fun create(
        semanticKey: String = "sample-finding",
        category: String = "COVERAGE_GAP",
        severity: FindingSeverity = FindingSeverity.MEDIUM,
        summary: String = "Sample is missing a description.",
        evidenceRefs: Set<String> = setOf(ClaimFixtures.highEvidence.id),
        unresolvedRefs: Set<String> = emptySet(),
    ) = FindingFactory.create(specification, subjectStableId, semanticKey, category, severity, summary, evidenceRefs, unresolvedRefs)

    @Test
    fun `evidence-only ref succeeds and id matches FindingIdentity`() {
        val finding = create()
        assertEquals(FindingIdentity.of(subjectStableId, "COVERAGE_GAP", "sample-finding"), finding.id.value)
    }

    @Test
    fun `contract-only ref succeeds`() {
        val finding = create(evidenceRefs = setOf(ClaimFixtures.contract.id))
        assertEquals(setOf(ClaimFixtures.contract.id), finding.evidenceRefs)
    }

    @Test
    fun `empty evidenceRefs is rejected`() {
        assertFailsWith<IllegalArgumentException> { create(evidenceRefs = emptySet()) }
    }

    @Test
    fun `ref not resolving to Evidence or Contract is rejected`() {
        assertFailsWith<IllegalArgumentException> { create(evidenceRefs = setOf("evidence:missing")) }
    }

    @Test
    fun `only low-confidence evidence is rejected`() {
        assertFailsWith<IllegalArgumentException> { create(evidenceRefs = setOf(ClaimFixtures.lowEvidence.id)) }
    }

    @Test
    fun `unresolvedRefs not present in specification unresolved is rejected`() {
        assertFailsWith<IllegalArgumentException> { create(unresolvedRefs = setOf("unresolved:missing-entirely")) }
    }

    @Test
    fun `blank subjectStableId is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            FindingFactory.create(specification, " ", "key", "CATEGORY", FindingSeverity.LOW, "Summary.", setOf(ClaimFixtures.highEvidence.id))
        }
    }

    @Test
    fun `blank semanticKey is rejected`() {
        assertFailsWith<IllegalArgumentException> { create(semanticKey = " ") }
    }

    @Test
    fun `blank category is rejected`() {
        assertFailsWith<IllegalArgumentException> { create(category = " ") }
    }

    @Test
    fun `blank summary is rejected`() {
        assertFailsWith<IllegalArgumentException> { create(summary = " ") }
    }

    @Test
    fun `identity is independent of severity and summary`() {
        val first = create(severity = FindingSeverity.LOW, summary = "Sample is missing a description.")
        val second = create(severity = FindingSeverity.CRITICAL, summary = "Sample really needs a description, urgently.")
        assertEquals(first.id, second.id)
    }

    @Test
    fun `different category or semanticKey yields different id`() {
        val base = create()
        val differentCategory = create(category = "TRACEABILITY_GAP")
        val differentKey = create(semanticKey = "other-finding")
        assertNotEquals(base.id, differentCategory.id)
        assertNotEquals(base.id, differentKey.id)
    }

    @Test
    fun `summary restating one of its own evidenceRefs is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            create(summary = "This relies on ${ClaimFixtures.highEvidence.id} directly.")
        }
    }

    @Test
    fun `different severities both construct successfully for the same subject`() {
        val low = create(semanticKey = "severity-check", severity = FindingSeverity.LOW)
        val critical = create(semanticKey = "severity-check", severity = FindingSeverity.CRITICAL)
        assertEquals(FindingSeverity.LOW, low.severity)
        assertEquals(FindingSeverity.CRITICAL, critical.severity)
        assertEquals(low.id, critical.id)
    }
}

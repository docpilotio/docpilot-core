package io.docpilot.core.documentation.backlog

import io.docpilot.core.specification.claim.ClaimFixtures
import io.docpilot.core.specification.finding.FindingFactory
import io.docpilot.core.specification.finding.FindingSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProductizationRoadmapBuilderTest {
    private val specification = ClaimFixtures.specification()

    private fun finding(semanticKey: String, severity: FindingSeverity) = FindingFactory.create(
        specification, "component:sample", semanticKey, "COVERAGE_GAP", severity,
        "Finding for $semanticKey.", setOf(ClaimFixtures.highEvidence.id),
    )

    @Test
    fun `empty findings list is rejected`() {
        assertFailsWith<IllegalArgumentException> { ProductizationRoadmapBuilder.build(emptyList()) }
    }

    @Test
    fun `severity maps to the expected priority for every severity`() {
        assertEquals(BacklogPriority.P0, BacklogPriorityMapping.of(FindingSeverity.CRITICAL))
        assertEquals(BacklogPriority.P1, BacklogPriorityMapping.of(FindingSeverity.HIGH))
        assertEquals(BacklogPriority.P1, BacklogPriorityMapping.of(FindingSeverity.MEDIUM))
        assertEquals(BacklogPriority.P2, BacklogPriorityMapping.of(FindingSeverity.LOW))
        assertEquals(BacklogPriority.P2, BacklogPriorityMapping.of(FindingSeverity.INFO))
    }

    @Test
    fun `entries are ordered P0 first`() {
        val low = finding("low-issue", FindingSeverity.LOW)
        val critical = finding("critical-issue", FindingSeverity.CRITICAL)
        val high = finding("high-issue", FindingSeverity.HIGH)
        val document = ProductizationRoadmapBuilder.build(listOf(low, high, critical))
        assertEquals(
            listOf(critical.id.value, high.id.value, low.id.value),
            document.entries.map { it.findingId },
        )
    }

    @Test
    fun `each entry statement carries its own finding's evidenceRefs`() {
        val contractBacked = FindingFactory.create(
            specification, "component:sample", "contract-issue", "COVERAGE_GAP", FindingSeverity.MEDIUM,
            "Finding referencing a Contract.", setOf(ClaimFixtures.contract.id),
        )
        val document = ProductizationRoadmapBuilder.build(listOf(contractBacked))
        assertEquals(setOf(ClaimFixtures.contract.id), document.entries.single().statement.evidenceRefs)
    }
}

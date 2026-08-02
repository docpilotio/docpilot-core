package io.docpilot.core.documentation.advisory

import io.docpilot.core.specification.claim.ClaimFixtures
import io.docpilot.core.specification.finding.FindingFactory
import io.docpilot.core.specification.finding.FindingSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KnownIssuesRegisterTest {
    private val specification = ClaimFixtures.specification()

    private fun finding(semanticKey: String, severity: FindingSeverity) = FindingFactory.create(
        specification, "component:sample", semanticKey, "COVERAGE_GAP", severity,
        "Finding for $semanticKey.", setOf(ClaimFixtures.highEvidence.id),
    )

    @Test
    fun `empty findings list is rejected`() {
        assertFailsWith<IllegalArgumentException> { KnownIssuesRegisterBuilder.build(emptyList()) }
    }

    @Test
    fun `statements are ordered most severe first`() {
        val low = finding("low-issue", FindingSeverity.LOW)
        val critical = finding("critical-issue", FindingSeverity.CRITICAL)
        val document = KnownIssuesRegisterBuilder.build(listOf(low, critical))
        assertEquals(listOf(critical.id.value, low.id.value), document.findingIds)
    }

    @Test
    fun `each statement carries its own finding's evidenceRefs`() {
        val a = finding("a", FindingSeverity.HIGH)
        val b = FindingFactory.create(
            specification, "component:sample", "b-contract", "COVERAGE_GAP", FindingSeverity.MEDIUM,
            "Finding referencing a Contract.", setOf(ClaimFixtures.contract.id),
        )
        val document = KnownIssuesRegisterBuilder.build(listOf(a, b))
        val byFindingId = document.statements.associateBy { statement -> statement.text }
        assertEquals(setOf(ClaimFixtures.highEvidence.id), byFindingId.getValue(a.summary).evidenceRefs)
        assertEquals(setOf(ClaimFixtures.contract.id), byFindingId.getValue(b.summary).evidenceRefs)
        assertTrue(document.statements.all { it.label == StatementLabel.AS_IS })
    }

    @Test
    fun `render produces a table row and AS-IS statement per finding`() {
        val findings = listOf(finding("only-issue", FindingSeverity.HIGH))
        val document = KnownIssuesRegisterBuilder.build(findings)
        val markdown = KnownIssuesRegisterMarkdownRenderer.render(document, findings)
        assertTrue(markdown.contains("# Known Issues Register"))
        assertTrue(markdown.contains("| HIGH |"))
        assertTrue(markdown.contains("**[AS-IS]**"))
    }
}

package io.docpilot.core.documentation.finding

import io.docpilot.core.documentation.enrichment.DocumentationEnrichmentStatus
import io.docpilot.core.documentation.synthesis.DocumentationTier
import io.docpilot.core.documentation.synthesis.SynthesisRecord
import io.docpilot.core.documentation.synthesis.SynthesisResult
import io.docpilot.core.specification.finding.FindingSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FindingProposalTest {
    private fun result(content: String?) = SynthesisResult(
        content,
        SynthesisRecord(
            synthesisStableId = "documentation-synthesis:test",
            tier = DocumentationTier.ADVISORY,
            providerId = "fixture",
            model = "fixture-model",
            canonicalInputIdentity = "hash",
            promptTemplateIdentity = "template@1",
            promptTemplateVersion = 1,
            documentType = "FINDING_PROPOSAL",
            sourceArtifactIds = listOf("component:A", "component:B"),
            evidenceRefs = emptyList(),
            unresolvedRefs = emptyList(),
            contentSha256 = content?.let { "hash2" },
            status = if (content != null) DocumentationEnrichmentStatus.APPLIED else DocumentationEnrichmentStatus.REJECTED,
            providerInvoked = true,
            cached = false,
        ),
    )

    private val allowed = setOf("component:A", "component:B")

    @Test
    fun `parses a single well-formed block`() {
        val content = """
            ### Finding
            Subject: component:A
            Category: reliability
            Severity: HIGH
            Summary: Missing error handling around a network call.
        """.trimIndent()

        val findings = FindingProposalBuilder.build(result(content), allowed)

        assertEquals(
            listOf(ProposedFinding("component:A", "reliability", FindingSeverity.HIGH, "Missing error handling around a network call.")),
            findings,
        )
    }

    @Test
    fun `parses multiple blocks for different subjects`() {
        val content = """
            ### Finding
            Subject: component:A
            Category: reliability
            Severity: HIGH
            Summary: Issue one.

            ### Finding
            Subject: component:B
            Category: security
            Severity: LOW
            Summary: Issue two.
        """.trimIndent()

        val findings = FindingProposalBuilder.build(result(content), allowed)

        assertEquals(2, findings?.size)
        assertEquals("component:A", findings!![0].subjectStableId)
        assertEquals("component:B", findings[1].subjectStableId)
    }

    @Test
    fun `accepts labeled lines in any order within a block`() {
        val content = """
            ### Finding
            Severity: MEDIUM
            Summary: Reordered fields still parse.
            Subject: component:A
            Category: maintainability
        """.trimIndent()

        val findings = FindingProposalBuilder.build(result(content), allowed)

        assertEquals(1, findings?.size)
    }

    @Test
    fun `an empty well-formed response is a valid non-error empty list`() {
        assertEquals(emptyList(), FindingProposalBuilder.build(result(""), allowed))
        assertEquals(emptyList(), FindingProposalBuilder.build(result("   \n  "), allowed))
    }

    @Test
    fun `rejects a null synthesis result`() {
        assertNull(FindingProposalBuilder.build(result(null), allowed))
    }

    @Test
    fun `rejects stray content before the first block`() {
        val content = """
            Here is my analysis:

            ### Finding
            Subject: component:A
            Category: reliability
            Severity: HIGH
            Summary: Issue.
        """.trimIndent()

        assertNull(FindingProposalBuilder.build(result(content), allowed))
    }

    @Test
    fun `rejects a block missing a required line`() {
        val content = """
            ### Finding
            Subject: component:A
            Category: reliability
            Severity: HIGH
        """.trimIndent()

        assertNull(FindingProposalBuilder.build(result(content), allowed))
    }

    @Test
    fun `rejects a block with a duplicated label`() {
        val content = """
            ### Finding
            Subject: component:A
            Subject: component:B
            Category: reliability
            Severity: HIGH
            Summary: Issue.
        """.trimIndent()

        assertNull(FindingProposalBuilder.build(result(content), allowed))
    }

    @Test
    fun `rejects a block with an extra unrecognized line`() {
        val content = """
            ### Finding
            Subject: component:A
            Category: reliability
            Severity: HIGH
            Summary: Issue.
            Confidence: high
        """.trimIndent()

        assertNull(FindingProposalBuilder.build(result(content), allowed))
    }

    @Test
    fun `rejects an unparseable severity`() {
        val content = """
            ### Finding
            Subject: component:A
            Category: reliability
            Severity: SEVERE
            Summary: Issue.
        """.trimIndent()

        assertNull(FindingProposalBuilder.build(result(content), allowed))
    }

    @Test
    fun `rejects a subject id that was not offered to the model`() {
        val content = """
            ### Finding
            Subject: component:hallucinated
            Category: reliability
            Severity: HIGH
            Summary: Issue.
        """.trimIndent()

        assertNull(FindingProposalBuilder.build(result(content), allowed))
    }

    @Test
    fun `rejects one malformed block even when a sibling block is well-formed`() {
        val content = """
            ### Finding
            Subject: component:A
            Category: reliability
            Severity: HIGH
            Summary: Fine.

            ### Finding
            Subject: component:B
            Category: security
            Severity: NOT_A_SEVERITY
            Summary: Broken.
        """.trimIndent()

        assertNull(FindingProposalBuilder.build(result(content), allowed))
    }

    @Test
    fun `severity is case-insensitive`() {
        val content = """
            ### Finding
            Subject: component:A
            Category: reliability
            Severity: high
            Summary: Issue.
        """.trimIndent()

        val findings = FindingProposalBuilder.build(result(content), allowed)

        assertTrue(findings != null && findings[0].severity == FindingSeverity.HIGH)
    }
}

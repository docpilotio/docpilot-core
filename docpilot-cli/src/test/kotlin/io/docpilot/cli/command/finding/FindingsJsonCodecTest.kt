package io.docpilot.cli.command.finding

import io.docpilot.core.documentation.adr.AiProposedAdr
import io.docpilot.core.documentation.enrichment.DocumentationEnrichmentStatus
import io.docpilot.core.documentation.synthesis.DocumentationTier
import io.docpilot.core.documentation.synthesis.SynthesisRecord
import io.docpilot.core.incremental.specification.review.DocumentationReviewDecision
import io.docpilot.core.incremental.specification.review.DocumentationReviewDisposition
import io.docpilot.core.specification.finding.Finding
import io.docpilot.core.specification.finding.FindingId
import io.docpilot.core.specification.finding.FindingSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FindingsJsonCodecTest {
    @Test
    fun `decodes finding input arrays including optional unresolvedRefs`() {
        val json = """
            [
              {"subjectStableId": "component:A", "semanticKey": "k1", "category": "reliability",
               "severity": "HIGH", "summary": "s1", "evidenceRefs": ["evidence:e1", "evidence:e2"]},
              {"subjectStableId": "component:B", "semanticKey": "k2", "category": "security",
               "severity": "critical", "summary": "s2", "evidenceRefs": ["evidence:e3"], "unresolvedRefs": ["unresolved:u1"]}
            ]
        """.trimIndent()

        val inputs = FindingsJsonCodec.decodeFindingInputs(json)

        assertEquals(2, inputs.size)
        assertEquals("component:A", inputs[0].subjectStableId)
        assertEquals(setOf("evidence:e1", "evidence:e2"), inputs[0].evidenceRefs)
        assertEquals(emptySet(), inputs[0].unresolvedRefs)
        assertEquals(setOf("unresolved:u1"), inputs[1].unresolvedRefs)
    }

    @Test
    fun `round-trips a validated finding list`() {
        val findings = listOf(
            Finding(
                id = FindingId("finding:1"), subjectStableId = "component:A", semanticKey = "k1",
                category = "reliability", severity = FindingSeverity.HIGH, summary = "summary text",
                evidenceRefs = setOf("evidence:e1"), unresolvedRefs = setOf("unresolved:u1"),
            ),
        )

        val decoded = FindingsJsonCodec.decodeFindings(FindingsJsonCodec.encodeFindings(findings))

        assertEquals(findings, decoded)
    }

    @Test
    fun `round-trips review decisions including a null comment`() {
        val decisions = listOf(
            DocumentationReviewDecision("finding:1", DocumentationReviewDisposition.ACCEPTED, "looks right"),
            DocumentationReviewDecision("finding:2", DocumentationReviewDisposition.REJECTED, null),
        )

        val decoded = FindingsJsonCodec.decodeDecisions(FindingsJsonCodec.encodeDecisions(decisions))

        assertEquals(decisions, decoded)
    }

    @Test
    fun `round-trips an AI-proposed ADR including its synthesis record`() {
        val proposal = AiProposedAdr(
            proposalId = "documentation-synthesis:abc",
            title = "Title", context = "Context", decision = "Decision",
            consequences = "Consequences", alternatives = "Alternatives",
            citedFindingIds = listOf("component:A", "component:B"),
            record = SynthesisRecord(
                synthesisStableId = "documentation-synthesis:abc",
                tier = DocumentationTier.ADVISORY,
                providerId = "fixture",
                model = "fixture-model",
                canonicalInputIdentity = "hash1",
                promptTemplateIdentity = "template@1",
                promptTemplateVersion = 1,
                documentType = "ARCHITECTURE_DECISION_RECORD",
                sourceArtifactIds = listOf("component:A", "component:B"),
                evidenceRefs = listOf("evidence:e1"),
                unresolvedRefs = emptyList(),
                contentSha256 = "hash2",
                status = DocumentationEnrichmentStatus.APPLIED,
                providerInvoked = true,
                cached = false,
                diagnostic = null,
            ),
        )

        val decoded = FindingsJsonCodec.decodeProposal(FindingsJsonCodec.encodeProposal(proposal))

        assertEquals(proposal, decoded)
    }

    @Test
    fun `escapes and restores special characters`() {
        val findings = listOf(
            Finding(
                id = FindingId("finding:1"), subjectStableId = "component:A", semanticKey = "k1",
                category = "reliability", severity = FindingSeverity.LOW,
                summary = "quote \" backslash \\ newline\n tab\t done",
                evidenceRefs = setOf("evidence:e1"),
            ),
        )

        val decoded = FindingsJsonCodec.decodeFindings(FindingsJsonCodec.encodeFindings(findings))

        assertEquals(findings[0].summary, decoded[0].summary)
    }

    @Test
    fun `rejects malformed json`() {
        assertFailsWith<IllegalArgumentException> { FindingsJsonCodec.decodeFindings("not json") }
    }

    @Test
    fun `rejects a json array root when an object is expected`() {
        assertFailsWith<IllegalStateException> { FindingsJsonCodec.decodeProposal("[]") }
    }
}

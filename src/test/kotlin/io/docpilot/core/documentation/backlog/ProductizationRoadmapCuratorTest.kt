package io.docpilot.core.documentation.backlog

import io.docpilot.core.incremental.specification.review.DocumentationReviewDecision
import io.docpilot.core.incremental.specification.review.DocumentationReviewDisposition
import io.docpilot.core.specification.claim.ClaimFixtures
import io.docpilot.core.specification.finding.FindingFactory
import io.docpilot.core.specification.finding.FindingSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProductizationRoadmapCuratorTest {
    private val specification = ClaimFixtures.specification()

    private fun finding(semanticKey: String, severity: FindingSeverity) = FindingFactory.create(
        specification, "component:sample", semanticKey, "COVERAGE_GAP", severity,
        "Finding for $semanticKey.", setOf(ClaimFixtures.highEvidence.id),
    )

    private fun document() = ProductizationRoadmapBuilder.build(
        listOf(finding("a", FindingSeverity.CRITICAL), finding("b", FindingSeverity.HIGH), finding("c", FindingSeverity.LOW)),
    )

    @Test
    fun `decision targeting an unknown entry is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ProductizationRoadmapCurator.apply(
                document(),
                listOf(DocumentationReviewDecision("finding:unknown", DocumentationReviewDisposition.ACCEPTED)),
            )
        }
    }

    @Test
    fun `duplicate decisions for the same target are rejected`() {
        val document = document()
        val targetId = ProductizationCurationBinding.decisionTargetId(document.entries.first())
        assertFailsWith<IllegalArgumentException> {
            ProductizationRoadmapCurator.apply(
                document,
                listOf(
                    DocumentationReviewDecision(targetId, DocumentationReviewDisposition.ACCEPTED),
                    DocumentationReviewDecision(targetId, DocumentationReviewDisposition.REJECTED),
                ),
            )
        }
    }

    @Test
    fun `decisions partition entries into adopted deferred and pending`() {
        val document = document()
        val (first, second, third) = document.entries
        val decisions = listOf(
            DocumentationReviewDecision(ProductizationCurationBinding.decisionTargetId(first), DocumentationReviewDisposition.ACCEPTED),
            DocumentationReviewDecision(ProductizationCurationBinding.decisionTargetId(second), DocumentationReviewDisposition.REJECTED),
        )
        val curated = ProductizationRoadmapCurator.apply(document, decisions)
        assertEquals(listOf(first), curated.adopted)
        assertEquals(listOf(second), curated.deferred)
        assertEquals(listOf(third), curated.pending)
    }

    @Test
    fun `binding-produced target id constructs a valid DocumentationReviewDecision`() {
        val entry = document().entries.first()
        val decision = DocumentationReviewDecision(
            targetId = ProductizationCurationBinding.decisionTargetId(entry),
            disposition = DocumentationReviewDisposition.ACCEPTED,
            comment = "Adopted for the next release.",
        )
        assertEquals(entry.findingId, decision.targetId)
    }
}

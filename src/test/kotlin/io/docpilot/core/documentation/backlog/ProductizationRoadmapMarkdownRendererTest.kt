package io.docpilot.core.documentation.backlog

import io.docpilot.core.incremental.specification.review.DocumentationReviewDecision
import io.docpilot.core.incremental.specification.review.DocumentationReviewDisposition
import io.docpilot.core.specification.claim.ClaimFixtures
import io.docpilot.core.specification.finding.FindingFactory
import io.docpilot.core.specification.finding.FindingSeverity
import kotlin.test.Test
import kotlin.test.assertTrue

class ProductizationRoadmapMarkdownRendererTest {
    private val specification = ClaimFixtures.specification()

    private fun finding(semanticKey: String, severity: FindingSeverity) = FindingFactory.create(
        specification, "component:sample", semanticKey, "COVERAGE_GAP", severity,
        "Finding for $semanticKey.", setOf(ClaimFixtures.highEvidence.id),
    )

    @Test
    fun `render shows P0 P1 P2 sections`() {
        val document = ProductizationRoadmapBuilder.build(
            listOf(finding("a", FindingSeverity.CRITICAL), finding("b", FindingSeverity.HIGH), finding("c", FindingSeverity.LOW)),
        )
        val markdown = ProductizationRoadmapMarkdownRenderer.render(document)
        assertTrue(markdown.contains("# Productization Roadmap"))
        assertTrue(markdown.contains("## P0"))
        assertTrue(markdown.contains("## P1"))
        assertTrue(markdown.contains("## P2"))
    }

    @Test
    fun `renderCuration shows Adopted Deferred Pending sections`() {
        val document = ProductizationRoadmapBuilder.build(
            listOf(finding("a", FindingSeverity.CRITICAL), finding("b", FindingSeverity.HIGH)),
        )
        val decisions = listOf(
            DocumentationReviewDecision(
                ProductizationCurationBinding.decisionTargetId(document.entries.first()), DocumentationReviewDisposition.ACCEPTED,
            ),
        )
        val curated = ProductizationRoadmapCurator.apply(document, decisions)
        val markdown = ProductizationRoadmapMarkdownRenderer.renderCuration(curated)
        assertTrue(markdown.contains("## Adopted"))
        assertTrue(markdown.contains("## Deferred"))
        assertTrue(markdown.contains("## Pending"))
    }
}

package io.docpilot.core.incremental.specification.review

import io.docpilot.core.incremental.specification.ChangeKind
import io.docpilot.core.incremental.specification.IncrementalUpdateTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentationReviewReportRendererTest {
    @Test
    fun `renders deterministic dry run report with decisions and evidence`() {
        val proposal = DocumentationReviewProposal(
            entries = listOf(
                DocumentationReviewEntry(
                    targetId = "api:one",
                    parentId = "type:one",
                    target = IncrementalUpdateTarget.API,
                    specificationChangeKind = ChangeKind.MODIFIED,
                    documentationChangeKind = DocumentationChangeKind.UPDATE,
                    existingMarkdown = "old",
                    proposedMarkdown = "new",
                    evidenceIds = listOf("evidence:1"),
                ),
            ),
        )
        val renderer = MarkdownDocumentationReviewReportRenderer()
        val decisions = listOf(
            DocumentationReviewDecision("api:one", DocumentationReviewDisposition.ACCEPTED, "Verified"),
        )

        val first = renderer.render(proposal, decisions)
        val second = renderer.render(proposal, decisions)

        assertEquals(first, second)
        assertTrue(first.contains("Proposal completeness: COMPLETE"))
        assertTrue(first.contains("Decision: ACCEPTED"))
        assertTrue(first.contains("Evidence: evidence:1"))
        assertTrue(first.contains("Comment: Verified"))
    }
}

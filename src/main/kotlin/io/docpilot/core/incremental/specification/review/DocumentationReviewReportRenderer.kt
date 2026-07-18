package io.docpilot.core.incremental.specification.review

public interface DocumentationReviewReportRenderer {
    public fun render(
        proposal: DocumentationReviewProposal,
        decisions: List<DocumentationReviewDecision> = emptyList(),
    ): String
}

public class MarkdownDocumentationReviewReportRenderer : DocumentationReviewReportRenderer {
    override fun render(
        proposal: DocumentationReviewProposal,
        decisions: List<DocumentationReviewDecision>,
    ): String {
        require(decisions.map { it.targetId }.distinct().size == decisions.size) {
            "Documentation review report contains duplicate decisions."
        }
        val entryIds = proposal.entries.map { it.targetId }.toSet()
        val unknownDecisionIds = decisions.map { it.targetId }.filterNot(entryIds::contains).sorted()
        require(unknownDecisionIds.isEmpty()) {
            "Documentation review report contains decisions for unknown targets: ${unknownDecisionIds.joinToString()}"
        }
        val decisionById = decisions.associateBy { it.targetId }

        return buildString {
            appendLine("# Documentation Diff Review")
            appendLine()
            appendLine("- Proposal completeness: ${if (proposal.isComplete) "COMPLETE" else "INCOMPLETE"}")
            appendLine("- Proposed targets: ${proposal.entries.size}")
            appendLine("- Missing patches: ${proposal.missingPatchTargetIds.size}")
            if (proposal.missingPatchTargetIds.isNotEmpty()) {
                appendLine("- Missing target IDs: ${proposal.missingPatchTargetIds.joinToString(", ")}")
            }

            proposal.entries.forEach { entry ->
                val decision = decisionById[entry.targetId]
                appendLine()
                appendLine("## `${entry.targetId}`")
                appendLine()
                appendLine("- Target: ${entry.target}")
                appendLine("- Parent: ${entry.parentId ?: "none"}")
                appendLine("- Specification change: ${entry.specificationChangeKind}")
                appendLine("- Documentation change: ${entry.documentationChangeKind}")
                appendLine("- Decision: ${decision?.disposition ?: "PENDING"}")
                appendLine("- Evidence: ${entry.evidenceIds.ifEmpty { listOf("none") }.joinToString(", ")}")
                decision?.comment?.let { appendLine("- Comment: $it") }
                appendLine()
                appendLine("### Existing Markdown")
                appendLine()
                appendLine("````markdown")
                appendLine(entry.existingMarkdown ?: "(managed block does not exist)")
                appendLine("````")
                appendLine()
                appendLine("### Proposed Markdown")
                appendLine()
                appendLine("````markdown")
                appendLine(entry.proposedMarkdown)
                appendLine("````")
            }
        }.trimEnd() + "\n"
    }
}

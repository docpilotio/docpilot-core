package io.docpilot.core.incremental.review

class DefaultDeterministicSectionValidator : DeterministicSectionValidator {
    override fun validate(request: SectionReviewRequest): List<ReviewIssue> {
        val markdown = request.generatedSection.markdown
        val availableEvidence = request.promptPlan.context.evidence.map { it.id }.toSet()
        val issues = buildList {
            if (PLACEHOLDER_PATTERNS.any { it.containsMatchIn(markdown) }) {
                add(
                    ReviewIssue(
                        ReviewIssueSeverity.ERROR,
                        ReviewIssueType.PLACEHOLDER_CONTENT,
                        "Generated section contains unresolved placeholder content.",
                    ),
                )
            }
            val citedEvidence = EVIDENCE_REFERENCE.findAll(markdown)
                .map { it.groupValues[1] }
                .toSortedSet()
            val unknown = citedEvidence - availableEvidence
            if (unknown.isNotEmpty()) {
                add(
                    ReviewIssue(
                        ReviewIssueSeverity.ERROR,
                        ReviewIssueType.MISSING_EVIDENCE,
                        "Generated section references unknown evidence IDs: ${unknown.joinToString()}.",
                        unknown.toList(),
                    ),
                )
            }
            if (availableEvidence.isEmpty() && containsFactualContent(markdown)) {
                add(
                    ReviewIssue(
                        ReviewIssueSeverity.WARNING,
                        ReviewIssueType.MISSING_EVIDENCE,
                        "No source evidence was supplied for a non-empty generated section.",
                    ),
                )
            }
        }
        return issues.sortedWith(SectionReviewResult.REVIEW_ISSUE_ORDER)
    }

    private fun containsFactualContent(markdown: String): Boolean =
        markdown.lineSequence().drop(1).any { it.isNotBlank() }

    private companion object {
        val EVIDENCE_REFERENCE = Regex("\\[evidence:([A-Za-z0-9._:-]+)]", RegexOption.IGNORE_CASE)
        val PLACEHOLDER_PATTERNS = listOf(
            Regex("\\bTODO\\b", RegexOption.IGNORE_CASE),
            Regex("\\bTBD\\b", RegexOption.IGNORE_CASE),
            Regex("\\[PLACEHOLDER]", RegexOption.IGNORE_CASE),
            Regex("<insert[^>]*>", RegexOption.IGNORE_CASE),
        )
    }
}

package io.docpilot.core.documentation.backlog

import io.docpilot.core.incremental.specification.review.DocumentationReviewDecision
import io.docpilot.core.incremental.specification.review.DocumentationReviewDisposition

/**
 * Mirrors RFC-0072's `ClaimReviewBinding` exactly: proves `entry.findingId` is a drop-in-valid
 * `DocumentationReviewDecision.targetId` with zero changes to the review package.
 */
public object ProductizationCurationBinding {
    public fun decisionTargetId(entry: ProductizationBacklogEntry): String = entry.findingId
}

public data class CuratedProductizationRoadmap(
    public val adopted: List<ProductizationBacklogEntry>,
    public val deferred: List<ProductizationBacklogEntry>,
    public val pending: List<ProductizationBacklogEntry>,
)

/**
 * A new, proportionate, in-memory apply step — not `ReviewLifecycleApplyWorkflow`, which is
 * file-system-coupled and tied to `StoredReviewBundle`'s specification-diff invariants that a
 * Finding backlog does not have. Reuses `DocumentationReviewDecision`/`DocumentationReviewDisposition`
 * as-is for the decision shape.
 */
public object ProductizationRoadmapCurator {
    public fun apply(
        document: ProductizationRoadmapDocument,
        decisions: List<DocumentationReviewDecision>,
    ): CuratedProductizationRoadmap {
        require(decisions.map { it.targetId }.distinct().size == decisions.size) {
            "Duplicate curation decision target ids."
        }
        val entryIds = document.entries.map { it.findingId }.toSet()
        require(decisions.all { it.targetId in entryIds }) { "Decision targets an unknown backlog entry." }

        val decisionsById = decisions.associateBy { it.targetId }
        val adopted = mutableListOf<ProductizationBacklogEntry>()
        val deferred = mutableListOf<ProductizationBacklogEntry>()
        val pending = mutableListOf<ProductizationBacklogEntry>()
        document.entries.forEach { entry ->
            when (decisionsById[entry.findingId]?.disposition) {
                DocumentationReviewDisposition.ACCEPTED -> adopted += entry
                DocumentationReviewDisposition.REJECTED -> deferred += entry
                null -> pending += entry
            }
        }
        return CuratedProductizationRoadmap(adopted, deferred, pending)
    }
}

public fun ProductizationRoadmapMarkdownRenderer.renderCuration(curated: CuratedProductizationRoadmap): String = buildString {
    appendLine("# Productization Roadmap — Curation Outcome")
    appendLine()
    listOf(
        "Adopted" to curated.adopted,
        "Deferred" to curated.deferred,
        "Pending" to curated.pending,
    ).forEach { (title, entries) ->
        appendLine("## $title")
        appendLine()
        if (entries.isEmpty()) {
            appendLine("None.")
        } else {
            entries.forEach { entry ->
                appendLine(
                    "- **[AS-IS]** ${ProductizationRoadmapMarkdownRenderer.escapeText(entry.statement.text)} " +
                        "_(Finding: ${ProductizationRoadmapMarkdownRenderer.code(entry.findingId)}; " +
                        "Priority: ${entry.priority})_",
                )
            }
        }
        appendLine()
    }
}

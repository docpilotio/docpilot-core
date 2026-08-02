package io.docpilot.core.documentation.adr

import io.docpilot.core.document.Document
import io.docpilot.core.document.DocumentMetadata
import io.docpilot.core.document.DocumentSection
import io.docpilot.core.generator.adr.AdrMetadata
import io.docpilot.core.generator.adr.AdrStatus
import io.docpilot.core.incremental.specification.review.DocumentationReviewDecision
import io.docpilot.core.incremental.specification.review.DocumentationReviewDisposition

/**
 * Mirrors RFC-0072's `ClaimReviewBinding` and RFC-0081's `ProductizationCurationBinding` exactly.
 */
public object AdrProposalCurationBinding {
    public fun decisionTargetId(proposal: AiProposedAdr): String = proposal.proposalId
}

/**
 * The only path from an AI-drafted proposal to a rendered [Document]. Structurally requires a
 * matching ACCEPTED [DocumentationReviewDecision] — an AI-proposed draft can never become canonical
 * without one. Bypasses `AdrGenerator`/`GenerationPipeline` entirely (which requires a
 * `KnowledgeBuildResult` and would trigger a second, redundant AI call); reuses only the
 * deterministic rendering layer (`Document`/`DocumentSection`/`DocumentRenderer`).
 */
public object AiProposedAdrAdoption {
    public fun adopt(proposal: AiProposedAdr, decision: DocumentationReviewDecision): Document {
        require(decision.targetId == proposal.proposalId) { "Decision does not target this proposal." }
        require(decision.disposition == DocumentationReviewDisposition.ACCEPTED) {
            "Only an ACCEPTED decision may adopt an AI-proposed ADR."
        }
        val content = buildString {
            appendLine("## Context"); appendLine(); appendLine(proposal.context); appendLine()
            appendLine("## Decision"); appendLine(); appendLine(proposal.decision); appendLine()
            appendLine("## Consequences"); appendLine(); appendLine(proposal.consequences); appendLine()
            append("## Alternatives"); appendLine(); appendLine(); append(proposal.alternatives)
        }
        val metadata = DocumentMetadata(
            type = AdrMetadata.DOCUMENT_TYPE,
            attributes = mapOf(
                AdrMetadata.STATUS_KEY to AdrStatus.ACCEPTED.value,
                AdrMetadata.TITLE_KEY to proposal.title,
                AdrMetadata.GENERATOR_KEY to "adr-ai-proposed",
                "adr.proposal.id" to proposal.proposalId,
            ),
        )
        return Document(
            title = proposal.title,
            sections = listOf(DocumentSection(AdrMetadata.SECTION_ID, "Architecture Decision Record", content)),
            metadata = metadata,
        )
    }
}

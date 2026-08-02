package io.docpilot.core.documentation.adr

import io.docpilot.core.documentation.synthesis.SynthesisRecord
import io.docpilot.core.documentation.synthesis.SynthesisRequest
import io.docpilot.core.documentation.synthesis.SynthesisResult
import io.docpilot.core.documentation.synthesis.SynthesisSource

/**
 * Composes with `SynthesisPrompt.render()`'s existing grounding instructions rather than replacing
 * them: appends a required five-heading Markdown format instruction to `canonicalNarrative`, mirroring
 * RFC-0080's `ExecutiveSummaryRequestBuilder`.
 */
public object AdrProposalRequestBuilder {
    private const val FORMAT_INSTRUCTION =
        "Structure the draft as exactly five Markdown sections, each on its own '## <Heading>' line, " +
            "in this order: '## Title', '## Context', '## Decision', '## Consequences', '## Alternatives'. " +
            "Each section's body must be grounded only in the supplied canonical input; do not fabricate " +
            "facts. Use no other heading."

    public fun request(
        sources: List<SynthesisSource>,
        canonicalFacts: String,
        providerId: String,
        model: String,
        documentType: String = "ARCHITECTURE_DECISION_RECORD",
    ): SynthesisRequest = SynthesisRequest(
        documentType = documentType,
        sources = sources,
        canonicalNarrative = "$canonicalFacts\n\n$FORMAT_INSTRUCTION",
        providerId = providerId,
        model = model,
    )
}

public data class AiProposedAdr(
    public val proposalId: String,
    public val title: String,
    public val context: String,
    public val decision: String,
    public val consequences: String,
    public val alternatives: String,
    public val citedFindingIds: List<String>,
    public val record: SynthesisRecord,
)

/**
 * Parses `SynthesisResult.content` into the five required sections. Fail-closed: any missing or
 * duplicated heading, or any blank section body, rejects the whole draft (returns null) rather than
 * partially or silently accepting a malformed one.
 *
 * Known limitation: an unexpected extra heading the AI adds despite the instruction is not itself
 * detected as an error — its text is silently absorbed into the preceding section's body rather than
 * causing rejection. This is a deliberate simplicity/robustness tradeoff, not an oversight.
 */
public object AiProposedAdrBuilder {
    private val REQUIRED_SECTIONS = listOf("Title", "Context", "Decision", "Consequences", "Alternatives")
    private val HEADING_PATTERN = Regex(
        "(?m)^##\\s+(Title|Context|Decision|Consequences|Alternatives)\\s*$",
        RegexOption.IGNORE_CASE,
    )

    public fun build(result: SynthesisResult): AiProposedAdr? {
        val content = result.content ?: return null
        val sections = parseSections(content) ?: return null
        return AiProposedAdr(
            proposalId = result.record.synthesisStableId,
            title = sections.getValue("Title"),
            context = sections.getValue("Context"),
            decision = sections.getValue("Decision"),
            consequences = sections.getValue("Consequences"),
            alternatives = sections.getValue("Alternatives"),
            citedFindingIds = result.record.sourceArtifactIds,
            record = result.record,
        )
    }

    private fun parseSections(content: String): Map<String, String>? {
        val matches = HEADING_PATTERN.findAll(content).toList()
        if (matches.size != REQUIRED_SECTIONS.size) return null
        val headings = matches.map { match ->
            REQUIRED_SECTIONS.first { it.equals(match.groupValues[1], ignoreCase = true) }
        }
        if (headings.toSet() != REQUIRED_SECTIONS.toSet()) return null

        val sections = mutableMapOf<String, String>()
        matches.forEachIndexed { index, match ->
            val start = match.range.last + 1
            val end = if (index + 1 < matches.size) matches[index + 1].range.first else content.length
            val body = content.substring(start, end).trim()
            if (body.isBlank()) return null
            sections[headings[index]] = body
        }
        return sections
    }
}

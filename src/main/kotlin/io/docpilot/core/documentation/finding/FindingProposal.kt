package io.docpilot.core.documentation.finding

import io.docpilot.core.documentation.synthesis.SynthesisRequest
import io.docpilot.core.documentation.synthesis.SynthesisResult
import io.docpilot.core.documentation.synthesis.SynthesisSource
import io.docpilot.core.specification.finding.FindingSeverity

/**
 * Composes with `SynthesisPrompt.render()`'s existing grounding instructions rather than replacing
 * them: appends a required repeated-block format instruction to `canonicalNarrative`, mirroring
 * RFC-0080's `ExecutiveSummaryRequestBuilder` and RFC-0082's `AdrProposalRequestBuilder`.
 */
public object FindingProposalRequestBuilder {
    private const val FORMAT_INSTRUCTION =
        "For each subject where you find a real, evidence-grounded issue, output a block starting with a line " +
            "reading exactly '### Finding', followed by exactly these four labeled lines, one per line, in any " +
            "order: 'Subject: <the exact source artifact id this issue is about>', 'Category: <short category>', " +
            "'Severity: <CRITICAL|HIGH|MEDIUM|LOW|INFO>', 'Summary: <one sentence>'. Output zero or more such " +
            "blocks, one per issue found; if no subject has a real issue, output nothing else."

    public fun request(
        sources: List<SynthesisSource>,
        canonicalFacts: String,
        providerId: String,
        model: String,
        documentType: String = "FINDING_PROPOSAL",
    ): SynthesisRequest = SynthesisRequest(
        documentType = documentType,
        sources = sources,
        canonicalNarrative = "$canonicalFacts\n\n$FORMAT_INSTRUCTION",
        providerId = providerId,
        model = model,
    )
}

public data class ProposedFinding(
    public val subjectStableId: String,
    public val category: String,
    public val severity: FindingSeverity,
    public val summary: String,
)

/**
 * Parses zero or more repeated `### Finding` blocks. Fail-closed: any block with a missing, duplicated,
 * or extra required line, any unparseable severity, any stray non-blank content outside a block, or any
 * `Subject` not present in [allowedSubjectIds] (guards against a hallucinated subject id) rejects the
 * whole batch (returns `null`) — never a partial result, mirroring
 * [io.docpilot.core.documentation.adr.AiProposedAdrBuilder]. A well-formed, empty response (no blocks)
 * is a valid, non-error outcome: `emptyList()`.
 *
 * `evidenceRefs` are deliberately absent from [ProposedFinding] — the caller (not this parser) is
 * responsible for attaching real, already-known evidence for the matched subject. The AI's own text is
 * never a source of evidenceRefs anywhere in this pipeline.
 */
public object FindingProposalBuilder {
    private val BLOCK_HEADER = Regex("(?m)^###\\s+Finding\\s*$")
    private val REQUIRED_LABELS = setOf("Subject", "Category", "Severity", "Summary")
    private val LABELED_LINE = Regex("^(Subject|Category|Severity|Summary):\\s*(.+)$")

    public fun build(result: SynthesisResult, allowedSubjectIds: Set<String>): List<ProposedFinding>? {
        val content = result.content ?: return null
        return parse(content, allowedSubjectIds)
    }

    private fun parse(content: String, allowedSubjectIds: Set<String>): List<ProposedFinding>? {
        val matches = BLOCK_HEADER.findAll(content).toList()
        if (matches.isEmpty()) return if (content.isBlank()) emptyList() else null
        if (content.substring(0, matches.first().range.first).isNotBlank()) return null

        val findings = mutableListOf<ProposedFinding>()
        matches.forEachIndexed { index, match ->
            val start = match.range.last + 1
            val end = if (index + 1 < matches.size) matches[index + 1].range.first else content.length
            val lines = content.substring(start, end).trim().lines().map { it.trim() }.filter { it.isNotBlank() }
            if (lines.size != REQUIRED_LABELS.size) return null

            val fields = mutableMapOf<String, String>()
            lines.forEach { line ->
                val fieldMatch = LABELED_LINE.matchEntire(line) ?: return null
                val (label, value) = fieldMatch.destructured
                if (value.isBlank() || fields.putIfAbsent(label, value.trim()) != null) return null
            }
            if (fields.keys != REQUIRED_LABELS) return null

            val subject = fields.getValue("Subject")
            if (subject !in allowedSubjectIds) return null
            val severity = runCatching { FindingSeverity.valueOf(fields.getValue("Severity").trim().uppercase()) }
                .getOrElse { return null }
            findings += ProposedFinding(subject, fields.getValue("Category"), severity, fields.getValue("Summary"))
        }
        return findings
    }
}

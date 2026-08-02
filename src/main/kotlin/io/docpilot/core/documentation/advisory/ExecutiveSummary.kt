package io.docpilot.core.documentation.advisory

import io.docpilot.core.documentation.synthesis.DocumentationTier
import io.docpilot.core.documentation.synthesis.SynthesisRecord
import io.docpilot.core.documentation.synthesis.SynthesisRequest
import io.docpilot.core.documentation.synthesis.SynthesisResult
import io.docpilot.core.documentation.synthesis.SynthesisSource

public data class ExecutiveSummaryDocument(
    public val tier: DocumentationTier = DocumentationTier.ADVISORY,
    public val statements: List<LabeledStatement>,
    public val sourceArtifactIds: List<String>,
    public val record: SynthesisRecord,
)

/**
 * Composes with `SynthesisPrompt.render()`'s existing grounding instructions rather than replacing
 * them: appends a required AS-IS/TO-BE line-format instruction to `canonicalNarrative`, which
 * `SynthesisPrompt` embeds verbatim into the final prompt sent to the provider.
 */
public object ExecutiveSummaryRequestBuilder {
    private const val FORMAT_INSTRUCTION =
        "Format every non-blank line as either 'AS-IS: <fact grounded only in the supplied canonical " +
            "input>' or 'TO-BE: <a proposed recommendation, not a fact>'. Use no other prefix, heading, " +
            "or list marker."

    public fun request(
        sources: List<SynthesisSource>,
        canonicalFacts: String,
        providerId: String,
        model: String,
        documentType: String = "EXECUTIVE_SUMMARY",
    ): SynthesisRequest = SynthesisRequest(
        documentType = documentType,
        sources = sources,
        canonicalNarrative = "$canonicalFacts\n\n$FORMAT_INSTRUCTION",
        providerId = providerId,
        model = model,
    )
}

/**
 * Parses `SynthesisResult.content` into `LabeledStatement`s. AS-IS statements carry the request's
 * aggregate `evidenceRefs` (already fail-closed validated per-source before the AI ever ran, per
 * RFC-0079/RFC-0072) — not a per-sentence-specific citation. DocPilot cannot independently verify
 * that one particular AI-generated sentence corresponds to one particular Evidence entry at
 * natural-language granularity; the achievable guarantee is request-level source grounding plus
 * explicit self-labeling of every line, with any unlabeled line rejecting the whole document.
 */
public object ExecutiveSummaryBuilder {
    public fun build(result: SynthesisResult): ExecutiveSummaryDocument? {
        val content = result.content ?: return null
        val statements = parse(content, result.record.evidenceRefs.toSet()) ?: return null
        return ExecutiveSummaryDocument(
            statements = statements,
            sourceArtifactIds = result.record.sourceArtifactIds,
            record = result.record,
        )
    }

    private fun parse(content: String, aggregateEvidenceRefs: Set<String>): List<LabeledStatement>? {
        val lines = content.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        val statements = mutableListOf<LabeledStatement>()
        for (line in lines) {
            val statement = when {
                line.startsWith("AS-IS:", ignoreCase = true) -> {
                    val text = line.substring(PREFIX_LENGTH).trim()
                    if (text.isBlank()) return null
                    LabeledStatement(StatementLabel.AS_IS, text, aggregateEvidenceRefs)
                }
                line.startsWith("TO-BE:", ignoreCase = true) -> {
                    val text = line.substring(PREFIX_LENGTH).trim()
                    if (text.isBlank()) return null
                    LabeledStatement(StatementLabel.TO_BE, text, emptySet())
                }
                else -> return null
            }
            statements += statement
        }
        return statements
    }

    private const val PREFIX_LENGTH = 6
}

public object ExecutiveSummaryMarkdownRenderer {
    public fun render(document: ExecutiveSummaryDocument): String = buildString {
        appendLine("# Executive Summary")
        appendLine()
        appendLine(
            "Tier: **Advisory** — synthesized from ${document.sourceArtifactIds.size} canonical source(s): " +
                document.sourceArtifactIds.joinToString(", ") { code(it) } + ".",
        )
        appendLine()
        document.statements.forEach { statement ->
            val tag = if (statement.label == StatementLabel.AS_IS) "**[AS-IS]**" else "**[TO-BE]**"
            appendLine("- $tag ${escapeText(statement.text)}")
        }
    }

    private fun code(value: String): String = "`${value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')}`"

    private fun escapeText(value: String): String = buildString {
        value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ').forEach { character ->
            if (character in MARKDOWN_SPECIAL_CHARACTERS) append('\\')
            append(character)
        }
    }

    private val MARKDOWN_SPECIAL_CHARACTERS: Set<Char> =
        setOf('\\', '`', '*', '_', '{', '}', '[', ']', '<', '>', '#', '+', '-', '!', '|')
}

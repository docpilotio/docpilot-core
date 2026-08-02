package io.docpilot.core.documentation.advisory

import io.docpilot.core.documentation.synthesis.DocumentationTier
import io.docpilot.core.specification.finding.Finding

public data class KnownIssuesRegisterDocument(
    public val tier: DocumentationTier = DocumentationTier.ADVISORY,
    public val statements: List<LabeledStatement>,
    public val findingIds: List<String>,
)

/**
 * Deterministic — no AI call. Every `Finding` supplied is already guaranteed valid by
 * `FindingFactory`'s own fail-closed construction (RFC-0078), so re-validating identical
 * `evidenceRefs` here would be redundant, not defense-in-depth.
 */
public object KnownIssuesRegisterBuilder {
    public fun build(findings: List<Finding>): KnownIssuesRegisterDocument {
        require(findings.isNotEmpty()) { "Known Issues Register requires at least one Finding." }
        val ordered = findings.sortedWith(compareBy({ it.severity.ordinal }, { it.id.value }))
        return KnownIssuesRegisterDocument(
            statements = ordered.map { LabeledStatement(StatementLabel.AS_IS, it.summary, it.evidenceRefs) },
            findingIds = ordered.map { it.id.value },
        )
    }
}

public object KnownIssuesRegisterMarkdownRenderer {
    public fun render(document: KnownIssuesRegisterDocument, findings: List<Finding>): String = buildString {
        val ordered = findings.sortedWith(compareBy({ it.severity.ordinal }, { it.id.value }))
        appendLine("# Known Issues Register")
        appendLine()
        appendLine(
            "Tier: **Advisory** — deterministically compiled from ${ordered.size} canonical Finding(s), " +
                "each cited to real Evidence/Contract. No AI call is used to produce this document.",
        )
        appendLine()
        appendLine("| Severity | Category | Summary | Evidence |")
        appendLine("|---|---|---|---:|")
        ordered.forEach { finding ->
            appendLine(
                "| ${finding.severity} | ${cell(finding.category)} | ${cell(finding.summary)} | " +
                    "${finding.evidenceRefs.size} |",
            )
        }
        appendLine()
        appendLine("## Statements")
        appendLine()
        document.statements.forEach { statement ->
            appendLine("- **[AS-IS]** ${escapeText(statement.text)} _(Evidence: ${statement.evidenceRefs.size})_")
        }
    }

    private fun escapeText(value: String): String = buildString {
        value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ').forEach { character ->
            if (character in MARKDOWN_SPECIAL_CHARACTERS) append('\\')
            append(character)
        }
    }

    private fun cell(value: String): String = escapeText(value).replace("|", "\\|")

    private val MARKDOWN_SPECIAL_CHARACTERS: Set<Char> =
        setOf('\\', '`', '*', '_', '{', '}', '[', ']', '<', '>', '#', '+', '-', '!', '|')
}

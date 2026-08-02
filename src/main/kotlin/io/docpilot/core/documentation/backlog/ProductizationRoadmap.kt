package io.docpilot.core.documentation.backlog

import io.docpilot.core.documentation.advisory.LabeledStatement
import io.docpilot.core.documentation.advisory.StatementLabel
import io.docpilot.core.documentation.synthesis.DocumentationTier
import io.docpilot.core.specification.finding.Finding
import io.docpilot.core.specification.finding.FindingSeverity

public enum class BacklogPriority { P0, P1, P2 }

/**
 * Single named mapping point from `Finding.severity` to `BacklogPriority`, so the rule can change
 * later without touching callers. CRITICAL is ship-blocking (P0); HIGH/MEDIUM are important (P1);
 * LOW/INFO are backlog (P2). This RFC organizes Findings by their existing AI-judged severity
 * (RFC-0078); it does not introduce a second, independent AI judgment of priority.
 */
public object BacklogPriorityMapping {
    public fun of(severity: FindingSeverity): BacklogPriority = when (severity) {
        FindingSeverity.CRITICAL -> BacklogPriority.P0
        FindingSeverity.HIGH, FindingSeverity.MEDIUM -> BacklogPriority.P1
        FindingSeverity.LOW, FindingSeverity.INFO -> BacklogPriority.P2
    }
}

public data class ProductizationBacklogEntry(
    public val findingId: String,
    public val priority: BacklogPriority,
    public val statement: LabeledStatement,
)

public data class ProductizationRoadmapDocument(
    public val tier: DocumentationTier = DocumentationTier.ADVISORY,
    public val entries: List<ProductizationBacklogEntry>,
)

/**
 * Deterministic — no AI call. Every `Finding` supplied is already guaranteed valid by
 * `FindingFactory`'s own fail-closed construction (RFC-0078), so re-validating identical
 * `evidenceRefs` here would be redundant, matching RFC-0080's Known Issues Register.
 */
public object ProductizationRoadmapBuilder {
    public fun build(findings: List<Finding>): ProductizationRoadmapDocument {
        require(findings.isNotEmpty()) { "Productization Roadmap requires at least one Finding." }
        val entries = findings.map { finding ->
            ProductizationBacklogEntry(
                findingId = finding.id.value,
                priority = BacklogPriorityMapping.of(finding.severity),
                statement = LabeledStatement(StatementLabel.AS_IS, finding.summary, finding.evidenceRefs),
            )
        }.sortedWith(compareBy({ it.priority.ordinal }, { it.findingId }))
        return ProductizationRoadmapDocument(entries = entries)
    }
}

public object ProductizationRoadmapMarkdownRenderer {
    public fun render(document: ProductizationRoadmapDocument): String = buildString {
        appendLine("# Productization Roadmap")
        appendLine()
        appendLine(
            "Tier: **Advisory** — deterministically organized from ${document.entries.size} canonical " +
                "Finding(s) by severity. No AI call is used to produce this document.",
        )
        appendLine()
        BacklogPriority.entries.forEach { priority ->
            val entries = document.entries.filter { it.priority == priority }
            appendLine("## $priority")
            appendLine()
            if (entries.isEmpty()) {
                appendLine("None.")
            } else {
                entries.forEach { entry ->
                    appendLine(
                        "- **[AS-IS]** ${escapeText(entry.statement.text)} " +
                            "_(Finding: ${code(entry.findingId)}; Evidence: ${entry.statement.evidenceRefs.size})_",
                    )
                }
            }
            appendLine()
        }
    }

    internal fun code(value: String): String = "`${value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')}`"

    internal fun escapeText(value: String): String = buildString {
        value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ').forEach { character ->
            if (character in MARKDOWN_SPECIAL_CHARACTERS) append('\\')
            append(character)
        }
    }

    private val MARKDOWN_SPECIAL_CHARACTERS: Set<Char> =
        setOf('\\', '`', '*', '_', '{', '}', '[', ']', '<', '>', '#', '+', '-', '!', '|')
}

package io.docpilot.core.evolution

public class EvolutionReportRenderer {
    public fun render(report: DocumentationEvolutionReport): String = buildString {
        appendLine("# Documentation Evolution Report")
        appendLine()
        appendLine("- Project: `${report.projectId}`")
        appendLine("- Before state: `${report.beforeStateSha256}`")
        appendLine("- After state: `${report.afterStateSha256}`")
        appendLine("- Coverage: `${report.coverage.state}`")
        appendLine("- Report SHA-256: `${report.reportSha256}`")
        appendLine()
        appendSection("Summary") {
            appendLine("- Changes: ${report.changes.size}")
            appendLine("- Impacted artifacts: ${report.impactedArtifacts.size}")
            appendLine("- Causal nodes: ${report.causalGraph.nodes.size}")
            appendLine("- Causal edges: ${report.causalGraph.edges.size}")
        }
        appendChangeSection("Added", report.changes.filter { it.kind in setOf(EvolutionChangeKind.ENTITY_ADDED, EvolutionChangeKind.RELATIONSHIP_ADDED, EvolutionChangeKind.ARTIFACT_CREATED) })
        appendChangeSection("Removed", report.changes.filter { it.kind in setOf(EvolutionChangeKind.ENTITY_REMOVED, EvolutionChangeKind.RELATIONSHIP_REMOVED) })
        appendChangeSection("Modified", report.changes.filter { it.kind in setOf(EvolutionChangeKind.ENTITY_MODIFIED, EvolutionChangeKind.API_CHANGED, EvolutionChangeKind.PROPERTY_CHANGED, EvolutionChangeKind.RELATIONSHIP_MODIFIED, EvolutionChangeKind.ARTIFACT_UPDATED) })
        appendChangeSection("Moved or Renamed", report.changes.filter { it.kind == EvolutionChangeKind.ENTITY_MOVED || "name" in it.changedFields })
        appendChangeSection("Ownership and Reconciliation Decisions", report.changes.filter { it.kind in setOf(EvolutionChangeKind.OWNERSHIP_CHANGED, EvolutionChangeKind.RECONCILIATION_CONFLICTED, EvolutionChangeKind.USER_DECISION_APPLIED, EvolutionChangeKind.ARTIFACT_RETAINED) })
        appendSection("Artifact Impact") {
            if (report.impactedArtifacts.isEmpty()) appendLine("No artifact impact.")
            report.impactedArtifacts.forEach { impact ->
                appendLine("- `${impact.artifactId}` → `${impact.operation}` at `${impact.relativePath}`")
                if (impact.selectionReasons.isNotEmpty()) appendLine("  - Reasons: ${impact.selectionReasons.joinToString(", ")}")
                if (impact.causalChangeIds.isNotEmpty()) appendLine("  - Causes: ${impact.causalChangeIds.joinToString(", ")}")
            }
        }
        appendSection("Causal Paths") {
            if (report.causalGraph.edges.isEmpty()) appendLine("No causal edges.")
            report.causalGraph.edges.forEach { edge ->
                appendLine("- `${edge.sourceNodeId}` --`${edge.kind}`--> `${edge.targetNodeId}`")
            }
        }
        appendSection("Missing Evidence") {
            if (report.coverage.findings.isEmpty()) appendLine("No uncovered material change.")
            report.coverage.findings.forEach { finding ->
                appendLine("- `${finding.kind}` `${finding.subjectId}`: ${finding.message}")
            }
        }
        appendSection("Integrity") {
            appendLine("- Graph SHA-256: `${report.causalGraph.graphSha256}`")
            appendLine("- Report verified: `${EvolutionReportVerifier().verify(report)}`")
        }
    }.trimEnd() + "\n"

    private fun StringBuilder.appendSection(title: String, block: StringBuilder.() -> Unit) {
        appendLine("## $title")
        appendLine()
        block()
        appendLine()
    }

    private fun StringBuilder.appendChangeSection(title: String, changes: List<DocumentationEvolutionChange>) {
        appendSection(title) {
            if (changes.isEmpty()) appendLine("None.")
            changes.sortedBy { it.changeId }.forEach { change ->
                appendLine("- `${change.kind}` `${change.subjectId}` (${change.subjectKind})")
                if (change.changedFields.isNotEmpty()) appendLine("  - Fields: ${change.changedFields.joinToString(", ")}")
                if (change.evidenceRefs.isNotEmpty()) appendLine("  - Evidence: ${change.evidenceRefs.joinToString(", ")}")
            }
        }
    }
}

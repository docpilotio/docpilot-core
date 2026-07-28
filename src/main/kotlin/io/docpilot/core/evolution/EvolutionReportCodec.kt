package io.docpilot.core.evolution

import io.docpilot.core.incremental.execution.DocumentationArtifactOperation
import java.util.Base64

/** Strict, deterministic, line-oriented format-1 codec for offline Evolution Evidence. */
public class EvolutionReportCodec {
    public fun encode(report: DocumentationEvolutionReport): String {
        require(EvolutionReportVerifier().verify(report)) { "Cannot encode invalid Evolution Report." }
        return buildString {
            appendLine("DOCPILOT_DOCUMENTATION_EVOLUTION\t${report.formatVersion}")
            appendLine("PROJECT\t${encodeString(report.projectId)}")
            appendLine("STATES\t${report.beforeStateSha256}\t${report.afterStateSha256}")
            report.changes.forEach { change ->
                appendLine(listOf(
                    "CHANGE",
                    encodeString(change.changeId),
                    encodeString(change.subjectId),
                    change.subjectKind.name,
                    change.kind.name,
                    encodeNullable(change.beforeSha256),
                    encodeNullable(change.afterSha256),
                    encodeNullable(change.previousParentId),
                    encodeNullable(change.currentParentId),
                    encodeList(change.changedFields),
                    encodeList(change.evidenceRefs),
                    encodeList(change.causalPredecessorIds),
                    encodeList(change.affectedArtifactIds),
                    change.confidenceClass.name,
                    change.coverageState.name,
                ).joinToString("\t"))
            }
            appendLine("GRAPH\t${report.causalGraph.graphSha256}")
            report.causalGraph.nodes.forEach { node ->
                appendLine(listOf(
                    "NODE",
                    encodeString(node.nodeId),
                    node.kind.name,
                    encodeString(node.subjectId),
                    encodeList(node.evidenceRefs),
                ).joinToString("\t"))
            }
            report.causalGraph.edges.forEach { edge ->
                appendLine(listOf(
                    "EDGE",
                    encodeString(edge.sourceNodeId),
                    encodeString(edge.targetNodeId),
                    edge.kind.name,
                ).joinToString("\t"))
            }
            report.impactedArtifacts.forEach { impact ->
                appendLine(listOf(
                    "IMPACT",
                    encodeString(impact.artifactId),
                    encodeString(impact.relativePath),
                    impact.operation.name,
                    encodeList(impact.selectionReasons),
                    encodeList(impact.sourceChangeIds),
                    encodeList(impact.causalChangeIds),
                    encodeNullable(impact.beforeArtifactSha256),
                    encodeNullable(impact.afterArtifactSha256),
                ).joinToString("\t"))
            }
            appendLine("COVERAGE\t${report.coverage.state.name}")
            report.coverage.findings.forEach { finding ->
                appendLine(listOf(
                    "FINDING",
                    finding.kind.name,
                    encodeString(finding.subjectId),
                    encodeString(finding.message),
                    finding.required.toString(),
                ).joinToString("\t"))
            }
            report.evidenceRefs.forEach { appendLine("EVIDENCE\t${encodeString(it)}") }
            appendLine("REPORT_SHA256\t${report.reportSha256}")
        }
    }

    public fun decode(value: String): DocumentationEvolutionReport {
        val lines = value.lineSequence().filter { it.isNotEmpty() }.toList()
        require(lines.isNotEmpty()) { "Evolution Report is empty." }
        val header = fields(lines.first(), 2, "header")
        require(header[0] == "DOCPILOT_DOCUMENTATION_EVOLUTION") { "Invalid Evolution Report header." }
        val formatVersion = header[1].toIntOrNull() ?: error("Invalid Evolution Report version.")
        require(formatVersion == DocumentationEvolutionFormat.CURRENT_VERSION) {
            "Unsupported Evolution Report format version: $formatVersion"
        }

        var projectId: String? = null
        var beforeState: String? = null
        var afterState: String? = null
        var graphSha: String? = null
        var coverageState: EvolutionCoverageState? = null
        var reportSha: String? = null
        val changes = mutableListOf<DocumentationEvolutionChange>()
        val nodes = mutableListOf<DocumentationEvolutionGraphNode>()
        val edges = mutableListOf<DocumentationEvolutionGraphEdge>()
        val impacts = mutableListOf<EvolutionArtifactImpact>()
        val findings = mutableListOf<EvolutionCoverageFinding>()
        val evidence = mutableListOf<String>()

        lines.drop(1).forEach { line ->
            val parts = line.split('\t')
            when (parts.firstOrNull()) {
                "PROJECT" -> {
                    require(projectId == null)
                    projectId = decodeString(fields(line, 2, "PROJECT")[1])
                }
                "STATES" -> {
                    require(beforeState == null && afterState == null)
                    val v = fields(line, 3, "STATES")
                    beforeState = v[1]; afterState = v[2]
                }
                "CHANGE" -> {
                    val v = fields(line, 15, "CHANGE")
                    changes += DocumentationEvolutionChange(
                        changeId = decodeString(v[1]),
                        subjectId = decodeString(v[2]),
                        subjectKind = EvolutionSubjectKind.valueOf(v[3]),
                        kind = EvolutionChangeKind.valueOf(v[4]),
                        beforeSha256 = decodeNullable(v[5]),
                        afterSha256 = decodeNullable(v[6]),
                        previousParentId = decodeNullable(v[7]),
                        currentParentId = decodeNullable(v[8]),
                        changedFields = decodeList(v[9]),
                        evidenceRefs = decodeList(v[10]),
                        causalPredecessorIds = decodeList(v[11]),
                        affectedArtifactIds = decodeList(v[12]),
                        confidenceClass = EvolutionConfidenceClass.valueOf(v[13]),
                        coverageState = EvolutionCoverageState.valueOf(v[14]),
                    )
                }
                "GRAPH" -> {
                    require(graphSha == null)
                    graphSha = fields(line, 2, "GRAPH")[1]
                }
                "NODE" -> {
                    val v = fields(line, 5, "NODE")
                    nodes += DocumentationEvolutionGraphNode(
                        decodeString(v[1]),
                        EvolutionGraphNodeKind.valueOf(v[2]),
                        decodeString(v[3]),
                        decodeList(v[4]),
                    )
                }
                "EDGE" -> {
                    val v = fields(line, 4, "EDGE")
                    edges += DocumentationEvolutionGraphEdge(
                        decodeString(v[1]),
                        decodeString(v[2]),
                        EvolutionGraphEdgeKind.valueOf(v[3]),
                    )
                }
                "IMPACT" -> {
                    val v = fields(line, 9, "IMPACT")
                    impacts += EvolutionArtifactImpact(
                        artifactId = decodeString(v[1]),
                        relativePath = decodeString(v[2]),
                        operation = DocumentationArtifactOperation.valueOf(v[3]),
                        selectionReasons = decodeList(v[4]),
                        sourceChangeIds = decodeList(v[5]),
                        causalChangeIds = decodeList(v[6]),
                        beforeArtifactSha256 = decodeNullable(v[7]),
                        afterArtifactSha256 = decodeNullable(v[8]),
                    )
                }
                "COVERAGE" -> {
                    require(coverageState == null)
                    coverageState = EvolutionCoverageState.valueOf(fields(line, 2, "COVERAGE")[1])
                }
                "FINDING" -> {
                    val v = fields(line, 5, "FINDING")
                    findings += EvolutionCoverageFinding(
                        EvolutionCoverageFindingKind.valueOf(v[1]),
                        decodeString(v[2]),
                        decodeString(v[3]),
                        v[4].toBooleanStrict(),
                    )
                }
                "EVIDENCE" -> evidence += decodeString(fields(line, 2, "EVIDENCE")[1])
                "REPORT_SHA256" -> {
                    require(reportSha == null)
                    reportSha = fields(line, 2, "REPORT_SHA256")[1]
                }
                else -> error("Unknown Evolution Report record: ${parts.firstOrNull()}")
            }
        }

        val report = DocumentationEvolutionReport(
            formatVersion = formatVersion,
            projectId = requireNotNull(projectId) { "Missing PROJECT record." },
            beforeStateSha256 = requireNotNull(beforeState) { "Missing STATES record." },
            afterStateSha256 = requireNotNull(afterState) { "Missing STATES record." },
            changes = changes,
            causalGraph = DocumentationEvolutionGraph(
                nodes,
                edges,
                requireNotNull(graphSha) { "Missing GRAPH record." },
            ),
            impactedArtifacts = impacts,
            coverage = EvolutionCoverage(
                requireNotNull(coverageState) { "Missing COVERAGE record." },
                findings,
            ),
            evidenceRefs = evidence,
            reportSha256 = requireNotNull(reportSha) { "Missing REPORT_SHA256 record." },
        )
        require(EvolutionReportVerifier().verify(report)) { "Evolution Report integrity verification failed." }
        return report
    }

    private fun fields(line: String, count: Int, label: String): List<String> =
        line.split('\t').also { require(it.size == count) { "Invalid $label field count." } }

    private fun encodeNullable(value: String?): String = value?.let(::encodeString) ?: "~"
    private fun decodeNullable(value: String): String? = if (value == "~") null else decodeString(value)
    private fun encodeList(values: List<String>): String = values.joinToString(",", transform = ::encodeString)
    private fun decodeList(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split(',').map(::decodeString)
    private fun encodeString(value: String): String = encoder.encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun decodeString(value: String): String = String(decoder.decode(value), Charsets.UTF_8)

    private companion object {
        val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
        val decoder: Base64.Decoder = Base64.getUrlDecoder()
    }
}

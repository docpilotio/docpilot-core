package io.docpilot.core.evolution

public class EvolutionReportVerifier {
    public fun verify(
        report: DocumentationEvolutionReport,
        expectedBeforeStateSha256: String? = null,
        expectedAfterStateSha256: String? = null,
    ): Boolean = runCatching {
        require(report.formatVersion == DocumentationEvolutionFormat.CURRENT_VERSION)
        require(report.projectId.isNotBlank())
        require(report.beforeStateSha256.matches(SHA) && report.afterStateSha256.matches(SHA))
        require(expectedBeforeStateSha256 == null || report.beforeStateSha256 == expectedBeforeStateSha256)
        require(expectedAfterStateSha256 == null || report.afterStateSha256 == expectedAfterStateSha256)
        require(report.changes == report.changes.distinctBy { it.changeId }.sortedBy { it.changeId })
        val changeIds = report.changes.mapTo(hashSetOf()) { it.changeId }
        report.changes.forEach { change ->
            require(change.changedFields == change.changedFields.distinct().sorted())
            require(change.evidenceRefs == change.evidenceRefs.distinct().sorted())
            require(change.causalPredecessorIds == change.causalPredecessorIds.distinct().sorted())
            require(change.affectedArtifactIds == change.affectedArtifactIds.distinct().sorted())
            require(change.confidenceClass != EvolutionConfidenceClass.OPTIONAL_AI_NARRATIVE)
            require(change.coverageState == report.coverage.state)
            require(change.causalPredecessorIds.all { it in change.evidenceRefs || it in changeIds })
            val expectedId = EvolutionCanonicalizer.stableChangeId(
                change.subjectId,
                change.subjectKind,
                change.kind,
                change.beforeSha256,
                change.afterSha256,
                change.previousParentId,
                change.currentParentId,
                change.changedFields,
            )
            require(change.changeId == expectedId)
        }
        require(EvolutionGraphVerifier().verify(report.causalGraph))
        val graphNodeIds = report.causalGraph.nodes.mapTo(hashSetOf()) { it.nodeId }
        require(report.changes.all { "change:${it.changeId}" in graphNodeIds })
        require(report.impactedArtifacts == report.impactedArtifacts.distinctBy { it.artifactId }.sortedBy { it.artifactId })
        report.impactedArtifacts.forEach { impact ->
            require("artifact:${impact.artifactId}" in graphNodeIds)
            require("artifact-plan:${impact.artifactId}:${impact.operation.name}" in graphNodeIds)
            require(EvolutionCanonicalizer.isSafeRelativePath(impact.relativePath))
            require(impact.selectionReasons == impact.selectionReasons.distinct().sorted())
            require(impact.sourceChangeIds == impact.sourceChangeIds.distinct().sorted())
            require(impact.causalChangeIds == impact.causalChangeIds.distinct().sorted())
            require(impact.beforeArtifactSha256 == null || impact.beforeArtifactSha256.matches(SHA))
            require(impact.afterArtifactSha256 == null || impact.afterArtifactSha256.matches(SHA))
        }
        require(report.coverage.findings == report.coverage.findings.distinct().sortedWith(compareBy({ it.kind.name }, { it.subjectId }, { it.message })))
        require(report.evidenceRefs == report.evidenceRefs.distinct().sorted())
        if (report.coverage.state == EvolutionCoverageState.COMPLETE) {
            require(report.coverage.findings.isEmpty())
            require(report.impactedArtifacts.filter { it.operation != io.docpilot.core.incremental.execution.DocumentationArtifactOperation.KEEP }
                .all { it.causalChangeIds.isNotEmpty() && it.afterArtifactSha256 != null })
        }
        require(report.reportSha256 == EvolutionCanonicalizer.reportSha256(report))
    }.isSuccess

    private companion object {
        val SHA = Regex("[0-9a-f]{64}")
    }
}

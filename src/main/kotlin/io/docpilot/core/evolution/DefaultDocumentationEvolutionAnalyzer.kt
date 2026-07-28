package io.docpilot.core.evolution

public class DefaultDocumentationEvolutionAnalyzer : DocumentationEvolutionAnalyzer {
    override fun analyze(request: DocumentationEvolutionRequest): DocumentationEvolutionReport {
        val validation = EvolutionInputValidator().validate(request)
        if (validation.blockedState != null) {
            return blockedReport(request, validation.blockedState, validation.findings)
        }

        val specificationChanges = EvolutionChangeExtractor().extract(
            request.beforeSnapshot.specification,
            request.afterSnapshot.specification,
        )
        val bindings = EvolutionBindingEngine().bind(request, specificationChanges)
        val findings = mutableListOf<EvolutionCoverageFinding>()

        val relationshipChanges = bindings.changes.filter { it.subjectKind == EvolutionSubjectKind.RELATIONSHIP }
        if (relationshipChanges.isNotEmpty() &&
            (request.beforeRelationshipReport == null || request.afterRelationshipReport == null)
        ) {
            findings += EvolutionCoverageFinding(
                EvolutionCoverageFindingKind.MISSING_RELATIONSHIP_REPORT,
                request.afterSnapshot.projectIdentity.projectId,
                "Relationship changes were observed without both before and after RFC-0053 projection reports.",
                required = false,
            )
        }
        bindings.changes.filter {
            it.confidenceClass == EvolutionConfidenceClass.OBSERVED &&
                it.subjectKind !in setOf(EvolutionSubjectKind.PROJECT, EvolutionSubjectKind.ARTIFACT) &&
                it.evidenceRefs.isEmpty()
        }.forEach { change ->
            findings += EvolutionCoverageFinding(
                EvolutionCoverageFindingKind.MISSING_SOURCE_EVIDENCE,
                change.subjectId,
                "Observed change has no source Evidence reference.",
                required = false,
            )
        }
        bindings.impacts.filter {
            it.operation != io.docpilot.core.incremental.execution.DocumentationArtifactOperation.KEEP &&
                it.causalChangeIds.isEmpty()
        }.forEach { impact ->
            findings += EvolutionCoverageFinding(
                EvolutionCoverageFindingKind.MISSING_CAUSAL_PATH,
                impact.artifactId,
                "Material artifact action has no verified causal change path.",
                required = false,
            )
        }
        bindings.impacts.filter {
            it.operation != io.docpilot.core.incremental.execution.DocumentationArtifactOperation.KEEP &&
                it.afterArtifactSha256 == null
        }.forEach { impact ->
            findings += EvolutionCoverageFinding(
                EvolutionCoverageFindingKind.MISSING_ARTIFACT_STATE,
                impact.artifactId,
                "After-document content hash was not supplied; impact selection remains verified but final document state is uncovered.",
                required = false,
            )
        }

        val coverageState = if (findings.isEmpty()) {
            EvolutionCoverageState.COMPLETE
        } else {
            EvolutionCoverageState.PARTIAL_MISSING_OPTIONAL_EVIDENCE
        }
        val changes = bindings.changes.map { it.copy(coverageState = coverageState) }.sortedBy { it.changeId }
        val graph = EvolutionCausalGraphBuilder().build(request, changes, bindings.impacts)
        val evidenceRefs = (
            changes.flatMap { it.evidenceRefs } + request.additionalEvidenceRefs
            ).distinct().sorted()
        val unsigned = DocumentationEvolutionReport(
            projectId = request.afterSnapshot.projectIdentity.projectId,
            beforeStateSha256 = request.beforeSnapshot.integrity.payloadSha256,
            afterStateSha256 = request.afterSnapshot.integrity.payloadSha256,
            changes = changes,
            causalGraph = graph,
            impactedArtifacts = bindings.impacts,
            coverage = EvolutionCoverage(
                coverageState,
                findings.distinct().sortedWith(compareBy({ it.kind.name }, { it.subjectId }, { it.message })),
            ),
            evidenceRefs = evidenceRefs,
            reportSha256 = "",
        )
        return unsigned.copy(reportSha256 = EvolutionCanonicalizer.reportSha256(unsigned))
    }

    private fun blockedReport(
        request: DocumentationEvolutionRequest,
        state: EvolutionCoverageState,
        findings: List<EvolutionCoverageFinding>,
    ): DocumentationEvolutionReport {
        val graph = DocumentationEvolutionGraph(emptyList(), emptyList(), EvolutionCanonicalizer.graphSha256(emptyList(), emptyList()))
        val unsigned = DocumentationEvolutionReport(
            projectId = request.afterSnapshot.projectIdentity.projectId,
            beforeStateSha256 = request.beforeSnapshot.integrity.payloadSha256,
            afterStateSha256 = request.afterSnapshot.integrity.payloadSha256,
            changes = emptyList(),
            causalGraph = graph,
            impactedArtifacts = emptyList(),
            coverage = EvolutionCoverage(state, findings.sortedWith(compareBy({ it.kind.name }, { it.subjectId }, { it.message }))),
            evidenceRefs = request.additionalEvidenceRefs.distinct().sorted(),
            reportSha256 = "",
        )
        return unsigned.copy(reportSha256 = EvolutionCanonicalizer.reportSha256(unsigned))
    }
}

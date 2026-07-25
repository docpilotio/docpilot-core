package io.docpilot.release

import java.nio.file.Path

public data class VerificationResult(
    val result: EvidenceResult,
    val failures: List<GateFailure>,
)

public class ReleaseEvidenceVerifier(
    private val repository: ReleaseEvidenceRepository = ReleaseEvidenceRepository(),
    private val git: GitCandidateInspector = GitCandidateInspector(),
    private val artifacts: ArtifactCollector = ArtifactCollector(),
    private val evaluator: ReleaseGateEvaluator = ReleaseGateEvaluator(),
    private val documentation: DocumentationSyncInspector = DocumentationSyncInspector(),
) {
    public fun verify(repositoryRoot: Path, manifestPath: Path): VerificationResult {
        val manifest = repository.load(manifestPath)
        val failures = linkedSetOf<GateFailure>()
        val current = git.inspect(repositoryRoot)
        if (current.commit != manifest.candidate.coreCommit) failures += GateFailure.CANDIDATE_MISMATCH
        if (!current.clean || current.operationInProgress) failures += GateFailure.DIRTY_REPOSITORY
        if (manifest.artifacts.filter { it.required }.any { !artifacts.verify(repositoryRoot, it) }) {
            failures += GateFailure.ARTIFACT_MISMATCH
        }
        val documentationSynchronized = documentation.inspect(repositoryRoot, implementationComplete = true)
        if (documentationSynchronized != manifest.scopeChecks.documentationSynchronized) {
            failures += GateFailure.INVALID_MANIFEST
        }
        val reevaluated = evaluator.evaluate(
            ReleaseEvidenceInput(
                manifest.releaseId,
                manifest.candidate,
                manifest.contracts,
                manifest.executions,
                manifest.testAggregate,
                manifest.artifacts,
                manifest.compatibilityChecks,
                manifest.scopeChecks.copy(documentationSynchronized = documentationSynchronized),
                manifest.policy,
            ),
        )
        if (reevaluated != manifest.gate) failures += GateFailure.INVALID_MANIFEST
        failures += reevaluated.failures
        val ordered = GateFailure.entries.filter { it in failures }
        return VerificationResult(
            if (ordered.isEmpty()) EvidenceResult.PASS else EvidenceResult.FAIL,
            ordered,
        )
    }
}

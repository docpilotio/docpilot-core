package io.docpilot.release

public class ReleaseGateEvaluator {
    public fun evaluate(input: ReleaseEvidenceInput): GateDecision {
        val failures = linkedSetOf<GateFailure>()

        if (!input.candidate.repositoryClean || !input.scopeChecks.repositoryClean) {
            failures += GateFailure.DIRTY_REPOSITORY
        }
        val executions = input.executions.associateBy { it.id }
        if (REQUIRED_EXECUTIONS.any { executions[it]?.required != true }) {
            failures += GateFailure.MISSING_EXECUTION
        }
        if (executions.values.any { it.required && (it.result != EvidenceResult.PASS || it.exitCode != 0) }) {
            failures += GateFailure.EXECUTION_FAILED
        }

        val tests = input.testAggregate
        if (tests.xmlFileCount <= 0 || tests.tests <= 0 ||
            tests.failures > tests.tests || tests.errors > tests.tests || tests.skipped > tests.tests
        ) {
            failures += GateFailure.INVALID_TEST_AGGREGATE
        }
        if (tests.cached || !tests.fresh) failures += GateFailure.CACHED_TEST_EVIDENCE
        if (tests.failures > 0) failures += GateFailure.TEST_FAILURES
        if (tests.errors > 0) failures += GateFailure.TEST_ERRORS
        if (tests.skipped > 0) failures += GateFailure.TESTS_SKIPPED

        val artifacts = input.artifacts.associateBy { it.id }
        if (!REQUIRED_ARTIFACT_KINDS.all { kind ->
                input.artifacts.any { it.required && it.kind == kind }
            }
        ) {
            failures += GateFailure.MISSING_ARTIFACT
        }
        val executionIds = executions.keys
        if (input.artifacts.any {
                it.required && (it.sizeBytes <= 0L || it.producerExecutionId !in executionIds)
            }
        ) {
            failures += GateFailure.ARTIFACT_MISMATCH
        }

        val checks = input.compatibilityChecks.associateBy { it.id }
        if (REQUIRED_COMPATIBILITY_CHECKS.any { checks[it]?.result != EvidenceResult.PASS } ||
            input.compatibilityChecks.any { check ->
                check.result == EvidenceResult.PASS && check.expected != check.actual ||
                    check.evidenceArtifactIds.any { it !in artifacts }
            }
        ) {
            failures += GateFailure.COMPATIBILITY_FAILED
        }

        if (!input.scopeChecks.submodulesClean ||
            input.scopeChecks.forbiddenGeneratedPaths.isNotEmpty()
        ) {
            failures += GateFailure.SCOPE_FAILED
        }

        if (!input.scopeChecks.documentationSynchronized) {
            failures += GateFailure.DOCUMENTATION_NOT_SYNCHRONIZED
        }

        val ordered = GateFailure.entries.filter { it in failures }
        return GateDecision(
            result = if (ordered.isEmpty()) EvidenceResult.PASS else EvidenceResult.FAIL,
            failures = ordered,
        )
    }
}

public data class ReleaseEvidenceInput(
    val releaseId: String,
    val candidate: ReleaseCandidate,
    val contracts: ReleaseContracts,
    val executions: List<ReleaseExecution>,
    val testAggregate: TestAggregate,
    val artifacts: List<ReleaseArtifact>,
    val compatibilityChecks: List<ReleaseCheck>,
    val scopeChecks: ScopeChecks,
    val policy: ReleasePolicy = ReleasePolicy(),
)

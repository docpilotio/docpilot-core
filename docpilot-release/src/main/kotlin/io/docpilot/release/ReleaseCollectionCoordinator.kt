package io.docpilot.release

import java.nio.file.Files
import java.nio.file.Path

public data class ExecutionPlan(
    val id: String,
    val kind: String,
    val arguments: List<String>,
    val workingDirectory: String = ".",
    val required: Boolean = true,
    val outputArtifactIds: List<String> = emptyList(),
    val cleanTest: Boolean = false,
)

public data class ArtifactPlan(
    val id: String,
    val kind: ArtifactKind,
    val path: String,
    val required: Boolean,
    val producerExecutionId: String,
)

public data class ReleaseCollectionRequest(
    val repositoryRoot: Path,
    val outputDirectory: Path,
    val releaseId: String,
    val expectedCommit: String,
    val contracts: ReleaseContracts,
    val executions: List<ExecutionPlan>,
    val junitRoots: List<String>,
    val artifacts: List<ArtifactPlan>,
    val compatibilityChecks: List<ReleaseCheck>,
    val documentationSynchronized: Boolean,
    val comparisonCommit: String? = null,
)

public sealed interface ReleaseCollectionResult {
    public data class Saved(
        val manifestPath: Path,
        val manifest: ReleaseEvidenceManifest,
    ) : ReleaseCollectionResult

    public data class Failed(
        val reason: GateFailure,
        val message: String,
    ) : ReleaseCollectionResult
}

public class ReleaseCollectionCoordinator(
    private val git: GitCandidateInspector = GitCandidateInspector(),
    private val runner: CommandRunner = CommandRunner(),
    private val junit: JunitXmlAggregator = JunitXmlAggregator(),
    private val artifactCollector: ArtifactCollector = ArtifactCollector(),
    private val codec: ReleaseEvidenceCodec = ReleaseEvidenceCodec(),
    private val repository: ReleaseEvidenceRepository = ReleaseEvidenceRepository(),
) {
    public fun collect(request: ReleaseCollectionRequest): ReleaseCollectionResult {
        val root = request.repositoryRoot.toAbsolutePath().normalize()
        val before = git.inspect(root)
        if (before.commit != request.expectedCommit) {
            return ReleaseCollectionResult.Failed(
                GateFailure.CANDIDATE_MISMATCH,
                "HEAD does not equal the requested candidate commit.",
            )
        }
        if (!before.clean || before.operationInProgress) {
            return ReleaseCollectionResult.Failed(
                GateFailure.DIRTY_REPOSITORY,
                "Release collection requires a clean, operation-free worktree.",
            )
        }

        val executions = mutableListOf<ReleaseExecution>()
        var cleanTestStarted = 0L
        request.executions.sortedBy { it.id }.forEach { plan ->
            if (plan.cleanTest) cleanTestStarted = System.currentTimeMillis()
            val workingDirectory = root.resolve(plan.workingDirectory).normalize()
            if (!workingDirectory.startsWith(root) || !Files.isDirectory(workingDirectory)) {
                return ReleaseCollectionResult.Failed(
                    GateFailure.INVALID_MANIFEST,
                    "Execution working directory escapes the repository.",
                )
            }
            val result = runner.run(workingDirectory, plan.arguments)
            executions += ReleaseExecution(
                plan.id,
                plan.kind,
                plan.arguments,
                root.relativize(workingDirectory).toString().replace('\\', '/').ifBlank { "." },
                result.exitCode,
                if (result.exitCode == 0) EvidenceResult.PASS else EvidenceResult.FAIL,
                plan.required,
                plan.outputArtifactIds,
            )
            if (plan.required && result.exitCode != 0) {
                return ReleaseCollectionResult.Failed(
                    GateFailure.EXECUTION_FAILED,
                    "Required execution failed: ${plan.id}",
                )
            }
        }
        if (cleanTestStarted == 0L) {
            return ReleaseCollectionResult.Failed(
                GateFailure.MISSING_EXECUTION,
                "A clean-test execution plan is required.",
            )
        }

        val testAggregate = try {
            junit.aggregate(
                request.junitRoots.map { relative ->
                    require(relative.isSafeRelativePath()) { "Unsafe JUnit root: $relative" }
                    root.resolve(relative).normalize()
                },
                cleanTestStarted,
            )
        } catch (failure: Exception) {
            return ReleaseCollectionResult.Failed(
                GateFailure.INVALID_TEST_AGGREGATE,
                failure.message ?: "JUnit XML aggregation failed.",
            )
        }

        val artifacts = try {
            request.artifacts.sortedBy { it.id }.map { plan ->
                require(plan.path.isSafeRelativePath()) { "Unsafe artifact path: ${plan.path}" }
                artifactCollector.collect(
                    root, plan.id, plan.kind, root.resolve(plan.path),
                    plan.required, plan.producerExecutionId,
                )
            }
        } catch (failure: Exception) {
            return ReleaseCollectionResult.Failed(
                GateFailure.MISSING_ARTIFACT,
                failure.message ?: "Artifact collection failed.",
            )
        }

        val after = git.inspect(root)
        if (after.commit != before.commit || !after.clean || after.operationInProgress) {
            return ReleaseCollectionResult.Failed(
                GateFailure.REPOSITORY_CHANGED_DURING_COLLECTION,
                "Repository identity or state changed during collection.",
            )
        }
        val scope = ScopeChecks(
            comparisonCommit = request.comparisonCommit,
            changedPaths = request.comparisonCommit?.let { git.diffPaths(root, it) } ?: emptyList(),
            forbiddenGeneratedPaths = emptyList(),
            repositoryClean = true,
            submodulesClean = true,
            documentationSynchronized = request.documentationSynchronized,
        )
        val input = ReleaseEvidenceInput(
            releaseId = request.releaseId,
            candidate = ReleaseCandidate(before.commit, before.branch, true),
            contracts = request.contracts,
            executions = executions,
            testAggregate = testAggregate,
            artifacts = artifacts,
            compatibilityChecks = request.compatibilityChecks,
            scopeChecks = scope,
        )
        val manifest = codec.create(input)
        if (manifest.gate.result != EvidenceResult.PASS) {
            return ReleaseCollectionResult.Failed(
                manifest.gate.failures.first(),
                "Release policy failed: ${manifest.gate.failures.joinToString()}",
            )
        }
        return try {
            ReleaseCollectionResult.Saved(repository.saveNew(request.outputDirectory, manifest), manifest)
        } catch (failure: Exception) {
            ReleaseCollectionResult.Failed(
                GateFailure.INVALID_MANIFEST,
                failure.message ?: "Evidence publication failed.",
            )
        }
    }
}

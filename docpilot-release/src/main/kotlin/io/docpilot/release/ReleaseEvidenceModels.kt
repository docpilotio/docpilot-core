package io.docpilot.release

public object ReleaseEvidenceFormat {
    public const val CURRENT_VERSION: Int = 1
    public const val POLICY_ID: String = "DOCPILOT_V0_5"
    public const val POLICY_VERSION: Int = 1
}

public enum class EvidenceResult { PASS, FAIL }

public enum class McpMode { EMBEDDED }

public data class ReleaseCandidate(
    val coreCommit: String,
    val branch: String,
    val repositoryClean: Boolean,
    val mcpMode: McpMode,
    val mcpCommit: String,
    val mcpVersion: String,
) {
    init {
        require(coreCommit.isCommit()) { "Core commit must be 40 lowercase hexadecimal characters." }
        require(branch.isNotBlank()) { "Candidate branch must not be blank." }
        require(mcpCommit.isCommit()) { "MCP commit must be 40 lowercase hexadecimal characters." }
        require(mcpVersion.matches(SEMVER)) { "MCP version must use semantic version format." }
        require(mcpMode != McpMode.EMBEDDED || mcpCommit == coreCommit) {
            "Embedded MCP commit must equal Core commit."
        }
    }
}

public data class ReleaseContracts(
    val dirSchemaVersion: String,
    val specificationSnapshotFormatVersion: Int,
    val reviewBundleFormatVersion: Int,
    val cliOutputFormatVersion: Int,
    val cliExitCodeContractVersion: Int,
) {
    init {
        require(dirSchemaVersion.isNotBlank())
        require(specificationSnapshotFormatVersion > 0)
        require(reviewBundleFormatVersion > 0)
        require(cliOutputFormatVersion > 0)
        require(cliExitCodeContractVersion > 0)
    }
}

public data class ReleaseExecution(
    val id: String,
    val kind: String,
    val commandArguments: List<String>,
    val workingDirectory: String,
    val exitCode: Int,
    val result: EvidenceResult,
    val required: Boolean,
    val outputArtifactIds: List<String> = emptyList(),
) {
    init {
        require(id.isStableId())
        require(kind.isStableId())
        require(commandArguments.isNotEmpty())
        require(workingDirectory.isSafeRelativePath(allowDot = true))
        require(outputArtifactIds.distinct().size == outputArtifactIds.size)
        require(outputArtifactIds.all(String::isStableId))
    }
}

public data class TestAggregate(
    val xmlFileCount: Int,
    val tests: Int,
    val failures: Int,
    val errors: Int,
    val skipped: Int,
    val fresh: Boolean,
    val cached: Boolean,
) {
    init {
        require(listOf(xmlFileCount, tests, failures, errors, skipped).all { it >= 0 })
    }
}

public enum class ArtifactKind {
    JUNIT_XML_SET,
    CLI_JSON_RESULT,
    REVIEW_BUNDLE,
    APPLIED_DOCUMENTATION,
    DIFF_CHECK_RESULT,
    SCOPE_CHECK_RESULT,
    DOCUMENTATION_SYNC_RESULT,
    BUILD_ARCHIVE,
}

public data class ReleaseArtifact(
    val id: String,
    val kind: ArtifactKind,
    val path: String,
    val sizeBytes: Long,
    val sha256: String,
    val required: Boolean,
    val producerExecutionId: String,
) {
    init {
        require(id.isStableId())
        require(path.isSafeRelativePath())
        require(sizeBytes >= 0)
        require(sha256.isSha256())
        require(producerExecutionId.isStableId())
    }
}

public data class ReleaseCheck(
    val id: String,
    val expected: String,
    val actual: String,
    val result: EvidenceResult,
    val evidenceArtifactIds: List<String> = emptyList(),
) {
    init {
        require(id.isStableId())
        require(expected.isNotBlank())
        require(actual.isNotBlank())
        require(evidenceArtifactIds.distinct().size == evidenceArtifactIds.size)
        require(evidenceArtifactIds.all(String::isStableId))
    }
}

public data class ScopeChecks(
    val comparisonCommit: String?,
    val changedPaths: List<String>,
    val forbiddenGeneratedPaths: List<String>,
    val repositoryClean: Boolean,
    val submodulesClean: Boolean,
    val documentationSynchronized: Boolean,
) {
    init {
        require(comparisonCommit == null || comparisonCommit.isCommit())
        require(changedPaths.distinct().size == changedPaths.size)
        require(forbiddenGeneratedPaths.distinct().size == forbiddenGeneratedPaths.size)
        require(changedPaths.all { it.isSafeRelativePath() })
        require(forbiddenGeneratedPaths.all { it.isSafeRelativePath() })
    }
}

public data class ReleasePolicy(
    val id: String = ReleaseEvidenceFormat.POLICY_ID,
    val version: Int = ReleaseEvidenceFormat.POLICY_VERSION,
    val requireZeroSkipped: Boolean = true,
    val requireFreshTests: Boolean = true,
    val allowCachedTests: Boolean = false,
) {
    init {
        require(id == ReleaseEvidenceFormat.POLICY_ID)
        require(version == ReleaseEvidenceFormat.POLICY_VERSION)
        require(requireZeroSkipped)
        require(requireFreshTests)
        require(!allowCachedTests)
    }
}

public enum class GateFailure {
    UNSUPPORTED_FORMAT,
    INVALID_MANIFEST,
    INTEGRITY_MISMATCH,
    CANDIDATE_MISMATCH,
    DIRTY_REPOSITORY,
    MCP_IDENTITY_MISMATCH,
    MISSING_EXECUTION,
    EXECUTION_FAILED,
    CACHED_TEST_EVIDENCE,
    INVALID_TEST_AGGREGATE,
    TEST_FAILURES,
    TEST_ERRORS,
    TESTS_SKIPPED,
    MISSING_ARTIFACT,
    ARTIFACT_MISMATCH,
    COMPATIBILITY_FAILED,
    SCOPE_FAILED,
    DOCUMENTATION_NOT_SYNCHRONIZED,
    REPOSITORY_CHANGED_DURING_COLLECTION,
}

public data class GateDecision(
    val result: EvidenceResult,
    val failures: List<GateFailure>,
) {
    init {
        require(failures.distinct().size == failures.size)
        require((result == EvidenceResult.PASS) == failures.isEmpty())
    }
}

public data class ReleaseIntegrity(
    val algorithm: String = "SHA-256",
    val payloadSha256: String,
) {
    init {
        require(algorithm == "SHA-256")
        require(payloadSha256.isSha256())
    }
}

public data class ReleaseEvidenceManifest(
    val releaseEvidenceFormatVersion: Int = ReleaseEvidenceFormat.CURRENT_VERSION,
    val releaseId: String,
    val candidate: ReleaseCandidate,
    val contracts: ReleaseContracts,
    val executions: List<ReleaseExecution>,
    val testAggregate: TestAggregate,
    val artifacts: List<ReleaseArtifact>,
    val compatibilityChecks: List<ReleaseCheck>,
    val scopeChecks: ScopeChecks,
    val policy: ReleasePolicy = ReleasePolicy(),
    val gate: GateDecision,
    val integrity: ReleaseIntegrity,
) {
    init {
        require(releaseEvidenceFormatVersion == ReleaseEvidenceFormat.CURRENT_VERSION)
        require(releaseId.matches(RELEASE_ID)) {
            "Release ID must match v<major>.<minor>.<patch>-rc.<positive-integer>."
        }
        requireUnique(executions.map { it.id }, "execution")
        requireUnique(artifacts.map { it.id }, "artifact")
        requireUnique(artifacts.map { it.path }, "artifact path")
        requireUnique(compatibilityChecks.map { it.id }, "compatibility check")
    }
}

internal val REQUIRED_EXECUTIONS = listOf(
    "cli-exit-code-contract",
    "cli-json-contract",
    "cli-review-smoke",
    "core-clean-build",
    "core-clean-test",
    "diff-check",
    "documentation-sync-check",
    "protected-scope-check",
)

internal val REQUIRED_COMPATIBILITY_CHECKS = listOf(
    "CLI_EXIT_CODE_CONTRACT_1",
    "CLI_JSON_OUTPUT_FORMAT_1",
    "CORE_HAS_NO_MCP_RUNTIME_DEPENDENCY",
    "DIR_SCHEMA_0_3",
    "MCP_EMBEDDED_IDENTITY",
    "REVIEW_BUNDLE_FORMAT_1",
    "RFC_0046_REMOVE_SEMANTICS",
    "RFC_0047_INTEGRITY_AND_STALE_APPLY",
    "RFC_0048_THIN_CLI_ADAPTER",
    "SPECIFICATION_SNAPSHOT_FORMAT_1",
)

internal val REQUIRED_ARTIFACT_KINDS = setOf(
    ArtifactKind.JUNIT_XML_SET,
    ArtifactKind.CLI_JSON_RESULT,
    ArtifactKind.REVIEW_BUNDLE,
    ArtifactKind.APPLIED_DOCUMENTATION,
    ArtifactKind.DIFF_CHECK_RESULT,
    ArtifactKind.SCOPE_CHECK_RESULT,
    ArtifactKind.DOCUMENTATION_SYNC_RESULT,
)

private val SHA256 = Regex("[0-9a-f]{64}")
private val COMMIT = Regex("[0-9a-f]{40}")
private val STABLE_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
private val RELEASE_ID = Regex("v\\d+\\.\\d+\\.\\d+-rc\\.[1-9]\\d*")
private val SEMVER = Regex("\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?")

internal fun String.isSha256(): Boolean = matches(SHA256)
internal fun String.isCommit(): Boolean = matches(COMMIT)
internal fun String.isStableId(): Boolean = matches(STABLE_ID)
internal fun String.isSafeRelativePath(allowDot: Boolean = false): Boolean {
    if (allowDot && this == ".") return true
    if (isBlank() || startsWith("/") || startsWith("\\") || Regex("^[A-Za-z]:").containsMatchIn(this)) return false
    val segments = replace('\\', '/').split('/')
    return segments.none { it.isBlank() || it == "." || it == ".." }
}

private fun requireUnique(values: List<String>, label: String) {
    require(values.distinct().size == values.size) { "Duplicate $label." }
}

package io.docpilot.release

internal fun passingInput(
    artifactSha: String = "a".repeat(64),
    artifactSize: Long = 1,
): ReleaseEvidenceInput {
    val executions = REQUIRED_EXECUTIONS.map { id ->
        ReleaseExecution(
            id = id,
            kind = id.uppercase().replace('-', '_'),
            commandArguments = listOf("tool", id),
            workingDirectory = ".",
            exitCode = 0,
            result = EvidenceResult.PASS,
            required = true,
            outputArtifactIds = emptyList(),
        )
    }
    val producer = executions.first().id
    val artifacts = REQUIRED_ARTIFACT_KINDS.sortedBy { it.name }.mapIndexed { index, kind ->
        ReleaseArtifact(
            id = "artifact-$index",
            kind = kind,
            path = "build/evidence/artifact-$index.txt",
            sizeBytes = artifactSize,
            sha256 = artifactSha,
            required = true,
            producerExecutionId = producer,
        )
    }
    val checks = REQUIRED_COMPATIBILITY_CHECKS.map { id ->
        ReleaseCheck(id, "PASS", "PASS", EvidenceResult.PASS)
    }
    return ReleaseEvidenceInput(
        releaseId = "v0.5.0-rc.1",
        candidate = ReleaseCandidate(
            "1".repeat(40), "main", true, McpMode.EMBEDDED, "1".repeat(40), "0.12.3",
        ),
        contracts = ReleaseContracts("0.3", 1, 1, 1, 1),
        executions = executions,
        testAggregate = TestAggregate(89, 273, 0, 0, 0, fresh = true, cached = false),
        artifacts = artifacts,
        compatibilityChecks = checks,
        scopeChecks = ScopeChecks(null, emptyList(), emptyList(), true, true, true),
    )
}

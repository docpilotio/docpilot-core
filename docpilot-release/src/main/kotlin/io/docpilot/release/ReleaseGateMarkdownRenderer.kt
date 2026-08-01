package io.docpilot.release

public class ReleaseGateMarkdownRenderer {
    public fun render(manifest: ReleaseEvidenceManifest): String = buildString {
        appendLine("# DocPilot Release Gate Report")
        appendLine()
        appendLine("## Release Candidate")
        appendLine()
        appendLine("- Release ID: `${manifest.releaseId}`")
        appendLine("- Gate: **${manifest.gate.result}**")
        appendLine()
        appendLine("## Source Identity")
        appendLine()
        appendLine("- Core Commit: `${manifest.candidate.coreCommit}`")
        appendLine("- Branch: `${manifest.candidate.branch}`")
        appendLine("- Repository Clean: `${manifest.candidate.repositoryClean}`")
        appendLine()
        appendLine("## Public Contracts")
        appendLine()
        appendLine("| Contract | Version |")
        appendLine("| --- | --- |")
        appendLine("| DIR Schema | ${manifest.contracts.dirSchemaVersion} |")
        appendLine("| Specification Snapshot | ${manifest.contracts.specificationSnapshotFormatVersion} |")
        appendLine("| Review Bundle | ${manifest.contracts.reviewBundleFormatVersion} |")
        appendLine("| CLI JSON | ${manifest.contracts.cliOutputFormatVersion} |")
        appendLine("| CLI Exit Codes | ${manifest.contracts.cliExitCodeContractVersion} |")
        appendLine()
        appendLine("## Build and Tests")
        appendLine()
        appendLine("| XML | Tests | Failures | Errors | Skipped | Fresh | Cached |")
        appendLine("| ---: | ---: | ---: | ---: | ---: | --- | --- |")
        with(manifest.testAggregate) {
            appendLine("| $xmlFileCount | $tests | $failures | $errors | $skipped | $fresh | $cached |")
        }
        appendLine()
        appendLine("## CLI Verification")
        appendLine()
        manifest.executions.filter { it.id.startsWith("cli-") }.sortedBy { it.id }.forEach {
            appendLine("- `${it.id}`: ${it.result} (exit ${it.exitCode})")
        }
        appendLine()
        appendLine("## Artifacts")
        appendLine()
        appendLine("| ID | Kind | Bytes | SHA-256 |")
        appendLine("| --- | --- | ---: | --- |")
        manifest.artifacts.sortedBy { it.id }.forEach {
            appendLine("| ${it.id.escape()} | ${it.kind} | ${it.sizeBytes} | `${it.sha256}` |")
        }
        appendLine()
        appendLine("## Compatibility")
        appendLine()
        manifest.compatibilityChecks.sortedBy { it.id }.forEach {
            appendLine("- `${it.id}`: ${it.result}")
        }
        appendLine()
        appendLine("## Scope")
        appendLine()
        appendLine("- Repository Clean: `${manifest.scopeChecks.repositoryClean}`")
        appendLine("- Submodules Clean: `${manifest.scopeChecks.submodulesClean}`")
        appendLine("- Forbidden Generated Paths: `${manifest.scopeChecks.forbiddenGeneratedPaths.size}`")
        appendLine()
        appendLine("## Documentation")
        appendLine()
        appendLine("- Synchronization: `${manifest.scopeChecks.documentationSynchronized}`")
        appendLine()
        appendLine("## Gate Decision")
        appendLine()
        appendLine("**${manifest.gate.result}**")
        appendLine()
        appendLine("## Failure Reasons")
        appendLine()
        if (manifest.gate.failures.isEmpty()) appendLine("- None")
        else manifest.gate.failures.forEach { appendLine("- `$it`") }
        appendLine()
        appendLine("## Manifest SHA-256")
        appendLine()
        appendLine("`${manifest.integrity.payloadSha256}`")
    }

    private fun String.escape(): String = replace("|", "\\|")
}

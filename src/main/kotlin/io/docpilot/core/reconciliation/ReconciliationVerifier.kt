package io.docpilot.core.reconciliation

public class ReconciliationVerifier {
    public fun verify(manifest: DocumentationOwnershipManifest): Boolean =
        ReconciliationIntegrity.verifyManifest(manifest) &&
            ReconciliationIntegrity.safePath(manifest.relativePath) &&
            manifest.managedBlocks.map { it.blockId }.let { it == it.distinct().sorted() }

    public fun verify(result: DocumentationReconciliationResult): Boolean {
        if (result.formatVersion != 1 || result.planSha256.length != 64) return false
        if (result.appliedOperationIds != result.appliedOperationIds.distinct().sorted()) return false
        if (result.retainedOperationIds != result.retainedOperationIds.distinct().sorted()) return false
        if ((result.appliedOperationIds.toSet() intersect result.retainedOperationIds.toSet()).isNotEmpty()) return false
        if (result.afterDocumentShaByPath.any { (path, sha) ->
                !ReconciliationIntegrity.safePath(path) || !sha.matches(Regex("[0-9a-f]{64}"))
            }
        ) return false
        val body = buildString {
            append(result.planSha256).append('\n')
            append(result.appliedOperationIds).append('\n')
                .append(result.retainedOperationIds).append('\n')
                .append(result.afterDocumentShaByPath.toSortedMap())
        }
        if (result.resultSha256 != ReconciliationIntegrity.sha256(body)) return false
        return verifyReport(result.explanationReport, result.planSha256, result.resultSha256)
    }

    public fun verify(plan: DocumentationReconciliationPlan): Boolean {
        if (plan.formatVersion != 1 || plan.projectId.isBlank()) return false
        val payload = buildString {
            append(plan.projectId).append('\n')
            plan.operations.sortedBy { it.operationId }.forEach {
                append(it.operationId).append('|').append(it.artifactId.value).append('|')
                    .append(it.relativePath).append('|').append(it.kind.name).append('|')
                    .append(it.ownership.name).append('|').append(it.expectedCurrentSha256 ?: "").append('|')
                    .append(it.resultSha256 ?: "").append('|')
                    .append(it.resultManifest?.manifestSha256 ?: "").append('|')
                    .append(it.expectedManifestSha256 ?: "").append('|')
                    .append(it.requiresDecision).append('|')
                    .append(it.ruleIds.sorted()).append('|').append(it.evidenceRefs.sorted()).append('\n')
            }
            plan.conflicts.sortedBy { it.conflictId }.forEach {
                append(it.conflictId).append('|').append(it.kind.name).append('|')
                    .append(it.relativePath).append('|').append(it.evidenceRefs.sorted()).append('\n')
            }
        }
        return plan.planSha256 == ReconciliationIntegrity.sha256(payload) &&
            plan.operations.all { it.resultManifest?.let(::verify) != false } &&
            verifyReport(plan.explanationReport, plan.planSha256, null)
    }

    public fun verifyOffline(
        result: DocumentationReconciliationResult,
        documents: Map<String, String>,
        manifests: List<DocumentationOwnershipManifest>,
    ): Boolean = verify(result) &&
        result.afterDocumentShaByPath.all { (path, sha) ->
            documents[path]?.let(ReconciliationIntegrity::sha256) == sha
        } &&
        manifests.all(::verify)

    internal fun verifyReport(
        report: ReconciliationExplanationReport,
        planSha: String,
        resultSha: String?,
    ): Boolean {
        val payload = buildString {
            append(planSha).append('|').append(resultSha ?: "").append('\n')
            report.explanations.sortedBy { it.decisionId }.forEach {
                append(it.decisionId).append('|').append(it.subjectId).append('|').append(it.outcome).append('|')
                    .append(it.ruleIds).append('|').append(it.evidenceRefs).append('|')
                    .append(it.baseSha256 ?: "").append('|').append(it.currentSha256 ?: "").append('|')
                    .append(it.candidateSha256).append('|').append(it.causedOperationIds).append('\n')
            }
        }
        return report.formatVersion == 1 && report.planSha256 == planSha &&
            report.resultSha256 == resultSha &&
            report.reportSha256 == ReconciliationIntegrity.sha256(payload)
    }
}

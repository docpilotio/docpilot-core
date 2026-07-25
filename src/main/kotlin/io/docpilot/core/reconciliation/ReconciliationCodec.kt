package io.docpilot.core.reconciliation

import io.docpilot.core.api.DocumentationArtifactId
import java.util.Base64

public class ReconciliationCodec {
    public fun encodePlan(value: DocumentationReconciliationPlan): String {
        require(ReconciliationVerifier().verify(value)) { "Plan integrity mismatch." }
        return buildString {
            line("format", value.formatVersion.toString())
            line("project", value.projectId)
            value.operations.sortedBy { it.operationId }.forEach {
                line(
                    "operation", it.operationId, it.artifactId.value, it.relativePath, it.kind.name,
                    it.ownership.name, it.expectedCurrentSha256.orEmpty(), it.resultContent.orEmpty(),
                    it.resultSha256.orEmpty(), it.resultManifest?.let(::encodeManifest).orEmpty(),
                    it.requiresDecision.toString(), it.ruleIds.sorted().joinToString("\u001f"),
                    it.evidenceRefs.sorted().joinToString("\u001f"), it.expectedManifestSha256.orEmpty(),
                )
            }
            value.conflicts.sortedBy { it.conflictId }.forEach {
                line("conflict", it.conflictId, it.artifactId.value, it.relativePath, it.kind.name,
                    it.message, it.evidenceRefs.sorted().joinToString("\u001f"))
            }
            value.explanationReport.explanations.sortedBy { it.decisionId }.forEach {
                encodeExplanation(it)
            }
            line("report", value.explanationReport.formatVersion.toString(), value.explanationReport.planSha256,
                "", value.explanationReport.reportSha256)
            line("plan", value.planSha256)
        }
    }

    public fun decodePlan(encoded: String): DocumentationReconciliationPlan {
        val records = records(encoded)
        val reportRecord = many(records, "report").single()
        val explanations = many(records, "explanation").map(::decodeExplanation)
        val plan = DocumentationReconciliationPlan(
            formatVersion = one(records, "format").toInt(),
            projectId = one(records, "project"),
            operations = many(records, "operation").map {
                require(it.size == 13) { "Invalid operation record." }
                ReconciliationOperation(
                    it[0], DocumentationArtifactId(it[1]), it[2], ReconciliationOperationKind.valueOf(it[3]),
                    DocumentationOwnership.valueOf(it[4]), it[5].ifEmpty { null }, it[6].ifEmpty { null },
                    it[7].ifEmpty { null }, it[8].ifEmpty { null }?.let(::decodeManifest),
                    it[9].toBooleanStrict(), split(it[10]), split(it[11]), it[12].ifEmpty { null },
                )
            },
            conflicts = many(records, "conflict").map {
                require(it.size == 6) { "Invalid conflict record." }
                ReconciliationConflict(it[0], DocumentationArtifactId(it[1]), it[2],
                    ReconciliationConflictKind.valueOf(it[3]), it[4], split(it[5]))
            },
            planSha256 = one(records, "plan"),
            explanationReport = ReconciliationExplanationReport(
                reportRecord[0].toInt(), reportRecord[1], null, explanations, reportRecord[3],
            ),
        )
        require(ReconciliationVerifier().verify(plan)) { "Plan integrity mismatch." }
        return plan
    }

    public fun encodeManifest(value: DocumentationOwnershipManifest): String {
        require(ReconciliationIntegrity.verifyManifest(value)) { "Manifest integrity mismatch." }
        return buildString {
            line("format", value.formatVersion.toString())
            line("artifact", value.artifactId.value)
            line("path", value.relativePath)
            line("media", value.mediaType)
            line("ownership", value.ownership.name)
            line("base", value.reviewedBaseSha256.orEmpty())
            line("renderer", value.rendererIdentity)
            value.evidenceRefs.distinct().sorted().forEach { line("evidence", it) }
            value.managedBlocks.sortedBy { it.blockId }.forEach {
                line("block", it.blockId, it.targetId, it.reviewedBaseContentSha256, it.lastAppliedContentSha256)
            }
            line("sha", value.manifestSha256)
        }
    }

    public fun decodeManifest(encoded: String): DocumentationOwnershipManifest {
        val records = records(encoded)
        val manifest = DocumentationOwnershipManifest(
            formatVersion = one(records, "format").toInt(),
            artifactId = DocumentationArtifactId(one(records, "artifact")),
            relativePath = one(records, "path"),
            mediaType = one(records, "media"),
            ownership = DocumentationOwnership.valueOf(one(records, "ownership")),
            reviewedBaseSha256 = one(records, "base").ifEmpty { null },
            rendererIdentity = one(records, "renderer"),
            evidenceRefs = many(records, "evidence").map { it.single() },
            managedBlocks = many(records, "block").map {
                require(it.size == 4) { "Invalid managed block record." }
                ManagedBlockOwnership(it[0], it[1], it[2], it[3])
            },
            manifestSha256 = one(records, "sha"),
        )
        require(manifest.formatVersion == 1 && ReconciliationIntegrity.verifyManifest(manifest)) {
            "Manifest integrity mismatch."
        }
        return manifest
    }

    public fun encodeResult(value: DocumentationReconciliationResult): String = buildString {
        line("format", value.formatVersion.toString())
        line("plan", value.planSha256)
        value.appliedOperationIds.sorted().forEach { line("applied", it) }
        value.retainedOperationIds.sorted().forEach { line("retained", it) }
        value.afterDocumentShaByPath.toSortedMap().forEach { (path, sha) -> line("document", path, sha) }
        value.explanationReport.explanations.sortedBy { it.decisionId }.forEach { encodeExplanation(it) }
        line("report", value.explanationReport.formatVersion.toString(), value.explanationReport.planSha256,
            value.explanationReport.resultSha256.orEmpty(), value.explanationReport.reportSha256)
        line("result", value.resultSha256)
    }

    public fun decodeResult(encoded: String): DocumentationReconciliationResult {
        val records = records(encoded)
        val reportRecord = many(records, "report").single()
        require(reportRecord.size == 4) { "Invalid explanation report record." }
        val explanations = many(records, "explanation").map(::decodeExplanation)
        val report = ReconciliationExplanationReport(
            reportRecord[0].toInt(), reportRecord[1], reportRecord[2].ifEmpty { null },
            explanations, reportRecord[3],
        )
        return DocumentationReconciliationResult(
            formatVersion = one(records, "format").toInt(),
            planSha256 = one(records, "plan"),
            appliedOperationIds = many(records, "applied").map { it.single() },
            retainedOperationIds = many(records, "retained").map { it.single() },
            afterDocumentShaByPath = many(records, "document").associate {
                require(it.size == 2)
                it[0] to it[1]
            }.toSortedMap(),
            resultSha256 = one(records, "result"),
            explanationReport = report,
        ).also { require(ReconciliationVerifier().verify(it)) { "Result integrity mismatch." } }
    }

    private fun StringBuilder.line(kind: String, vararg values: String) {
        append(kind)
        values.forEach { append('|').append(Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8))) }
        append('\n')
    }

    private fun records(value: String): List<Pair<String, List<String>>> {
        require(value.endsWith('\n')) { "Canonical reconciliation record must end with newline." }
        return value.lineSequence().filter(String::isNotEmpty).map { line ->
            val fields = line.split('|')
            require(fields.size >= 2 && fields[0].matches(Regex("[a-z]+"))) { "Invalid reconciliation record." }
            fields[0] to fields.drop(1).map {
                String(Base64.getDecoder().decode(it), Charsets.UTF_8)
            }
        }.toList()
    }

    private fun one(records: List<Pair<String, List<String>>>, kind: String): String =
        many(records, kind).single().single()

    private fun many(records: List<Pair<String, List<String>>>, kind: String): List<List<String>> =
        records.filter { it.first == kind }.map { it.second }

    private fun split(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split('\u001f')

    private fun StringBuilder.encodeExplanation(value: DecisionExplanation) {
        line(
            "explanation", value.decisionId, value.subjectId, value.outcome,
            value.ruleIds.sorted().joinToString("\u001f"), value.evidenceRefs.sorted().joinToString("\u001f"),
            value.baseSha256.orEmpty(), value.currentSha256.orEmpty(), value.candidateSha256,
            value.causedOperationIds.sorted().joinToString("\u001f"),
        )
    }

    private fun decodeExplanation(value: List<String>): DecisionExplanation {
        require(value.size == 9) { "Invalid explanation record." }
        return DecisionExplanation(
            value[0], value[1], value[2], split(value[3]), split(value[4]), value[5].ifEmpty { null },
            value[6].ifEmpty { null }, value[7], split(value[8]),
        )
    }
}

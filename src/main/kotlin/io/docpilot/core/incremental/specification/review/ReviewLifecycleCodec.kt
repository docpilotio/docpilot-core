package io.docpilot.core.incremental.specification.review

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

public class ReviewLifecycleCodec {
    public fun createActive(bundle: StoredReviewBundle, generation: Long = 1): ReviewLifecycleMetadata =
        metadata(
            projectId = bundle.projectIdentity.projectId,
            proposalId = bundle.proposalId,
            bundleSha = bundle.integrity.payloadSha256,
            generation = generation,
            state = ReviewLifecycleState.ACTIVE,
        )

    public fun metadata(
        projectId: String,
        proposalId: String,
        bundleSha: String,
        generation: Long,
        state: ReviewLifecycleState,
        transactionId: String? = null,
        receiptId: String? = null,
        supersededBy: String? = null,
        archivedFrom: ReviewLifecycleState? = null,
    ): ReviewLifecycleMetadata {
        requireIdentity(projectId, proposalId, bundleSha, generation)
        val payload = listOf(
            "1", projectId, proposalId, "1", bundleSha, generation.toString(), state.name,
            transactionId.orEmpty(), receiptId.orEmpty(), supersededBy.orEmpty(), archivedFrom?.name.orEmpty(),
        ).joinToString("\u001f")
        return ReviewLifecycleMetadata(
            projectId = projectId,
            proposalId = proposalId,
            observedBundlePayloadSha256 = bundleSha,
            generation = generation,
            state = state,
            activeTransactionId = transactionId,
            applyReceiptId = receiptId,
            supersededByProposalId = supersededBy,
            archivedFrom = archivedFrom,
            integrity = ReviewLifecycleIntegrity(sha256(payload)),
        )
    }

    public fun receipt(
        bundle: StoredReviewBundle,
        resultSha: String,
    ): ApplyReceipt {
        require(resultSha.isSha256())
        val accepted = bundle.decisions.filter { it.disposition == DocumentationReviewDisposition.ACCEPTED }
            .map { it.targetId }.sorted()
        val rejected = bundle.decisions.filter { it.disposition == DocumentationReviewDisposition.REJECTED }
            .map { it.targetId }.sorted()
        val operations = bundle.decisions.sortedBy { it.targetId }
            .map { ApplyReceiptOperation(it.targetId, it.disposition) }
        val body = listOf(
            bundle.projectIdentity.projectId,
            bundle.proposalId,
            bundle.integrity.payloadSha256,
            bundle.proposal.reviewedDocumentationSha256,
            resultSha,
            accepted.joinToString("\u001e"),
            rejected.joinToString("\u001e"),
            operations.joinToString("\u001e") { "${it.targetId}:${it.disposition}" },
        ).joinToString("\u001f")
        val receiptId = "receipt:${sha256(body)}"
        return ApplyReceipt(
            receiptId = receiptId,
            projectId = bundle.projectIdentity.projectId,
            proposalId = bundle.proposalId,
            bundlePayloadSha256 = bundle.integrity.payloadSha256,
            reviewedDocumentationSha256 = bundle.proposal.reviewedDocumentationSha256,
            resultDocumentationSha256 = resultSha,
            acceptedTargetIds = accepted,
            rejectedTargetIds = rejected,
            operations = operations,
            integrity = ReviewLifecycleIntegrity(sha256("1\u001f$receiptId\u001f$body")),
        )
    }

    public fun transaction(
        bundle: StoredReviewBundle,
        generation: Long,
        resultSha: String,
        receiptId: String,
        phase: ReviewApplyTransactionPhase,
    ): ReviewApplyTransaction {
        val idBody = "${bundle.proposalId}\u001f${bundle.integrity.payloadSha256}\u001f$generation\u001f$resultSha"
        val transactionId = "transaction:${sha256(idBody)}"
        val payload = listOf(
            "1", transactionId, bundle.projectIdentity.projectId, bundle.proposalId, generation.toString(),
            bundle.integrity.payloadSha256, bundle.proposal.reviewedDocumentationSha256, resultSha, receiptId, phase.name,
        ).joinToString("\u001f")
        return ReviewApplyTransaction(
            transactionId = transactionId,
            projectId = bundle.projectIdentity.projectId,
            proposalId = bundle.proposalId,
            expectedLifecycleGeneration = generation,
            bundlePayloadSha256 = bundle.integrity.payloadSha256,
            inputDocumentationSha256 = bundle.proposal.reviewedDocumentationSha256,
            resultDocumentationSha256 = resultSha,
            receiptId = receiptId,
            phase = phase,
            integrity = ReviewLifecycleIntegrity(sha256(payload)),
        )
    }

    public fun verify(metadata: ReviewLifecycleMetadata): Boolean =
        metadata == metadata(
            metadata.projectId, metadata.proposalId, metadata.observedBundlePayloadSha256,
            metadata.generation, metadata.state, metadata.activeTransactionId, metadata.applyReceiptId,
            metadata.supersededByProposalId, metadata.archivedFrom,
        )

    public fun verify(receipt: ApplyReceipt): Boolean {
        if (!receipt.receiptId.matches(Regex("receipt:[0-9a-f]{64}"))) return false
        val body = listOf(
            receipt.projectId, receipt.proposalId, receipt.bundlePayloadSha256,
            receipt.reviewedDocumentationSha256, receipt.resultDocumentationSha256,
            receipt.acceptedTargetIds.joinToString("\u001e"), receipt.rejectedTargetIds.joinToString("\u001e"),
            receipt.operations.joinToString("\u001e") { "${it.targetId}:${it.disposition}" },
        ).joinToString("\u001f")
        return receipt.receiptId == "receipt:${sha256(body)}" &&
            receipt.integrity.payloadSha256 == sha256("1\u001f${receipt.receiptId}\u001f$body")
    }

    public fun verify(transaction: ReviewApplyTransaction): Boolean {
        val payload = listOf(
            "1", transaction.transactionId, transaction.projectId, transaction.proposalId,
            transaction.expectedLifecycleGeneration.toString(), transaction.bundlePayloadSha256,
            transaction.inputDocumentationSha256, transaction.resultDocumentationSha256,
            transaction.receiptId, transaction.phase.name,
        ).joinToString("\u001f")
        return transaction.integrity.payloadSha256 == sha256(payload)
    }

    public fun encode(metadata: ReviewLifecycleMetadata): String {
        require(verify(metadata))
        return json(
            "formatVersion" to metadata.formatVersion,
            "projectId" to metadata.projectId,
            "proposalId" to metadata.proposalId,
            "reviewBundleFormatVersion" to metadata.reviewBundleFormatVersion,
            "observedBundlePayloadSha256" to metadata.observedBundlePayloadSha256,
            "generation" to metadata.generation,
            "state" to metadata.state.name,
            "activeTransactionId" to metadata.activeTransactionId,
            "applyReceiptId" to metadata.applyReceiptId,
            "supersededByProposalId" to metadata.supersededByProposalId,
            "archivedFrom" to metadata.archivedFrom?.name,
            "payloadSha256" to metadata.integrity.payloadSha256,
        )
    }

    public fun encode(receipt: ApplyReceipt): String {
        require(verify(receipt))
        return json(
            "formatVersion" to receipt.formatVersion,
            "receiptId" to receipt.receiptId,
            "projectId" to receipt.projectId,
            "proposalId" to receipt.proposalId,
            "bundlePayloadSha256" to receipt.bundlePayloadSha256,
            "reviewedDocumentationSha256" to receipt.reviewedDocumentationSha256,
            "resultDocumentationSha256" to receipt.resultDocumentationSha256,
            "acceptedTargetIds" to receipt.acceptedTargetIds,
            "rejectedTargetIds" to receipt.rejectedTargetIds,
            "operations" to receipt.operations.map { "${it.targetId}:${it.disposition.name}" },
            "payloadSha256" to receipt.integrity.payloadSha256,
        )
    }

    public fun encode(transaction: ReviewApplyTransaction): String {
        require(verify(transaction))
        return json(
            "formatVersion" to transaction.formatVersion,
            "transactionId" to transaction.transactionId,
            "projectId" to transaction.projectId,
            "proposalId" to transaction.proposalId,
            "expectedLifecycleGeneration" to transaction.expectedLifecycleGeneration,
            "bundlePayloadSha256" to transaction.bundlePayloadSha256,
            "inputDocumentationSha256" to transaction.inputDocumentationSha256,
            "resultDocumentationSha256" to transaction.resultDocumentationSha256,
            "receiptId" to transaction.receiptId,
            "phase" to transaction.phase.name,
            "payloadSha256" to transaction.integrity.payloadSha256,
        )
    }

    public fun decodeMetadata(value: String): ReviewLifecycleMetadata {
        requireKeys(
            value, "formatVersion", "projectId", "proposalId", "reviewBundleFormatVersion",
            "observedBundlePayloadSha256", "generation", "state", "activeTransactionId",
            "applyReceiptId", "supersededByProposalId", "archivedFrom", "payloadSha256",
        )
        val metadata = ReviewLifecycleMetadata(
            formatVersion = number(value, "formatVersion").toInt(),
            projectId = string(value, "projectId"),
            proposalId = string(value, "proposalId"),
            reviewBundleFormatVersion = number(value, "reviewBundleFormatVersion").toInt(),
            observedBundlePayloadSha256 = string(value, "observedBundlePayloadSha256"),
            generation = number(value, "generation"),
            state = ReviewLifecycleState.valueOf(string(value, "state")),
            activeTransactionId = nullableString(value, "activeTransactionId"),
            applyReceiptId = nullableString(value, "applyReceiptId"),
            supersededByProposalId = nullableString(value, "supersededByProposalId"),
            archivedFrom = nullableString(value, "archivedFrom")?.let(ReviewLifecycleState::valueOf),
            integrity = ReviewLifecycleIntegrity(string(value, "payloadSha256")),
        )
        require(metadata.formatVersion == ReviewLifecycleFormat.CURRENT_VERSION)
        require(metadata.reviewBundleFormatVersion == ReviewBundleFormat.CURRENT_VERSION)
        require(verify(metadata)) { "Lifecycle metadata integrity mismatch." }
        return metadata
    }

    public fun decodeReceipt(value: String): ApplyReceipt {
        requireKeys(
            value, "formatVersion", "receiptId", "projectId", "proposalId", "bundlePayloadSha256",
            "reviewedDocumentationSha256", "resultDocumentationSha256", "acceptedTargetIds",
            "rejectedTargetIds", "operations", "payloadSha256",
        )
        val receipt = ApplyReceipt(
            formatVersion = number(value, "formatVersion").toInt(),
            receiptId = string(value, "receiptId"),
            projectId = string(value, "projectId"),
            proposalId = string(value, "proposalId"),
            bundlePayloadSha256 = string(value, "bundlePayloadSha256"),
            reviewedDocumentationSha256 = string(value, "reviewedDocumentationSha256"),
            resultDocumentationSha256 = string(value, "resultDocumentationSha256"),
            acceptedTargetIds = stringArray(value, "acceptedTargetIds"),
            rejectedTargetIds = stringArray(value, "rejectedTargetIds"),
            operations = stringArray(value, "operations").map {
                val split = it.lastIndexOf(':')
                require(split > 0) { "Invalid receipt operation." }
                ApplyReceiptOperation(it.substring(0, split), DocumentationReviewDisposition.valueOf(it.substring(split + 1)))
            },
            integrity = ReviewLifecycleIntegrity(string(value, "payloadSha256")),
        )
        require(receipt.formatVersion == ReviewLifecycleFormat.CURRENT_VERSION)
        require(verify(receipt)) { "Apply receipt integrity mismatch." }
        return receipt
    }

    public fun decodeTransaction(value: String): ReviewApplyTransaction {
        requireKeys(
            value, "formatVersion", "transactionId", "projectId", "proposalId",
            "expectedLifecycleGeneration", "bundlePayloadSha256", "inputDocumentationSha256",
            "resultDocumentationSha256", "receiptId", "phase", "payloadSha256",
        )
        val transaction = ReviewApplyTransaction(
            formatVersion = number(value, "formatVersion").toInt(),
            transactionId = string(value, "transactionId"),
            projectId = string(value, "projectId"),
            proposalId = string(value, "proposalId"),
            expectedLifecycleGeneration = number(value, "expectedLifecycleGeneration"),
            bundlePayloadSha256 = string(value, "bundlePayloadSha256"),
            inputDocumentationSha256 = string(value, "inputDocumentationSha256"),
            resultDocumentationSha256 = string(value, "resultDocumentationSha256"),
            receiptId = string(value, "receiptId"),
            phase = ReviewApplyTransactionPhase.valueOf(string(value, "phase")),
            integrity = ReviewLifecycleIntegrity(string(value, "payloadSha256")),
        )
        require(transaction.formatVersion == ReviewLifecycleFormat.CURRENT_VERSION)
        require(verify(transaction)) { "Apply transaction integrity mismatch." }
        return transaction
    }

    private fun requireIdentity(projectId: String, proposalId: String, sha: String, generation: Long) {
        require(projectId.isNotBlank())
        require(proposalId.matches(Regex("review:[0-9a-f]{64}")))
        require(sha.isSha256())
        require(generation > 0)
    }

    private fun json(vararg fields: Pair<String, Any?>): String = buildString {
        append("{\n")
        fields.forEachIndexed { index, (name, value) ->
            append("  \"").append(name).append("\": ").append(jsonValue(value))
            if (index != fields.lastIndex) append(',')
            append('\n')
        }
        append("}\n")
    }

    private fun jsonValue(value: Any?): String = when (value) {
        null -> "null"
        is Number -> value.toString()
        is List<*> -> value.joinToString(prefix = "[", postfix = "]") { jsonValue(it) }
        else -> "\"${value.toString().replace("\\", "\\\\").replace("\"", "\\\"")}\""
    }

    private fun string(value: String, name: String): String =
        Regex("\"${Regex.escape(name)}\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
            .find(value)?.groupValues?.get(1)?.replace("\\\"", "\"")?.replace("\\\\", "\\")
            ?: error("Missing string field: $name")

    private fun nullableString(value: String, name: String): String? {
        val field = Regex("\"${Regex.escape(name)}\"\\s*:\\s*(null|\"((?:\\\\.|[^\"])*)\")")
            .find(value) ?: error("Missing nullable field: $name")
        return if (field.groupValues[1] == "null") null
        else field.groupValues[2].replace("\\\"", "\"").replace("\\\\", "\\")
    }

    private fun number(value: String, name: String): Long =
        Regex("\"${Regex.escape(name)}\"\\s*:\\s*([0-9]+)")
            .find(value)?.groupValues?.get(1)?.toLong()
            ?: error("Missing number field: $name")

    private fun stringArray(value: String, name: String): List<String> {
        val body = Regex("\"${Regex.escape(name)}\"\\s*:\\s*\\[([^]]*)]")
            .find(value)?.groupValues?.get(1) ?: error("Missing array field: $name")
        if (body.isBlank()) return emptyList()
        return Regex("\"((?:\\\\.|[^\"])*)\"").findAll(body).map {
            it.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\")
        }.toList()
    }

    private fun requireKeys(value: String, vararg expected: String) {
        val actual = Regex("\"([A-Za-z][A-Za-z0-9]*)\"\\s*:").findAll(value)
            .map { it.groupValues[1] }.toList()
        require(actual.size == actual.distinct().size) { "Duplicate JSON field." }
        require(actual.toSet() == expected.toSet()) { "Unexpected or missing JSON field." }
        require(value.trim().startsWith("{") && value.trim().endsWith("}")) { "Expected JSON object." }
    }

    internal fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

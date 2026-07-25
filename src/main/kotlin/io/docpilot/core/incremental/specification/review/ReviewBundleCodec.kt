package io.docpilot.core.incremental.specification.review

import io.docpilot.core.incremental.specification.ChangeKind
import io.docpilot.core.incremental.specification.IncrementalUpdateTarget
import io.docpilot.core.incremental.specification.ai.AiDocumentationPatchOperation
import io.docpilot.core.incremental.specification.snapshot.JsonSpecificationSnapshotCodec
import io.docpilot.core.model.ProjectSpecification
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

public class JsonReviewBundleCodec {
    public fun create(
        previous: ProjectSpecification,
        current: ProjectSpecification,
        proposal: DocumentationReviewProposal,
        decisions: List<DocumentationReviewDecision> = emptyList(),
    ): StoredReviewBundle {
        require(previous.project.id == current.project.id) { "Review specifications must belong to one project." }
        val identity = ReviewSpecificationIdentity(
            sha256(JsonSpecificationSnapshotCodec().encodePayload(previous)),
            sha256(JsonSpecificationSnapshotCodec().encodePayload(current)),
        )
        val orderedDecisions = decisions.sortedBy { it.targetId }
        val proposalId = proposalId(previous.project.id, identity, proposal)
        val payload = encodePayload(identity, proposal, orderedDecisions)
        return StoredReviewBundle(
            ReviewBundleFormat.CURRENT_VERSION,
            ReviewBundleProjectIdentity(previous.project.id),
            proposalId,
            identity,
            proposal,
            orderedDecisions,
            ReviewBundleIntegrity(payloadSha256 = sha256(payload)),
        )
    }

    public fun withDecisions(
        bundle: StoredReviewBundle,
        decisions: List<DocumentationReviewDecision>,
    ): StoredReviewBundle {
        val ordered = decisions.sortedBy { it.targetId }
        val payload = encodePayload(bundle.specificationIdentity, bundle.proposal, ordered)
        return bundle.copy(decisions = ordered, integrity = ReviewBundleIntegrity(payloadSha256 = sha256(payload)))
    }

    public fun encode(bundle: StoredReviewBundle): String {
        validateIdentity(bundle)
        val payload = encodePayload(bundle.specificationIdentity, bundle.proposal, bundle.decisions)
        require(sha256(payload) == bundle.integrity.payloadSha256) { "Review bundle payload integrity is inconsistent." }
        return buildString {
            append("{\n")
            append("  \"reviewBundleFormatVersion\":${bundle.formatVersion},\n")
            append("  \"projectIdentity\":{\"projectId\":").appendJson(bundle.projectIdentity.projectId).append("},\n")
            append("  \"proposalId\":").appendJson(bundle.proposalId).append(",\n")
            append("  \"payload\":").append(payload).append(",\n")
            append("  \"integrity\":{\"algorithm\":\"SHA-256\",\"payloadSha256\":")
                .appendJson(bundle.integrity.payloadSha256).append("}\n}\n")
        }
    }

    public fun decode(value: String, expectedProjectId: String): ReviewBundleLoadResult = try {
        val root = ReviewJsonParser(value).root()
        root.requireKeys("reviewBundleFormatVersion", "projectIdentity", "proposalId", "payload", "integrity")
        val version = root.long("reviewBundleFormatVersion").toInt()
        if (version != ReviewBundleFormat.CURRENT_VERSION) {
            return ReviewBundleLoadResult.Invalid(
                ReviewBundleValidationFailure.UNSUPPORTED_VERSION,
                "Unsupported review bundle format version: $version",
            )
        }
        val project = root.obj("projectIdentity").also { it.requireKeys("projectId") }.string("projectId")
        if (project != expectedProjectId) {
            return ReviewBundleLoadResult.Invalid(
                ReviewBundleValidationFailure.PROJECT_MISMATCH,
                "Review bundle project does not match the expected project.",
            )
        }
        val payloadObject = root.obj("payload")
        val decoded = decodePayload(payloadObject)
        val proposalId = root.string("proposalId")
        val expectedProposalId = proposalId(project, decoded.first, decoded.second)
        if (proposalId != expectedProposalId) {
            return ReviewBundleLoadResult.Invalid(
                ReviewBundleValidationFailure.PROPOSAL_ID_MISMATCH,
                "Review bundle proposal identity check failed.",
            )
        }
        val integrityObject = root.obj("integrity").also { it.requireKeys("algorithm", "payloadSha256") }
        if (integrityObject.string("algorithm") != "SHA-256") {
            return ReviewBundleLoadResult.Invalid(ReviewBundleValidationFailure.CORRUPTED, "Unsupported integrity algorithm.")
        }
        val checksum = integrityObject.string("payloadSha256")
        val canonicalPayload = encodePayload(decoded.first, decoded.second, decoded.third)
        if (sha256(canonicalPayload) != checksum) {
            return ReviewBundleLoadResult.Invalid(
                ReviewBundleValidationFailure.INTEGRITY_MISMATCH,
                "Review bundle integrity check failed.",
            )
        }
        ReviewBundleLoadResult.Valid(
            StoredReviewBundle(
                version,
                ReviewBundleProjectIdentity(project),
                proposalId,
                decoded.first,
                decoded.second,
                decoded.third,
                ReviewBundleIntegrity(payloadSha256 = checksum),
            ),
        )
    } catch (error: Exception) {
        ReviewBundleLoadResult.Invalid(
            ReviewBundleValidationFailure.CORRUPTED,
            error.message ?: "Corrupted review bundle.",
        )
    }

    private fun validateIdentity(bundle: StoredReviewBundle) {
        require(
            bundle.proposalId == proposalId(bundle.projectIdentity.projectId, bundle.specificationIdentity, bundle.proposal),
        ) { "Review bundle proposal identity is inconsistent." }
    }

    private fun proposalId(
        projectId: String,
        identity: ReviewSpecificationIdentity,
        proposal: DocumentationReviewProposal,
    ): String {
        val value = buildString {
            append("docpilot-review-proposal-v1\n")
            append(projectId).append('\n')
            append(identity.previousSpecificationSha256).append('\n')
            append(identity.currentSpecificationSha256).append('\n')
            append(proposal.reviewedDocumentationSha256).append('\n')
            append(encodeProposal(proposal))
        }
        return "review:${sha256(value)}"
    }

    private fun encodePayload(
        identity: ReviewSpecificationIdentity,
        proposal: DocumentationReviewProposal,
        decisions: List<DocumentationReviewDecision>,
    ): String = buildString {
        append('{')
        append("\"previousSpecificationSha256\":").appendJson(identity.previousSpecificationSha256).append(',')
        append("\"currentSpecificationSha256\":").appendJson(identity.currentSpecificationSha256).append(',')
        append("\"reviewedDocumentationSha256\":").appendJson(proposal.reviewedDocumentationSha256).append(',')
        append("\"proposal\":").append(encodeProposal(proposal)).append(',')
        append("\"decisions\":").append(encodeDecisions(decisions))
        append('}')
    }

    private fun encodeProposal(proposal: DocumentationReviewProposal): String = buildString {
        append("{\"entries\":[")
        proposal.entries.forEachIndexed { index, entry ->
            if (index > 0) append(',')
            append('{')
            append("\"targetId\":").appendJson(entry.targetId).append(',')
            append("\"parentId\":"); if (entry.parentId == null) append("null") else appendJson(entry.parentId); append(',')
            append("\"target\":").appendJson(entry.target.name).append(',')
            append("\"specificationChangeKind\":").appendJson(entry.specificationChangeKind.name).append(',')
            append("\"documentationChangeKind\":").appendJson(entry.documentationChangeKind.name).append(',')
            append("\"operation\":").appendJson(entry.operation.name).append(',')
            append("\"existingMarkdown\":"); if (entry.existingMarkdown == null) append("null") else appendJson(entry.existingMarkdown); append(',')
            append("\"proposedMarkdown\":").appendJson(entry.proposedMarkdown).append(',')
            append("\"evidenceIds\":").appendStringArray(entry.evidenceIds)
            append('}')
        }
        append("],\"missingPatchTargetIds\":").appendStringArray(proposal.missingPatchTargetIds).append('}')
    }

    private fun encodeDecisions(decisions: List<DocumentationReviewDecision>): String = buildString {
        append('[')
        decisions.sortedBy { it.targetId }.forEachIndexed { index, decision ->
            if (index > 0) append(',')
            append("{\"targetId\":").appendJson(decision.targetId)
            append(",\"disposition\":").appendJson(decision.disposition.name)
            append(",\"comment\":"); if (decision.comment == null) append("null") else appendJson(decision.comment)
            append('}')
        }
        append(']')
    }

    private fun decodePayload(
        payload: ReviewJsonObject,
    ): Triple<ReviewSpecificationIdentity, DocumentationReviewProposal, List<DocumentationReviewDecision>> {
        payload.requireKeys(
            "previousSpecificationSha256",
            "currentSpecificationSha256",
            "reviewedDocumentationSha256",
            "proposal",
            "decisions",
        )
        val identity = ReviewSpecificationIdentity(
            payload.string("previousSpecificationSha256"),
            payload.string("currentSpecificationSha256"),
        )
        val reviewedHash = payload.string("reviewedDocumentationSha256")
        val proposalObject = payload.obj("proposal").also { it.requireKeys("entries", "missingPatchTargetIds") }
        val entries = proposalObject.array("entries").map { value ->
            val entry = value.asObject().also {
                it.requireKeys(
                    "targetId", "parentId", "target", "specificationChangeKind", "documentationChangeKind",
                    "operation", "existingMarkdown", "proposedMarkdown", "evidenceIds",
                )
            }
            DocumentationReviewEntry(
                targetId = entry.string("targetId"),
                parentId = entry.nullableString("parentId"),
                target = IncrementalUpdateTarget.valueOf(entry.string("target")),
                specificationChangeKind = ChangeKind.valueOf(entry.string("specificationChangeKind")),
                documentationChangeKind = DocumentationChangeKind.valueOf(entry.string("documentationChangeKind")),
                operation = AiDocumentationPatchOperation.valueOf(entry.string("operation")),
                existingMarkdown = entry.nullableString("existingMarkdown"),
                proposedMarkdown = entry.string("proposedMarkdown"),
                evidenceIds = entry.stringArray("evidenceIds"),
            )
        }
        val proposal = DocumentationReviewProposal(
            entries,
            proposalObject.stringArray("missingPatchTargetIds"),
            reviewedHash,
        )
        val decisions = payload.array("decisions").map { value ->
            val decision = value.asObject().also { it.requireKeys("targetId", "disposition", "comment") }
            DocumentationReviewDecision(
                decision.string("targetId"),
                DocumentationReviewDisposition.valueOf(decision.string("disposition")),
                decision.nullableString("comment"),
            )
        }
        return Triple(identity, proposal, decisions)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun StringBuilder.appendStringArray(values: List<String>): StringBuilder {
        append('[')
        values.forEachIndexed { index, value -> if (index > 0) append(','); appendJson(value) }
        return append(']')
    }

    private fun StringBuilder.appendJson(value: String): StringBuilder {
        append('"')
        value.forEach { c -> when (c) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\b' -> append("\\b"); '\u000C' -> append("\\f")
            '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
        } }
        return append('"')
    }
}

private sealed interface ReviewJsonValue {
    data class Obj(val values: Map<String, ReviewJsonValue>) : ReviewJsonValue
    data class Arr(val values: List<ReviewJsonValue>) : ReviewJsonValue
    data class Str(val value: String) : ReviewJsonValue
    data class Num(val value: Long) : ReviewJsonValue
    data object Null : ReviewJsonValue
}
private typealias ReviewJsonObject = ReviewJsonValue.Obj
private fun ReviewJsonValue.asObject() = this as? ReviewJsonObject ?: error("Expected JSON object.")
private fun ReviewJsonObject.requireKeys(vararg keys: String) {
    require(values.keys == keys.toSet()) { "Unexpected or missing JSON fields." }
}
private fun ReviewJsonObject.string(name: String) = (values[name] as? ReviewJsonValue.Str)?.value
    ?: error("Missing or invalid JSON string: $name")
private fun ReviewJsonObject.nullableString(name: String) = when (val value = values[name]) {
    ReviewJsonValue.Null -> null
    is ReviewJsonValue.Str -> value.value
    else -> error("Missing or invalid nullable JSON string: $name")
}
private fun ReviewJsonObject.long(name: String) = (values[name] as? ReviewJsonValue.Num)?.value
    ?: error("Missing or invalid JSON number: $name")
private fun ReviewJsonObject.obj(name: String) =
    (values[name] ?: error("Missing JSON object: $name")).asObject()
private fun ReviewJsonObject.array(name: String) = (values[name] as? ReviewJsonValue.Arr)?.values
    ?: error("Missing or invalid JSON array: $name")
private fun ReviewJsonObject.stringArray(name: String) = array(name).map {
    (it as? ReviewJsonValue.Str)?.value ?: error("Invalid JSON string array: $name")
}

private class ReviewJsonParser(private val input: String) {
    private var index = 0
    fun root(): ReviewJsonObject {
        val value = parseValue().asObject()
        skip()
        require(index == input.length) { "Unexpected trailing JSON content." }
        return value
    }
    private fun parseValue(): ReviewJsonValue {
        skip(); require(index < input.length) { "Unexpected end of JSON." }
        return when (input[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> ReviewJsonValue.Str(parseString())
            'n' -> { token("null"); ReviewJsonValue.Null }
            '-', in '0'..'9' -> ReviewJsonValue.Num(parseLong())
            else -> error("Unexpected JSON character at index $index.")
        }
    }
    private fun parseObject(): ReviewJsonObject {
        expect('{'); skip()
        val values = linkedMapOf<String, ReviewJsonValue>()
        if (peek('}')) { expect('}'); return ReviewJsonValue.Obj(values) }
        while (true) {
            val key = parseString(); expect(':')
            require(values.put(key, parseValue()) == null) { "Duplicate JSON field: $key" }
            skip()
            when { peek(',') -> expect(','); peek('}') -> { expect('}'); return ReviewJsonValue.Obj(values) }
                else -> error("Expected ',' or '}'.") }
        }
    }
    private fun parseArray(): ReviewJsonValue.Arr {
        expect('['); skip(); val values = mutableListOf<ReviewJsonValue>()
        if (peek(']')) { expect(']'); return ReviewJsonValue.Arr(values) }
        while (true) {
            values += parseValue(); skip()
            when { peek(',') -> expect(','); peek(']') -> { expect(']'); return ReviewJsonValue.Arr(values) }
                else -> error("Expected ',' or ']'.") }
        }
    }
    private fun parseString(): String {
        skip(); expect('"'); val result = StringBuilder()
        while (index < input.length) {
            val c = input[index++]
            when {
                c == '"' -> return result.toString()
                c == '\\' -> result.append(parseEscape())
                c.code < 0x20 -> error("Unescaped JSON control character.")
                else -> result.append(c)
            }
        }
        error("Unterminated JSON string.")
    }
    private fun parseEscape(): Char {
        require(index < input.length) { "Unterminated JSON escape." }
        return when (val escaped = input[index++]) {
            '"' -> '"'; '\\' -> '\\'; '/' -> '/'; 'b' -> '\b'; 'f' -> '\u000C'; 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'
            'u' -> { val digits = input.substring(index, index + 4); index += 4; digits.toInt(16).toChar() }
            else -> error("Unsupported JSON escape: $escaped")
        }
    }
    private fun parseLong(): Long {
        val start = index
        if (peek('-')) index++
        require(index < input.length && input[index].isDigit())
        while (index < input.length && input[index].isDigit()) index++
        return input.substring(start, index).toLong()
    }
    private fun token(value: String) { require(input.startsWith(value, index)); index += value.length }
    private fun expect(c: Char) { skip(); require(peek(c)) { "Expected '$c'." }; index++ }
    private fun peek(c: Char) = index < input.length && input[index] == c
    private fun skip() { while (index < input.length && input[index].isWhitespace()) index++ }
}

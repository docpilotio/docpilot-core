package io.docpilot.cli.command.finding

import io.docpilot.core.documentation.adr.AiProposedAdr
import io.docpilot.core.documentation.enrichment.DocumentationEnrichmentStatus
import io.docpilot.core.documentation.synthesis.DocumentationTier
import io.docpilot.core.documentation.synthesis.SynthesisRecord
import io.docpilot.core.incremental.specification.review.DocumentationReviewDecision
import io.docpilot.core.incremental.specification.review.DocumentationReviewDisposition
import io.docpilot.core.specification.finding.Finding
import io.docpilot.core.specification.finding.FindingId
import io.docpilot.core.specification.finding.FindingSeverity

/** Raw, not-yet-validated Finding fields as authored by a human (or a future extraction tool). */
internal data class FindingInput(
    val subjectStableId: String,
    val semanticKey: String,
    val category: String,
    val severity: String,
    val summary: String,
    val evidenceRefs: Set<String>,
    val unresolvedRefs: Set<String>,
)

/**
 * Hand-rolled JSON codec scoped to docpilot-cli, mirroring the style of core's
 * `JsonSpecificationSnapshotCodec` (which keeps its own parser `private` to that file). No JSON
 * library is used anywhere in this codebase; this is an independent, equivalent parser, not a
 * shared/reused one.
 */
internal object FindingsJsonCodec {
    fun encodeFindingInputs(inputs: List<FindingInput>): String = array(inputs.map(::encodeFindingInput))

    fun decodeFindingInputs(json: String): List<FindingInput> =
        JsonParser(json).parseRootArray().mapIndexed { index, value ->
            val o = value.asObject("Finding input at index $index")
            FindingInput(
                subjectStableId = o.requiredString("subjectStableId"),
                semanticKey = o.requiredString("semanticKey"),
                category = o.requiredString("category"),
                severity = o.requiredString("severity"),
                summary = o.requiredString("summary"),
                evidenceRefs = o.stringSet("evidenceRefs"),
                unresolvedRefs = o.optionalStringSet("unresolvedRefs"),
            )
        }

    fun encodeFindings(findings: List<Finding>): String = array(findings.map(::encodeFinding))

    fun decodeFindings(json: String): List<Finding> =
        JsonParser(json).parseRootArray().mapIndexed { index, value ->
            decodeFinding(value.asObject("Finding at index $index"))
        }

    fun encodeDecisions(decisions: List<DocumentationReviewDecision>): String = array(decisions.map(::encodeDecision))

    fun decodeDecisions(json: String): List<DocumentationReviewDecision> =
        JsonParser(json).parseRootArray().mapIndexed { index, value ->
            val o = value.asObject("Decision at index $index")
            DocumentationReviewDecision(
                targetId = o.requiredString("targetId"),
                disposition = DocumentationReviewDisposition.valueOf(o.requiredString("disposition")),
                comment = o.optionalString("comment"),
            )
        }

    fun encodeProposal(proposal: AiProposedAdr): String = obj(
        "proposalId" to str(proposal.proposalId),
        "title" to str(proposal.title),
        "context" to str(proposal.context),
        "decision" to str(proposal.decision),
        "consequences" to str(proposal.consequences),
        "alternatives" to str(proposal.alternatives),
        "citedFindingIds" to strings(proposal.citedFindingIds),
        "record" to encodeRecord(proposal.record),
    )

    fun decodeProposal(json: String): AiProposedAdr {
        val o = JsonParser(json).parseRootObject()
        return AiProposedAdr(
            proposalId = o.requiredString("proposalId"),
            title = o.requiredString("title"),
            context = o.requiredString("context"),
            decision = o.requiredString("decision"),
            consequences = o.requiredString("consequences"),
            alternatives = o.requiredString("alternatives"),
            citedFindingIds = o.stringList("citedFindingIds"),
            record = decodeRecord(o.requiredObject("record")),
        )
    }

    private fun encodeFindingInput(v: FindingInput): String = obj(
        "subjectStableId" to str(v.subjectStableId),
        "semanticKey" to str(v.semanticKey),
        "category" to str(v.category),
        "severity" to str(v.severity),
        "summary" to str(v.summary),
        "evidenceRefs" to strings(v.evidenceRefs),
        "unresolvedRefs" to strings(v.unresolvedRefs),
    )

    private fun encodeFinding(v: Finding): String = obj(
        "formatVersion" to v.formatVersion.toString(),
        "id" to str(v.id.value),
        "subjectStableId" to str(v.subjectStableId),
        "semanticKey" to str(v.semanticKey),
        "category" to str(v.category),
        "severity" to str(v.severity.name),
        "summary" to str(v.summary),
        "evidenceRefs" to strings(v.evidenceRefs),
        "unresolvedRefs" to strings(v.unresolvedRefs),
    )

    private fun decodeFinding(o: JsonValue.ObjectValue): Finding = Finding(
        formatVersion = o.optionalInt("formatVersion") ?: 1,
        id = FindingId(o.requiredString("id")),
        subjectStableId = o.requiredString("subjectStableId"),
        semanticKey = o.requiredString("semanticKey"),
        category = o.requiredString("category"),
        severity = FindingSeverity.valueOf(o.requiredString("severity")),
        summary = o.requiredString("summary"),
        evidenceRefs = o.stringSet("evidenceRefs"),
        unresolvedRefs = o.optionalStringSet("unresolvedRefs"),
    )

    private fun encodeDecision(v: DocumentationReviewDecision): String = obj(
        "targetId" to str(v.targetId),
        "disposition" to str(v.disposition.name),
        "comment" to nullable(v.comment),
    )

    private fun encodeRecord(v: SynthesisRecord): String = obj(
        "formatVersion" to v.formatVersion.toString(),
        "synthesisStableId" to str(v.synthesisStableId),
        "tier" to str(v.tier.name),
        "providerId" to nullable(v.providerId),
        "model" to nullable(v.model),
        "canonicalInputIdentity" to str(v.canonicalInputIdentity),
        "promptTemplateIdentity" to str(v.promptTemplateIdentity),
        "promptTemplateVersion" to v.promptTemplateVersion.toString(),
        "documentType" to str(v.documentType),
        "sourceArtifactIds" to array(v.sourceArtifactIds.map(::str)),
        "evidenceRefs" to array(v.evidenceRefs.map(::str)),
        "unresolvedRefs" to array(v.unresolvedRefs.map(::str)),
        "contentSha256" to nullable(v.contentSha256),
        "status" to str(v.status.name),
        "providerInvoked" to v.providerInvoked.toString(),
        "cached" to v.cached.toString(),
        "diagnostic" to nullable(v.diagnostic),
    )

    private fun decodeRecord(o: JsonValue.ObjectValue): SynthesisRecord = SynthesisRecord(
        formatVersion = o.optionalInt("formatVersion") ?: 1,
        synthesisStableId = o.requiredString("synthesisStableId"),
        tier = DocumentationTier.valueOf(o.requiredString("tier")),
        providerId = o.optionalString("providerId"),
        model = o.optionalString("model"),
        canonicalInputIdentity = o.requiredString("canonicalInputIdentity"),
        promptTemplateIdentity = o.requiredString("promptTemplateIdentity"),
        promptTemplateVersion = o.requiredInt("promptTemplateVersion"),
        documentType = o.requiredString("documentType"),
        sourceArtifactIds = o.stringList("sourceArtifactIds"),
        evidenceRefs = o.stringList("evidenceRefs"),
        unresolvedRefs = o.stringList("unresolvedRefs"),
        contentSha256 = o.optionalString("contentSha256"),
        status = DocumentationEnrichmentStatus.valueOf(o.requiredString("status")),
        providerInvoked = o.requiredBoolean("providerInvoked"),
        cached = o.requiredBoolean("cached"),
        diagnostic = o.optionalString("diagnostic"),
    )

    private fun str(v: String) = buildString { appendJsonString(v) }
    private fun nullable(v: String?) = v?.let(::str) ?: "null"
    private fun strings(values: Collection<String>) = array(values.sorted().map(::str))
    private fun array(values: List<String>) = values.joinToString(prefix = "[", postfix = "]", separator = ",")
    private fun obj(vararg fields: Pair<String, String>) =
        fields.joinToString(prefix = "{", postfix = "}", separator = ",") { (k, v) -> "${str(k)}:$v" }
}

private fun StringBuilder.appendJsonString(value: String): StringBuilder {
    append('"')
    value.forEach { c ->
        when (c) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\b' -> append("\\b"); '\u000C' -> append("\\f")
            '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
        }
    }
    return append('"')
}

private sealed interface JsonValue {
    data class ObjectValue(val values: Map<String, JsonValue>) : JsonValue
    data class ArrayValue(val values: List<JsonValue>) : JsonValue
    data class StringValue(val value: String) : JsonValue
    data class NumberValue(val value: Long) : JsonValue
    data class BooleanValue(val value: Boolean) : JsonValue
    data object NullValue : JsonValue
}

private fun JsonValue.asObject(label: String): JsonValue.ObjectValue =
    this as? JsonValue.ObjectValue ?: error("$label must be a JSON object.")

private fun JsonValue.ObjectValue.requiredString(n: String) =
    (values[n] as? JsonValue.StringValue)?.value ?: error("Missing or invalid JSON string: $n")
private fun JsonValue.ObjectValue.optionalString(n: String) = when (val v = values[n]) {
    null, JsonValue.NullValue -> null
    is JsonValue.StringValue -> v.value
    else -> error("Invalid JSON string: $n")
}
private fun JsonValue.ObjectValue.requiredLong(n: String) =
    (values[n] as? JsonValue.NumberValue)?.value ?: error("Missing or invalid JSON number: $n")
private fun JsonValue.ObjectValue.requiredInt(n: String) = requiredLong(n).toIntExact(n)
private fun JsonValue.ObjectValue.optionalInt(n: String) = when (val v = values[n]) {
    null, JsonValue.NullValue -> null
    is JsonValue.NumberValue -> v.value.toIntExact(n)
    else -> error("Invalid JSON number: $n")
}
private fun JsonValue.ObjectValue.requiredBoolean(n: String) =
    (values[n] as? JsonValue.BooleanValue)?.value ?: error("Missing or invalid JSON boolean: $n")
private fun JsonValue.ObjectValue.requiredObject(n: String) =
    values[n] as? JsonValue.ObjectValue ?: error("Missing or invalid JSON object: $n")
private fun JsonValue.ObjectValue.requiredArray(n: String) =
    (values[n] as? JsonValue.ArrayValue)?.values ?: error("Missing or invalid JSON array: $n")
private fun JsonValue.ObjectValue.stringList(n: String) = requiredArray(n).mapIndexed { i, v ->
    (v as? JsonValue.StringValue)?.value ?: error("Invalid string at $n[$i]")
}
private fun JsonValue.ObjectValue.stringSet(n: String) = stringList(n).toSet()
private fun JsonValue.ObjectValue.optionalStringSet(n: String) = if (n in values) stringSet(n) else emptySet()
private fun Long.toIntExact(n: String): Int {
    require(this in Int.MIN_VALUE..Int.MAX_VALUE) { "JSON number outside Int range: $n" }
    return toInt()
}

private class JsonParser(private val input: String) {
    private var i = 0

    fun parseRootObject(): JsonValue.ObjectValue {
        val v = parseValue() as? JsonValue.ObjectValue ?: error("JSON root must be an object")
        skip(); require(i == input.length) { "Unexpected trailing JSON content at index $i" }
        return v
    }

    fun parseRootArray(): List<JsonValue> {
        val v = parseValue() as? JsonValue.ArrayValue ?: error("JSON root must be an array")
        skip(); require(i == input.length) { "Unexpected trailing JSON content at index $i" }
        return v.values
    }

    private fun parseValue(): JsonValue {
        skip(); require(i < input.length) { "Unexpected end of JSON" }
        return when (input[i]) {
            '{' -> parseObject(); '[' -> parseArray(); '"' -> JsonValue.StringValue(parseString())
            'n' -> { token("null"); JsonValue.NullValue }
            't' -> { token("true"); JsonValue.BooleanValue(true) }
            'f' -> { token("false"); JsonValue.BooleanValue(false) }
            '-', in '0'..'9' -> JsonValue.NumberValue(parseLong())
            else -> error("Unexpected JSON character '${input[i]}' at index $i")
        }
    }

    private fun parseObject(): JsonValue.ObjectValue {
        expect('{'); skip()
        val m = linkedMapOf<String, JsonValue>()
        if (peek('}')) { expect('}'); return JsonValue.ObjectValue(m) }
        while (true) {
            val n = parseString(); expect(':')
            require(m.put(n, parseValue()) == null) { "Duplicate JSON field: $n" }
            skip()
            if (peek(',')) { expect(',') } else if (peek('}')) { expect('}'); return JsonValue.ObjectValue(m) }
            else error("Expected ',' or '}' at index $i")
        }
    }

    private fun parseArray(): JsonValue.ArrayValue {
        expect('['); skip()
        val a = mutableListOf<JsonValue>()
        if (peek(']')) { expect(']'); return JsonValue.ArrayValue(a) }
        while (true) {
            a += parseValue(); skip()
            if (peek(',')) { expect(',') } else if (peek(']')) { expect(']'); return JsonValue.ArrayValue(a) }
            else error("Expected ',' or ']' at index $i")
        }
    }

    private fun parseString(): String {
        skip(); expect('"')
        val b = StringBuilder()
        while (i < input.length) {
            val c = input[i++]
            when {
                c == '"' -> return b.toString()
                c == '\\' -> b.append(escape())
                c.code < 0x20 -> error("Unescaped control character")
                else -> b.append(c)
            }
        }
        error("Unterminated JSON string")
    }

    private fun escape(): Char {
        require(i < input.length) { "Unterminated JSON escape" }
        return when (val e = input[i++]) {
            '"' -> '"'; '\\' -> '\\'; '/' -> '/'; 'b' -> '\b'; 'f' -> '\u000C'; 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'
            'u' -> {
                val d = input.substring(i, i + 4)
                require(d.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' })
                i += 4; d.toInt(16).toChar()
            }
            else -> error("Unsupported JSON escape: \\$e")
        }
    }

    private fun parseLong(): Long {
        val s = i
        if (peek('-')) i++
        require(i < input.length && input[i].isDigit())
        if (input[i] == '0') { i++; require(i >= input.length || !input[i].isDigit()) }
        else while (i < input.length && input[i].isDigit()) i++
        require(i >= input.length || input[i] !in listOf('.', 'e', 'E'))
        return input.substring(s, i).toLong()
    }

    private fun token(t: String) { require(input.startsWith(t, i)) { "Expected '$t' at index $i" }; i += t.length }
    private fun expect(c: Char) { skip(); require(i < input.length && input[i] == c) { "Expected '$c' at index $i" }; i++ }
    private fun peek(c: Char) = i < input.length && input[i] == c
    private fun skip() { while (i < input.length && input[i].isWhitespace()) i++ }
}

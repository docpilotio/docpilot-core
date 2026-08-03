package io.docpilot.core.specification.finding

import io.docpilot.core.incremental.specification.review.DocumentationReviewDecision
import io.docpilot.core.incremental.specification.review.DocumentationReviewDisposition
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

public object CurationDecisionRegistryFormat {
    public const val CURRENT_VERSION: Int = 1
    public const val DEFAULT_RELATIVE_PATH: String = ".docpilot/findings/decisions.json"
}

public data class StoredCurationDecisionRegistry(
    public val formatVersion: Int,
    public val projectId: String,
    public val decisions: List<DocumentationReviewDecision>,
    public val payloadSha256: String,
)

public enum class CurationDecisionRegistryValidationFailure { CORRUPTED, UNSUPPORTED_VERSION, PROJECT_MISMATCH, INTEGRITY_MISMATCH }

public sealed interface CurationDecisionRegistryLoadResult {
    public data object NotFound : CurationDecisionRegistryLoadResult
    public data class Valid(public val registry: StoredCurationDecisionRegistry) : CurationDecisionRegistryLoadResult
    public data class Invalid(
        public val reason: CurationDecisionRegistryValidationFailure,
        public val message: String,
    ) : CurationDecisionRegistryLoadResult
}

/**
 * Hand-rolled JSON codec, mirroring `JsonFindingRegistryCodec`/`JsonReviewBundleCodec`'s style. Reuses
 * [DocumentationReviewDecision]/[DocumentationReviewDisposition] unmodified as the decision shape,
 * matching every prior RFC in this track.
 */
public class JsonCurationDecisionRegistryCodec {
    public fun encode(projectId: String, decisions: List<DocumentationReviewDecision>): String {
        val ordered = decisions.sortedBy { it.targetId }
        val payload = encodeDecisions(ordered)
        val checksum = sha256(payload)
        return buildString {
            append("{\"curationDecisionRegistryFormatVersion\":${CurationDecisionRegistryFormat.CURRENT_VERSION},")
            append("\"projectId\":").appendJson(projectId).append(',')
            append("\"decisions\":").append(payload).append(',')
            append("\"integrity\":{\"algorithm\":\"SHA-256\",\"payloadSha256\":").appendJson(checksum).append("}}")
        }
    }

    public fun decode(value: String, expectedProjectId: String): CurationDecisionRegistryLoadResult = try {
        val root = DecisionJsonParser(value).root()
        root.requireKeys("curationDecisionRegistryFormatVersion", "projectId", "decisions", "integrity")
        val version = root.long("curationDecisionRegistryFormatVersion").toInt()
        if (version != CurationDecisionRegistryFormat.CURRENT_VERSION) {
            CurationDecisionRegistryLoadResult.Invalid(
                CurationDecisionRegistryValidationFailure.UNSUPPORTED_VERSION,
                "Unsupported curation decision registry format version: $version",
            )
        } else {
            val projectId = root.string("projectId")
            if (projectId != expectedProjectId) {
                CurationDecisionRegistryLoadResult.Invalid(
                    CurationDecisionRegistryValidationFailure.PROJECT_MISMATCH,
                    "Curation decision registry project does not match the expected project.",
                )
            } else {
                val decisions = root.array("decisions").map { decodeDecision(it.asObject()) }
                val integrity = root.obj("integrity").also { it.requireKeys("algorithm", "payloadSha256") }
                if (integrity.string("algorithm") != "SHA-256") {
                    CurationDecisionRegistryLoadResult.Invalid(
                        CurationDecisionRegistryValidationFailure.CORRUPTED, "Unsupported integrity algorithm.",
                    )
                } else {
                    val checksum = integrity.string("payloadSha256")
                    if (sha256(encodeDecisions(decisions)) != checksum) {
                        CurationDecisionRegistryLoadResult.Invalid(
                            CurationDecisionRegistryValidationFailure.INTEGRITY_MISMATCH,
                            "Curation decision registry integrity check failed.",
                        )
                    } else {
                        CurationDecisionRegistryLoadResult.Valid(StoredCurationDecisionRegistry(version, projectId, decisions, checksum))
                    }
                }
            }
        }
    } catch (error: Exception) {
        CurationDecisionRegistryLoadResult.Invalid(
            CurationDecisionRegistryValidationFailure.CORRUPTED, error.message ?: "Corrupted curation decision registry.",
        )
    }

    private fun encodeDecisions(decisions: List<DocumentationReviewDecision>): String = buildString {
        append('[')
        decisions.forEachIndexed { index, decision ->
            if (index > 0) append(',')
            append("{\"targetId\":").appendJson(decision.targetId)
            append(",\"disposition\":").appendJson(decision.disposition.name)
            append(",\"comment\":")
            if (decision.comment == null) append("null") else appendJson(decision.comment)
            append('}')
        }
        append(']')
    }

    private fun decodeDecision(o: DecisionJsonObject): DocumentationReviewDecision {
        o.requireKeys("targetId", "disposition", "comment")
        return DocumentationReviewDecision(
            targetId = o.string("targetId"),
            disposition = DocumentationReviewDisposition.valueOf(o.string("disposition")),
            comment = o.nullableString("comment"),
        )
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

/**
 * One JSON file per project, under `<outputRoot>/.docpilot/findings/decisions.json`. Shared by both
 * `generate roadmap --decisions-registry` and `generate adr-adopt --decisions-registry` (roadmap
 * targetIds are `Finding.id.value`, ADR adoption targetIds are `AiProposedAdr.proposalId` — the two
 * schemes never collide in practice, since their string prefixes differ: `finding:` vs.
 * `documentation-synthesis:`).
 */
public class FileCurationDecisionRegistryRepository(
    outputRoot: Path,
    private val codec: JsonCurationDecisionRegistryCodec = JsonCurationDecisionRegistryCodec(),
) {
    private val path: Path = outputRoot.resolve(CurationDecisionRegistryFormat.DEFAULT_RELATIVE_PATH)

    public fun load(projectId: String): CurationDecisionRegistryLoadResult {
        if (!Files.exists(path)) return CurationDecisionRegistryLoadResult.NotFound
        if (!Files.isRegularFile(path)) {
            return CurationDecisionRegistryLoadResult.Invalid(
                CurationDecisionRegistryValidationFailure.CORRUPTED,
                "Curation decision registry path is not a regular file.",
            )
        }
        return try {
            codec.decode(Files.readString(path, StandardCharsets.UTF_8), projectId)
        } catch (error: Exception) {
            CurationDecisionRegistryLoadResult.Invalid(
                CurationDecisionRegistryValidationFailure.CORRUPTED, error.message ?: "Unable to read curation decision registry.",
            )
        }
    }

    /**
     * Merges [newDecisions] into the persisted registry keyed by `targetId`; a new decision for an
     * already-decided target overwrites (last write wins) — humans revise curation calls, and this is
     * deliberately not treated as a fail-closed conflict (see RFC-0084's "Out of scope"). Returns the
     * full merged, sorted list.
     */
    public fun merge(projectId: String, newDecisions: List<DocumentationReviewDecision>): List<DocumentationReviewDecision> {
        val current = when (val loaded = load(projectId)) {
            is CurationDecisionRegistryLoadResult.Valid -> loaded.registry.decisions
            CurationDecisionRegistryLoadResult.NotFound -> emptyList()
            is CurationDecisionRegistryLoadResult.Invalid ->
                throw IllegalStateException("Cannot merge into a corrupted curation decision registry: ${loaded.message}")
        }
        val byTarget = LinkedHashMap<String, DocumentationReviewDecision>()
        current.forEach { byTarget[it.targetId] = it }
        newDecisions.forEach { byTarget[it.targetId] = it }
        val merged = byTarget.values.sortedBy { it.targetId }
        save(projectId, merged)
        return merged
    }

    private fun save(projectId: String, decisions: List<DocumentationReviewDecision>) {
        val directory = requireNotNull(path.parent) { "Curation decision registry path must have a parent directory." }
        Files.createDirectories(directory)
        val encoded = codec.encode(projectId, decisions)
        val temporary = Files.createTempFile(directory, "curation-decisions-", ".tmp")
        try {
            Files.writeString(temporary, encoded, StandardCharsets.UTF_8)
            val validation = codec.decode(Files.readString(temporary, StandardCharsets.UTF_8), projectId)
            check(validation is CurationDecisionRegistryLoadResult.Valid) { "Refusing to store an invalid curation decision registry." }
            moveReplacing(temporary, path)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun moveReplacing(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

private fun StringBuilder.appendJson(value: String): StringBuilder {
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

private sealed interface DecisionJsonValue {
    data class Obj(val values: Map<String, DecisionJsonValue>) : DecisionJsonValue
    data class Arr(val values: List<DecisionJsonValue>) : DecisionJsonValue
    data class Str(val value: String) : DecisionJsonValue
    data class Num(val value: Long) : DecisionJsonValue
    data object Null : DecisionJsonValue
}
private typealias DecisionJsonObject = DecisionJsonValue.Obj
private fun DecisionJsonValue.asObject() = this as? DecisionJsonObject ?: error("Expected JSON object.")
private fun DecisionJsonObject.requireKeys(vararg keys: String) {
    require(values.keys == keys.toSet()) { "Unexpected or missing JSON fields." }
}
private fun DecisionJsonObject.string(name: String) = (values[name] as? DecisionJsonValue.Str)?.value
    ?: error("Missing or invalid JSON string: $name")
private fun DecisionJsonObject.nullableString(name: String) = when (val value = values[name]) {
    DecisionJsonValue.Null -> null
    is DecisionJsonValue.Str -> value.value
    else -> error("Missing or invalid nullable JSON string: $name")
}
private fun DecisionJsonObject.long(name: String) = (values[name] as? DecisionJsonValue.Num)?.value
    ?: error("Missing or invalid JSON number: $name")
private fun DecisionJsonObject.obj(name: String) = (values[name] ?: error("Missing JSON object: $name")).asObject()
private fun DecisionJsonObject.array(name: String) = (values[name] as? DecisionJsonValue.Arr)?.values
    ?: error("Missing or invalid JSON array: $name")

private class DecisionJsonParser(private val input: String) {
    private var index = 0
    fun root(): DecisionJsonObject {
        val value = parseValue().asObject()
        skip()
        require(index == input.length) { "Unexpected trailing JSON content." }
        return value
    }
    private fun parseValue(): DecisionJsonValue {
        skip(); require(index < input.length) { "Unexpected end of JSON." }
        return when (input[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> DecisionJsonValue.Str(parseString())
            'n' -> { token("null"); DecisionJsonValue.Null }
            '-', in '0'..'9' -> DecisionJsonValue.Num(parseLong())
            else -> error("Unexpected JSON character at index $index.")
        }
    }
    private fun parseObject(): DecisionJsonObject {
        expect('{'); skip()
        val values = linkedMapOf<String, DecisionJsonValue>()
        if (peek('}')) { expect('}'); return DecisionJsonValue.Obj(values) }
        while (true) {
            val key = parseString(); expect(':')
            require(values.put(key, parseValue()) == null) { "Duplicate JSON field: $key" }
            skip()
            when {
                peek(',') -> expect(',')
                peek('}') -> { expect('}'); return DecisionJsonValue.Obj(values) }
                else -> error("Expected ',' or '}'.")
            }
        }
    }
    private fun parseArray(): DecisionJsonValue.Arr {
        expect('['); skip(); val values = mutableListOf<DecisionJsonValue>()
        if (peek(']')) { expect(']'); return DecisionJsonValue.Arr(values) }
        while (true) {
            values += parseValue(); skip()
            when {
                peek(',') -> expect(',')
                peek(']') -> { expect(']'); return DecisionJsonValue.Arr(values) }
                else -> error("Expected ',' or ']'.")
            }
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

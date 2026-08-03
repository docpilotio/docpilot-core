package io.docpilot.core.specification.finding

import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

public object FindingRegistryFormat {
    public const val CURRENT_VERSION: Int = 1
    public const val DEFAULT_RELATIVE_PATH: String = ".docpilot/findings/registry.json"
}

public data class StoredFindingRegistry(
    public val formatVersion: Int,
    public val projectId: String,
    public val findings: List<Finding>,
    public val payloadSha256: String,
)

public enum class FindingRegistryValidationFailure { CORRUPTED, UNSUPPORTED_VERSION, PROJECT_MISMATCH, INTEGRITY_MISMATCH }

public sealed interface FindingRegistryLoadResult {
    public data object NotFound : FindingRegistryLoadResult
    public data class Valid(public val registry: StoredFindingRegistry) : FindingRegistryLoadResult
    public data class Invalid(
        public val reason: FindingRegistryValidationFailure,
        public val message: String,
    ) : FindingRegistryLoadResult
}

/**
 * Hand-rolled JSON codec, mirroring `JsonReviewBundleCodec`'s style (no external JSON library is used
 * anywhere in this codebase — each persisted-state codec hand-rolls its own minimal parser).
 */
public class JsonFindingRegistryCodec {
    public fun encode(projectId: String, findings: List<Finding>): String {
        val ordered = findings.sortedBy { it.id.value }
        val payload = encodeFindings(ordered)
        val checksum = sha256(payload)
        return buildString {
            append("{\"findingRegistryFormatVersion\":${FindingRegistryFormat.CURRENT_VERSION},")
            append("\"projectId\":").appendJson(projectId).append(',')
            append("\"findings\":").append(payload).append(',')
            append("\"integrity\":{\"algorithm\":\"SHA-256\",\"payloadSha256\":").appendJson(checksum).append("}}")
        }
    }

    public fun decode(value: String, expectedProjectId: String): FindingRegistryLoadResult = try {
        val root = FindingJsonParser(value).root()
        root.requireKeys("findingRegistryFormatVersion", "projectId", "findings", "integrity")
        val version = root.long("findingRegistryFormatVersion").toInt()
        if (version != FindingRegistryFormat.CURRENT_VERSION) {
            FindingRegistryLoadResult.Invalid(
                FindingRegistryValidationFailure.UNSUPPORTED_VERSION,
                "Unsupported Finding registry format version: $version",
            )
        } else {
            val projectId = root.string("projectId")
            if (projectId != expectedProjectId) {
                FindingRegistryLoadResult.Invalid(
                    FindingRegistryValidationFailure.PROJECT_MISMATCH,
                    "Finding registry project does not match the expected project.",
                )
            } else {
                val findings = root.array("findings").map { decodeFinding(it.asObject()) }
                val integrity = root.obj("integrity").also { it.requireKeys("algorithm", "payloadSha256") }
                if (integrity.string("algorithm") != "SHA-256") {
                    FindingRegistryLoadResult.Invalid(FindingRegistryValidationFailure.CORRUPTED, "Unsupported integrity algorithm.")
                } else {
                    val checksum = integrity.string("payloadSha256")
                    if (sha256(encodeFindings(findings)) != checksum) {
                        FindingRegistryLoadResult.Invalid(
                            FindingRegistryValidationFailure.INTEGRITY_MISMATCH,
                            "Finding registry integrity check failed.",
                        )
                    } else {
                        FindingRegistryLoadResult.Valid(StoredFindingRegistry(version, projectId, findings, checksum))
                    }
                }
            }
        }
    } catch (error: Exception) {
        FindingRegistryLoadResult.Invalid(FindingRegistryValidationFailure.CORRUPTED, error.message ?: "Corrupted Finding registry.")
    }

    private fun encodeFindings(findings: List<Finding>): String = buildString {
        append('[')
        findings.forEachIndexed { index, finding ->
            if (index > 0) append(',')
            append('{')
            append("\"formatVersion\":${finding.formatVersion},")
            append("\"id\":").appendJson(finding.id.value).append(',')
            append("\"subjectStableId\":").appendJson(finding.subjectStableId).append(',')
            append("\"semanticKey\":").appendJson(finding.semanticKey).append(',')
            append("\"category\":").appendJson(finding.category).append(',')
            append("\"severity\":").appendJson(finding.severity.name).append(',')
            append("\"summary\":").appendJson(finding.summary).append(',')
            append("\"evidenceRefs\":").appendStringArray(finding.evidenceRefs.sorted()).append(',')
            append("\"unresolvedRefs\":").appendStringArray(finding.unresolvedRefs.sorted())
            append('}')
        }
        append(']')
    }

    private fun decodeFinding(o: FindingJsonObject): Finding {
        o.requireKeys(
            "formatVersion", "id", "subjectStableId", "semanticKey", "category", "severity", "summary",
            "evidenceRefs", "unresolvedRefs",
        )
        return Finding(
            formatVersion = o.long("formatVersion").toInt(),
            id = FindingId(o.string("id")),
            subjectStableId = o.string("subjectStableId"),
            semanticKey = o.string("semanticKey"),
            category = o.string("category"),
            severity = FindingSeverity.valueOf(o.string("severity")),
            summary = o.string("summary"),
            evidenceRefs = o.stringArray("evidenceRefs").toSet(),
            unresolvedRefs = o.stringArray("unresolvedRefs").toSet(),
        )
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

/**
 * One JSON file per project, under `<outputRoot>/.docpilot/findings/registry.json`. Mirrors
 * `FileReviewBundleRepository`'s file-handling shape (temp file, decode-and-validate it back, atomic
 * move, stored `payloadSha256`) — not its domain model, which RFC-0081/0082 already established is
 * Markdown-patch-specific and does not fit Finding-shaped data.
 */
public class FileFindingRegistryRepository(
    outputRoot: Path,
    private val codec: JsonFindingRegistryCodec = JsonFindingRegistryCodec(),
) {
    private val path: Path = outputRoot.resolve(FindingRegistryFormat.DEFAULT_RELATIVE_PATH)

    public fun load(projectId: String): FindingRegistryLoadResult {
        if (!Files.exists(path)) return FindingRegistryLoadResult.NotFound
        if (!Files.isRegularFile(path)) {
            return FindingRegistryLoadResult.Invalid(
                FindingRegistryValidationFailure.CORRUPTED,
                "Finding registry path is not a regular file.",
            )
        }
        return try {
            codec.decode(Files.readString(path, StandardCharsets.UTF_8), projectId)
        } catch (error: Exception) {
            FindingRegistryLoadResult.Invalid(FindingRegistryValidationFailure.CORRUPTED, error.message ?: "Unable to read Finding registry.")
        }
    }

    /**
     * Unions [newFindings] into the persisted registry, keyed by [Finding.id]; an existing entry wins
     * on an id collision (a [Finding]'s id is content-derived — see [FindingIdentity] — so a collision
     * means identical content, not a real conflict). Returns the full merged, sorted list.
     */
    public fun merge(projectId: String, newFindings: List<Finding>): List<Finding> {
        val current = when (val loaded = load(projectId)) {
            is FindingRegistryLoadResult.Valid -> loaded.registry.findings
            FindingRegistryLoadResult.NotFound -> emptyList()
            is FindingRegistryLoadResult.Invalid ->
                throw IllegalStateException("Cannot merge into a corrupted Finding registry: ${loaded.message}")
        }
        val byId = LinkedHashMap<String, Finding>()
        current.forEach { byId[it.id.value] = it }
        newFindings.forEach { byId.putIfAbsent(it.id.value, it) }
        val merged = byId.values.sortedBy { it.id.value }
        save(projectId, merged)
        return merged
    }

    private fun save(projectId: String, findings: List<Finding>) {
        val directory = requireNotNull(path.parent) { "Finding registry path must have a parent directory." }
        Files.createDirectories(directory)
        val encoded = codec.encode(projectId, findings)
        val temporary = Files.createTempFile(directory, "findings-registry-", ".tmp")
        try {
            Files.writeString(temporary, encoded, StandardCharsets.UTF_8)
            val validation = codec.decode(Files.readString(temporary, StandardCharsets.UTF_8), projectId)
            check(validation is FindingRegistryLoadResult.Valid) { "Refusing to store an invalid Finding registry." }
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

private fun StringBuilder.appendStringArray(values: List<String>): StringBuilder {
    append('[')
    values.forEachIndexed { index, value -> if (index > 0) append(','); appendJson(value) }
    return append(']')
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

private sealed interface FindingJsonValue {
    data class Obj(val values: Map<String, FindingJsonValue>) : FindingJsonValue
    data class Arr(val values: List<FindingJsonValue>) : FindingJsonValue
    data class Str(val value: String) : FindingJsonValue
    data class Num(val value: Long) : FindingJsonValue
}
private typealias FindingJsonObject = FindingJsonValue.Obj
private fun FindingJsonValue.asObject() = this as? FindingJsonObject ?: error("Expected JSON object.")
private fun FindingJsonObject.requireKeys(vararg keys: String) {
    require(values.keys == keys.toSet()) { "Unexpected or missing JSON fields." }
}
private fun FindingJsonObject.string(name: String) = (values[name] as? FindingJsonValue.Str)?.value
    ?: error("Missing or invalid JSON string: $name")
private fun FindingJsonObject.long(name: String) = (values[name] as? FindingJsonValue.Num)?.value
    ?: error("Missing or invalid JSON number: $name")
private fun FindingJsonObject.obj(name: String) = (values[name] ?: error("Missing JSON object: $name")).asObject()
private fun FindingJsonObject.array(name: String) = (values[name] as? FindingJsonValue.Arr)?.values
    ?: error("Missing or invalid JSON array: $name")
private fun FindingJsonObject.stringArray(name: String) = array(name).map {
    (it as? FindingJsonValue.Str)?.value ?: error("Invalid JSON string array: $name")
}

private class FindingJsonParser(private val input: String) {
    private var index = 0
    fun root(): FindingJsonObject {
        val value = parseValue().asObject()
        skip()
        require(index == input.length) { "Unexpected trailing JSON content." }
        return value
    }
    private fun parseValue(): FindingJsonValue {
        skip(); require(index < input.length) { "Unexpected end of JSON." }
        return when (input[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> FindingJsonValue.Str(parseString())
            '-', in '0'..'9' -> FindingJsonValue.Num(parseLong())
            else -> error("Unexpected JSON character at index $index.")
        }
    }
    private fun parseObject(): FindingJsonObject {
        expect('{'); skip()
        val values = linkedMapOf<String, FindingJsonValue>()
        if (peek('}')) { expect('}'); return FindingJsonValue.Obj(values) }
        while (true) {
            val key = parseString(); expect(':')
            require(values.put(key, parseValue()) == null) { "Duplicate JSON field: $key" }
            skip()
            when {
                peek(',') -> expect(',')
                peek('}') -> { expect('}'); return FindingJsonValue.Obj(values) }
                else -> error("Expected ',' or '}'.")
            }
        }
    }
    private fun parseArray(): FindingJsonValue.Arr {
        expect('['); skip(); val values = mutableListOf<FindingJsonValue>()
        if (peek(']')) { expect(']'); return FindingJsonValue.Arr(values) }
        while (true) {
            values += parseValue(); skip()
            when {
                peek(',') -> expect(',')
                peek(']') -> { expect(']'); return FindingJsonValue.Arr(values) }
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
    private fun expect(c: Char) { skip(); require(peek(c)) { "Expected '$c'." }; index++ }
    private fun peek(c: Char) = index < input.length && input[index] == c
    private fun skip() { while (index < input.length && input[index].isWhitespace()) index++ }
}

package io.docpilot.release

public class ReleaseEvidenceCodec(
    private val evaluator: ReleaseGateEvaluator = ReleaseGateEvaluator(),
) {
    public fun create(input: ReleaseEvidenceInput): ReleaseEvidenceManifest {
        val gate = evaluator.evaluate(input)
        val unsigned = ReleaseEvidenceManifest(
            releaseId = input.releaseId,
            candidate = input.candidate,
            contracts = input.contracts,
            executions = input.executions.sortedBy { it.id },
            testAggregate = input.testAggregate,
            artifacts = input.artifacts.sortedBy { it.id },
            compatibilityChecks = input.compatibilityChecks.sortedBy { it.id },
            scopeChecks = input.scopeChecks.copy(
                changedPaths = input.scopeChecks.changedPaths.sorted(),
                forbiddenGeneratedPaths = input.scopeChecks.forbiddenGeneratedPaths.sorted(),
            ),
            policy = input.policy,
            gate = gate,
            integrity = ReleaseIntegrity(payloadSha256 = "0".repeat(64)),
        )
        return unsigned.copy(integrity = ReleaseIntegrity(payloadSha256 = sha256(encodePayload(unsigned))))
    }

    public fun encode(manifest: ReleaseEvidenceManifest): String {
        validateIntegrity(manifest)
        return buildString {
            append("{\n")
            append(encodePayloadFields(manifest))
            append(",\n  \"integrity\": ")
            appendObject(
                listOf(
                    "algorithm" to json(manifest.integrity.algorithm),
                    "payloadSha256" to json(manifest.integrity.payloadSha256),
                ),
                1,
            )
            append("\n}\n")
        }
    }

    public fun encodePayload(manifest: ReleaseEvidenceManifest): String =
        "{\n${encodePayloadFields(manifest)}\n}\n"

    public fun decode(encoded: String): ReleaseEvidenceManifest {
        val root = JsonParser(encoded).parse().obj()
        root.requireKeys(
            "releaseEvidenceFormatVersion", "releaseId", "candidate", "contracts",
            "executions", "testAggregate", "artifacts", "compatibilityChecks",
            "scopeChecks", "policy", "gate", "integrity",
        )
        val version = root.int("releaseEvidenceFormatVersion")
        require(version == ReleaseEvidenceFormat.CURRENT_VERSION) {
            "Unsupported Release Evidence Manifest format: $version"
        }
        val candidate = root.obj("candidate").let {
            it.requireKeys("coreCommit", "branch", "repositoryClean", "mcpMode", "mcpCommit", "mcpVersion")
            ReleaseCandidate(
                it.string("coreCommit"), it.string("branch"), it.bool("repositoryClean"),
                McpMode.valueOf(it.string("mcpMode")), it.string("mcpCommit"), it.string("mcpVersion"),
            )
        }
        val contracts = root.obj("contracts").let {
            it.requireKeys(
                "dirSchemaVersion", "specificationSnapshotFormatVersion",
                "reviewBundleFormatVersion", "cliOutputFormatVersion", "cliExitCodeContractVersion",
            )
            ReleaseContracts(
                it.string("dirSchemaVersion"),
                it.int("specificationSnapshotFormatVersion"),
                it.int("reviewBundleFormatVersion"),
                it.int("cliOutputFormatVersion"),
                it.int("cliExitCodeContractVersion"),
            )
        }
        val executions = root.array("executions").map { value ->
            value.obj().let {
                it.requireKeys(
                    "id", "kind", "commandArguments", "workingDirectory", "exitCode",
                    "result", "required", "outputArtifactIds",
                )
                ReleaseExecution(
                    it.string("id"), it.string("kind"), it.stringArray("commandArguments"),
                    it.string("workingDirectory"), it.int("exitCode"),
                    EvidenceResult.valueOf(it.string("result")), it.bool("required"),
                    it.stringArray("outputArtifactIds"),
                )
            }
        }
        val tests = root.obj("testAggregate").let {
            it.requireKeys("xmlFileCount", "tests", "failures", "errors", "skipped", "fresh", "cached")
            TestAggregate(
                it.int("xmlFileCount"), it.int("tests"), it.int("failures"),
                it.int("errors"), it.int("skipped"), it.bool("fresh"), it.bool("cached"),
            )
        }
        val artifacts = root.array("artifacts").map { value ->
            value.obj().let {
                it.requireKeys("id", "kind", "path", "sizeBytes", "sha256", "required", "producerExecutionId")
                ReleaseArtifact(
                    it.string("id"), ArtifactKind.valueOf(it.string("kind")), it.string("path"),
                    it.long("sizeBytes"), it.string("sha256"), it.bool("required"),
                    it.string("producerExecutionId"),
                )
            }
        }
        val checks = root.array("compatibilityChecks").map { value ->
            value.obj().let {
                it.requireKeys("id", "expected", "actual", "result", "evidenceArtifactIds")
                ReleaseCheck(
                    it.string("id"), it.string("expected"), it.string("actual"),
                    EvidenceResult.valueOf(it.string("result")), it.stringArray("evidenceArtifactIds"),
                )
            }
        }
        val scope = root.obj("scopeChecks").let {
            it.requireKeys(
                "comparisonCommit", "changedPaths", "forbiddenGeneratedPaths",
                "repositoryClean", "submodulesClean", "documentationSynchronized",
            )
            ScopeChecks(
                it.nullableString("comparisonCommit"), it.stringArray("changedPaths"),
                it.stringArray("forbiddenGeneratedPaths"), it.bool("repositoryClean"),
                it.bool("submodulesClean"), it.bool("documentationSynchronized"),
            )
        }
        val policy = root.obj("policy").let {
            it.requireKeys("id", "version", "requireZeroSkipped", "requireFreshTests", "allowCachedTests")
            ReleasePolicy(
                it.string("id"), it.int("version"), it.bool("requireZeroSkipped"),
                it.bool("requireFreshTests"), it.bool("allowCachedTests"),
            )
        }
        val gate = root.obj("gate").let {
            it.requireKeys("result", "failures")
            GateDecision(
                EvidenceResult.valueOf(it.string("result")),
                it.stringArray("failures").map(GateFailure::valueOf),
            )
        }
        val integrity = root.obj("integrity").let {
            it.requireKeys("algorithm", "payloadSha256")
            ReleaseIntegrity(it.string("algorithm"), it.string("payloadSha256"))
        }
        return ReleaseEvidenceManifest(
            version, root.string("releaseId"), candidate, contracts, executions, tests,
            artifacts, checks, scope, policy, gate, integrity,
        ).also(::validateIntegrity)
    }

    public fun validateIntegrity(manifest: ReleaseEvidenceManifest) {
        require(sha256(encodePayload(manifest)) == manifest.integrity.payloadSha256) {
            "Release Evidence Manifest integrity mismatch."
        }
    }

    private fun encodePayloadFields(m: ReleaseEvidenceManifest): String = buildString {
        append("  \"releaseEvidenceFormatVersion\": ${m.releaseEvidenceFormatVersion},\n")
        append("  \"releaseId\": ").append(json(m.releaseId)).append(",\n")
        append("  \"candidate\": ")
        appendObject(
            listOf(
                "coreCommit" to json(m.candidate.coreCommit),
                "branch" to json(m.candidate.branch),
                "repositoryClean" to m.candidate.repositoryClean.toString(),
                "mcpMode" to json(m.candidate.mcpMode.name),
                "mcpCommit" to json(m.candidate.mcpCommit),
                "mcpVersion" to json(m.candidate.mcpVersion),
            ), 1,
        )
        append(",\n  \"contracts\": ")
        appendObject(
            listOf(
                "dirSchemaVersion" to json(m.contracts.dirSchemaVersion),
                "specificationSnapshotFormatVersion" to m.contracts.specificationSnapshotFormatVersion.toString(),
                "reviewBundleFormatVersion" to m.contracts.reviewBundleFormatVersion.toString(),
                "cliOutputFormatVersion" to m.contracts.cliOutputFormatVersion.toString(),
                "cliExitCodeContractVersion" to m.contracts.cliExitCodeContractVersion.toString(),
            ), 1,
        )
        append(",\n  \"executions\": ")
        appendArray(m.executions.sortedBy { it.id }.map(::executionJson), 1)
        append(",\n  \"testAggregate\": ")
        appendObject(
            listOf(
                "xmlFileCount" to m.testAggregate.xmlFileCount.toString(),
                "tests" to m.testAggregate.tests.toString(),
                "failures" to m.testAggregate.failures.toString(),
                "errors" to m.testAggregate.errors.toString(),
                "skipped" to m.testAggregate.skipped.toString(),
                "fresh" to m.testAggregate.fresh.toString(),
                "cached" to m.testAggregate.cached.toString(),
            ), 1,
        )
        append(",\n  \"artifacts\": ")
        appendArray(m.artifacts.sortedBy { it.id }.map(::artifactJson), 1)
        append(",\n  \"compatibilityChecks\": ")
        appendArray(m.compatibilityChecks.sortedBy { it.id }.map(::checkJson), 1)
        append(",\n  \"scopeChecks\": ")
        appendObject(
            listOf(
                "comparisonCommit" to (m.scopeChecks.comparisonCommit?.let(::json) ?: "null"),
                "changedPaths" to stringArray(m.scopeChecks.changedPaths.sorted()),
                "forbiddenGeneratedPaths" to stringArray(m.scopeChecks.forbiddenGeneratedPaths.sorted()),
                "repositoryClean" to m.scopeChecks.repositoryClean.toString(),
                "submodulesClean" to m.scopeChecks.submodulesClean.toString(),
                "documentationSynchronized" to m.scopeChecks.documentationSynchronized.toString(),
            ), 1,
        )
        append(",\n  \"policy\": ")
        appendObject(
            listOf(
                "id" to json(m.policy.id),
                "version" to m.policy.version.toString(),
                "requireZeroSkipped" to m.policy.requireZeroSkipped.toString(),
                "requireFreshTests" to m.policy.requireFreshTests.toString(),
                "allowCachedTests" to m.policy.allowCachedTests.toString(),
            ), 1,
        )
        append(",\n  \"gate\": ")
        appendObject(
            listOf(
                "result" to json(m.gate.result.name),
                "failures" to stringArray(m.gate.failures.map { it.name }),
            ), 1,
        )
    }

    private fun executionJson(e: ReleaseExecution): String = objectJson(
        listOf(
            "id" to json(e.id), "kind" to json(e.kind),
            "commandArguments" to stringArray(e.commandArguments),
            "workingDirectory" to json(e.workingDirectory), "exitCode" to e.exitCode.toString(),
            "result" to json(e.result.name), "required" to e.required.toString(),
            "outputArtifactIds" to stringArray(e.outputArtifactIds.sorted()),
        ),
    )

    private fun artifactJson(a: ReleaseArtifact): String = objectJson(
        listOf(
            "id" to json(a.id), "kind" to json(a.kind.name), "path" to json(a.path),
            "sizeBytes" to a.sizeBytes.toString(), "sha256" to json(a.sha256),
            "required" to a.required.toString(), "producerExecutionId" to json(a.producerExecutionId),
        ),
    )

    private fun checkJson(c: ReleaseCheck): String = objectJson(
        listOf(
            "id" to json(c.id), "expected" to json(c.expected), "actual" to json(c.actual),
            "result" to json(c.result.name),
            "evidenceArtifactIds" to stringArray(c.evidenceArtifactIds.sorted()),
        ),
    )

    private fun objectJson(fields: List<Pair<String, String>>): String =
        buildString { appendObject(fields, 0) }

    private fun StringBuilder.appendObject(fields: List<Pair<String, String>>, indent: Int) {
        append('{')
        fields.forEachIndexed { index, (name, value) ->
            if (index > 0) append(',')
            append('\n').append("  ".repeat(indent + 1)).append(json(name)).append(": ").append(value.indent(indent + 1))
        }
        append('\n').append("  ".repeat(indent)).append('}')
    }

    private fun StringBuilder.appendArray(values: List<String>, indent: Int) {
        if (values.isEmpty()) {
            append("[]")
            return
        }
        append('[')
        values.forEachIndexed { index, value ->
            if (index > 0) append(',')
            append('\n').append("  ".repeat(indent + 1)).append(value.indent(indent + 1))
        }
        append('\n').append("  ".repeat(indent)).append(']')
    }

    private fun String.indent(level: Int): String =
        replace("\n", "\n${"  ".repeat(level)}")

    private fun stringArray(values: List<String>): String =
        if (values.isEmpty()) "[]" else values.joinToString(prefix = "[", postfix = "]") { json(it) }

    private fun json(value: String): String = buildString {
        append('"')
        value.forEach {
            when (it) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (it.code < 0x20) append("\\u%04x".format(it.code)) else append(it)
            }
        }
        append('"')
    }
}

private sealed interface JsonValue {
    data class Obj(val values: LinkedHashMap<String, JsonValue>) : JsonValue
    data class Arr(val values: List<JsonValue>) : JsonValue
    data class Str(val value: String) : JsonValue
    data class Num(val value: Long) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data object Null : JsonValue
}

private fun JsonValue.obj(): JsonValue.Obj = this as? JsonValue.Obj ?: error("Expected JSON object.")
private fun JsonValue.Obj.requireKeys(vararg expected: String) {
    require(values.keys.toList() == expected.toList()) {
        "Unexpected JSON fields. Expected ${expected.toList()}, found ${values.keys}."
    }
}
private fun JsonValue.Obj.string(name: String) = (values[name] as? JsonValue.Str)?.value ?: error("$name must be a string.")
private fun JsonValue.Obj.nullableString(name: String) = when (val value = values[name]) {
    JsonValue.Null -> null
    is JsonValue.Str -> value.value
    else -> error("$name must be a string or null.")
}
private fun JsonValue.Obj.long(name: String) = (values[name] as? JsonValue.Num)?.value ?: error("$name must be an integer.")
private fun JsonValue.Obj.int(name: String): Int = Math.toIntExact(long(name))
private fun JsonValue.Obj.bool(name: String) = (values[name] as? JsonValue.Bool)?.value ?: error("$name must be boolean.")
private fun JsonValue.Obj.obj(name: String) = values[name]?.obj() ?: error("$name must be an object.")
private fun JsonValue.Obj.array(name: String) = (values[name] as? JsonValue.Arr)?.values ?: error("$name must be an array.")
private fun JsonValue.Obj.stringArray(name: String) = array(name).map {
    (it as? JsonValue.Str)?.value ?: error("$name must contain strings.")
}

private class JsonParser(private val input: String) {
    private var index = 0

    fun parse(): JsonValue {
        val value = value()
        whitespace()
        require(index == input.length) { "Trailing JSON content." }
        return value
    }

    private fun value(): JsonValue {
        whitespace()
        require(index < input.length) { "Unexpected end of JSON." }
        return when (input[index]) {
            '{' -> obj()
            '[' -> array()
            '"' -> JsonValue.Str(string())
            't' -> literal("true", JsonValue.Bool(true))
            'f' -> literal("false", JsonValue.Bool(false))
            'n' -> literal("null", JsonValue.Null)
            '-', in '0'..'9' -> number()
            else -> error("Unexpected JSON token at $index.")
        }
    }

    private fun obj(): JsonValue.Obj {
        expect('{')
        whitespace()
        val values = linkedMapOf<String, JsonValue>()
        if (peek('}')) {
            index++
            return JsonValue.Obj(values)
        }
        while (true) {
            whitespace()
            val key = string()
            require(!values.containsKey(key)) { "Duplicate JSON field: $key" }
            whitespace()
            expect(':')
            values[key] = value()
            whitespace()
            if (peek('}')) {
                index++
                return JsonValue.Obj(values)
            }
            expect(',')
        }
    }

    private fun array(): JsonValue.Arr {
        expect('[')
        whitespace()
        val values = mutableListOf<JsonValue>()
        if (peek(']')) {
            index++
            return JsonValue.Arr(values)
        }
        while (true) {
            values += value()
            whitespace()
            if (peek(']')) {
                index++
                return JsonValue.Arr(values)
            }
            expect(',')
        }
    }

    private fun string(): String {
        expect('"')
        return buildString {
            while (true) {
                require(index < input.length) { "Unterminated JSON string." }
                val char = input[index++]
                when (char) {
                    '"' -> return@buildString
                    '\\' -> {
                        require(index < input.length) { "Unterminated JSON escape." }
                        append(
                            when (val escaped = input[index++]) {
                                '"', '\\', '/' -> escaped
                                'b' -> '\b'
                                'f' -> '\u000C'
                                'n' -> '\n'
                                'r' -> '\r'
                                't' -> '\t'
                                'u' -> {
                                    require(index + 4 <= input.length) { "Invalid Unicode escape." }
                                    input.substring(index, index + 4).toInt(16).toChar().also { index += 4 }
                                }
                                else -> error("Invalid JSON escape: $escaped")
                            },
                        )
                    }
                    else -> {
                        require(char.code >= 0x20) { "Control character in JSON string." }
                        append(char)
                    }
                }
            }
        }
    }

    private fun number(): JsonValue.Num {
        val start = index
        if (peek('-')) index++
        require(index < input.length && input[index].isDigit())
        if (input[index] == '0') index++ else while (index < input.length && input[index].isDigit()) index++
        require(index == input.length || input[index] !in listOf('.', 'e', 'E')) { "Only integers are supported." }
        return JsonValue.Num(input.substring(start, index).toLong())
    }

    private fun <T : JsonValue> literal(text: String, value: T): T {
        require(input.startsWith(text, index)) { "Invalid JSON literal." }
        index += text.length
        return value
    }

    private fun expect(char: Char) {
        whitespace()
        require(index < input.length && input[index] == char) { "Expected '$char' at $index." }
        index++
    }
    private fun peek(char: Char) = index < input.length && input[index] == char
    private fun whitespace() {
        while (index < input.length && input[index].isWhitespace()) index++
    }
}

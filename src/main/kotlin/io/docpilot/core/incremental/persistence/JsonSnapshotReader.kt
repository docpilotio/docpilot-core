package io.docpilot.core.incremental.persistence

import io.docpilot.core.incremental.ProjectSnapshot
import io.docpilot.core.incremental.SourceFileFingerprint
import java.time.Instant

/**
 * Reads only the RFC-0028 snapshot JSON schema.
 *
 * Keeping this parser schema-focused avoids adding a JSON dependency to the
 * core module while still validating malformed or unsupported snapshots.
 */
class JsonSnapshotReader {

    fun read(
        value: String,
    ): StoredProjectSnapshot {
        val parser = SnapshotJsonParser(value)
        val root = parser.parseRootObject()
        parser.requireEnd()

        val schemaVersion = root.requiredLong("schemaVersion").toIntExact(
            fieldName = "schemaVersion",
        )
        require(schemaVersion == SnapshotFormat.CURRENT_SCHEMA_VERSION) {
            "Unsupported snapshot schema version: $schemaVersion"
        }

        val createdAt = Instant.parse(root.requiredString("createdAt"))
        val files = root.requiredArray("files")
            .mapIndexed { index, item ->
                val file = item as? SnapshotJsonValue.ObjectValue
                    ?: error("Snapshot file at index $index must be a JSON object.")

                SourceFileFingerprint(
                    relativePath = file.requiredString("relativePath"),
                    contentSha256 = file.requiredString("contentSha256"),
                    sizeBytes = file.requiredLong("sizeBytes"),
                )
            }
            .sortedBy { it.relativePath }

        return StoredProjectSnapshot(
            schemaVersion = schemaVersion,
            createdAt = createdAt,
            snapshot = ProjectSnapshot(files),
        )
    }

    private fun Long.toIntExact(
        fieldName: String,
    ): Int {
        require(this in Int.MIN_VALUE..Int.MAX_VALUE) {
            "JSON number is outside Int range: $fieldName"
        }
        return toInt()
    }
}

private sealed interface SnapshotJsonValue {
    data class ObjectValue(
        val values: Map<String, SnapshotJsonValue>,
    ) : SnapshotJsonValue

    data class ArrayValue(
        val values: List<SnapshotJsonValue>,
    ) : SnapshotJsonValue

    data class StringValue(
        val value: String,
    ) : SnapshotJsonValue

    data class NumberValue(
        val value: Long,
    ) : SnapshotJsonValue

    data object NullValue : SnapshotJsonValue
}

private fun SnapshotJsonValue.ObjectValue.requiredString(
    name: String,
): String =
    (values[name] as? SnapshotJsonValue.StringValue)?.value
        ?: error("Missing or invalid JSON string: $name")

private fun SnapshotJsonValue.ObjectValue.requiredLong(
    name: String,
): Long =
    (values[name] as? SnapshotJsonValue.NumberValue)?.value
        ?: error("Missing or invalid JSON number: $name")

private fun SnapshotJsonValue.ObjectValue.requiredArray(
    name: String,
): List<SnapshotJsonValue> =
    (values[name] as? SnapshotJsonValue.ArrayValue)?.values
        ?: error("Missing or invalid JSON array: $name")

private class SnapshotJsonParser(
    private val input: String,
) {
    private var index: Int = 0

    fun parseRootObject(): SnapshotJsonValue.ObjectValue =
        parseValue() as? SnapshotJsonValue.ObjectValue
            ?: error("Snapshot JSON root must be an object.")

    fun requireEnd() {
        skipWhitespace()
        require(index == input.length) {
            "Unexpected trailing JSON content at index $index."
        }
    }

    private fun parseValue(): SnapshotJsonValue {
        skipWhitespace()
        require(index < input.length) {
            "Unexpected end of JSON input."
        }

        return when (input[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> SnapshotJsonValue.StringValue(parseString())
            'n' -> {
                requireToken("null")
                SnapshotJsonValue.NullValue
            }
            '-', in '0'..'9' ->
                SnapshotJsonValue.NumberValue(parseLong())
            else -> error(
                "Unexpected JSON character '${input[index]}' at index $index.",
            )
        }
    }

    private fun parseObject(): SnapshotJsonValue.ObjectValue {
        expect('{')
        skipWhitespace()

        val values = linkedMapOf<String, SnapshotJsonValue>()
        if (peek('}')) {
            expect('}')
            return SnapshotJsonValue.ObjectValue(values)
        }

        while (true) {
            val name = parseString()
            skipWhitespace()
            expect(':')
            val parsedValue = parseValue()
            require(values.put(name, parsedValue) == null) {
                "Duplicate JSON field: $name"
            }
            skipWhitespace()

            when {
                peek(',') -> expect(',')
                peek('}') -> {
                    expect('}')
                    return SnapshotJsonValue.ObjectValue(values)
                }
                else -> error("Expected ',' or '}' at index $index.")
            }
        }
    }

    private fun parseArray(): SnapshotJsonValue.ArrayValue {
        expect('[')
        skipWhitespace()

        val values = mutableListOf<SnapshotJsonValue>()
        if (peek(']')) {
            expect(']')
            return SnapshotJsonValue.ArrayValue(values)
        }

        while (true) {
            values += parseValue()
            skipWhitespace()

            when {
                peek(',') -> expect(',')
                peek(']') -> {
                    expect(']')
                    return SnapshotJsonValue.ArrayValue(values)
                }
                else -> error("Expected ',' or ']' at index $index.")
            }
        }
    }

    private fun parseString(): String {
        skipWhitespace()
        expect('"')
        val result = StringBuilder()

        while (index < input.length) {
            val character = input[index++]
            when {
                character == '"' -> return result.toString()
                character == '\\' -> result.append(parseEscape())
                character.code < 0x20 -> error(
                    "Unescaped control character in JSON string at index ${index - 1}.",
                )
                else -> result.append(character)
            }
        }

        error("Unterminated JSON string.")
    }

    private fun parseEscape(): Char {
        require(index < input.length) {
            "Unterminated JSON escape."
        }

        return when (val escaped = input[index++]) {
            '"' -> '"'
            '\\' -> '\\'
            '/' -> '/'
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> parseUnicodeEscape()
            else -> error("Unsupported JSON escape: \\$escaped")
        }
    }

    private fun parseUnicodeEscape(): Char {
        require(index + 4 <= input.length) {
            "Incomplete JSON unicode escape."
        }

        val digits = input.substring(index, index + 4)
        require(digits.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            "Invalid JSON unicode escape: \\u$digits"
        }
        index += 4
        return digits.toInt(16).toChar()
    }

    private fun parseLong(): Long {
        val start = index
        if (peek('-')) {
            index++
        }

        require(index < input.length && input[index].isDigit()) {
            "Invalid JSON number at index $start."
        }

        if (input[index] == '0') {
            index++
            require(index >= input.length || !input[index].isDigit()) {
                "JSON numbers must not contain leading zeroes at index $start."
            }
        } else {
            while (index < input.length && input[index].isDigit()) {
                index++
            }
        }

        require(index >= input.length || input[index] !in listOf('.', 'e', 'E')) {
            "Snapshot JSON numbers must be integers at index $start."
        }

        return input.substring(start, index).toLongOrNull()
            ?: error("JSON integer is outside Long range at index $start.")
    }

    private fun requireToken(
        token: String,
    ) {
        require(input.startsWith(token, index)) {
            "Expected '$token' at index $index."
        }
        index += token.length
    }

    private fun expect(
        expected: Char,
    ) {
        skipWhitespace()
        require(index < input.length && input[index] == expected) {
            "Expected '$expected' at index $index."
        }
        index++
    }

    private fun peek(
        expected: Char,
    ): Boolean = index < input.length && input[index] == expected

    private fun skipWhitespace() {
        while (index < input.length && input[index].isWhitespace()) {
            index++
        }
    }
}

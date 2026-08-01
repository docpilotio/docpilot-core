package io.docpilot.provider.openai

internal sealed interface JsonValue {
    data class Object(val fields: Map<String, JsonValue>) : JsonValue
    data class Array(val values: List<JsonValue>) : JsonValue
    data class StringValue(val value: String) : JsonValue
    data class NumberValue(val value: String) : JsonValue
    data class BooleanValue(val value: Boolean) : JsonValue
    data object NullValue : JsonValue
}

internal object OpenAiJson {
    fun requestBody(
        model: String,
        messages: List<Pair<String, String>>,
        temperature: Double?,
        maxOutputTokens: Int?,
        jsonResponse: Boolean,
    ): String {
        val fields = linkedMapOf<String, JsonValue>(
            "model" to JsonValue.StringValue(model),
            "input" to JsonValue.Array(messages.map { (role, content) ->
                JsonValue.Object(linkedMapOf(
                    "role" to JsonValue.StringValue(role),
                    "content" to JsonValue.StringValue(content),
                ))
            }),
        )
        temperature?.let { fields["temperature"] = JsonValue.NumberValue(it.toString()) }
        maxOutputTokens?.let { fields["max_output_tokens"] = JsonValue.NumberValue(it.toString()) }
        if (jsonResponse) {
            fields["text"] = JsonValue.Object(mapOf(
                "format" to JsonValue.Object(mapOf(
                    "type" to JsonValue.StringValue("json_object"),
                )),
            ))
        }
        return stringify(JsonValue.Object(fields))
    }

    fun parse(json: String): JsonValue = Parser(json).parse()

    fun stringify(value: JsonValue): String = when (value) {
        is JsonValue.Object -> value.fields.entries.joinToString(",", "{", "}") {
            "${string(it.key)}:${stringify(it.value)}"
        }
        is JsonValue.Array -> value.values.joinToString(",", "[", "]", transform = ::stringify)
        is JsonValue.StringValue -> string(value.value)
        is JsonValue.NumberValue -> value.value
        is JsonValue.BooleanValue -> value.value.toString()
        JsonValue.NullValue -> "null"
    }

    private fun string(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    private class Parser(private val source: String) {
        private var index = 0

        fun parse(): JsonValue {
            val value = value()
            whitespace()
            require(index == source.length) { "Unexpected trailing JSON data." }
            return value
        }

        private fun value(): JsonValue {
            whitespace()
            require(index < source.length) { "Unexpected end of JSON." }
            return when (source[index]) {
                '{' -> objectValue()
                '[' -> arrayValue()
                '"' -> JsonValue.StringValue(stringValue())
                't' -> literal("true", JsonValue.BooleanValue(true))
                'f' -> literal("false", JsonValue.BooleanValue(false))
                'n' -> literal("null", JsonValue.NullValue)
                '-', in '0'..'9' -> numberValue()
                else -> error("Invalid JSON value at offset $index.")
            }
        }

        private fun objectValue(): JsonValue.Object {
            index++
            whitespace()
            val fields = linkedMapOf<String, JsonValue>()
            if (consume('}')) return JsonValue.Object(fields)
            while (true) {
                whitespace()
                require(index < source.length && source[index] == '"') { "Expected JSON object key." }
                val key = stringValue()
                whitespace()
                require(consume(':')) { "Expected ':' after JSON object key." }
                require(fields.put(key, value()) == null) { "Duplicate JSON object key: $key" }
                whitespace()
                if (consume('}')) return JsonValue.Object(fields)
                require(consume(',')) { "Expected ',' in JSON object." }
            }
        }

        private fun arrayValue(): JsonValue.Array {
            index++
            whitespace()
            val values = mutableListOf<JsonValue>()
            if (consume(']')) return JsonValue.Array(values)
            while (true) {
                values += value()
                whitespace()
                if (consume(']')) return JsonValue.Array(values)
                require(consume(',')) { "Expected ',' in JSON array." }
            }
        }

        private fun stringValue(): String {
            require(consume('"'))
            val result = StringBuilder()
            while (index < source.length) {
                when (val c = source[index++]) {
                    '"' -> return result.toString()
                    '\\' -> {
                        require(index < source.length) { "Invalid JSON escape." }
                        when (val escaped = source[index++]) {
                            '"', '\\', '/' -> result.append(escaped)
                            'b' -> result.append('\b')
                            'f' -> result.append('\u000C')
                            'n' -> result.append('\n')
                            'r' -> result.append('\r')
                            't' -> result.append('\t')
                            'u' -> {
                                require(index + 4 <= source.length) { "Invalid unicode escape." }
                                result.append(source.substring(index, index + 4).toInt(16).toChar())
                                index += 4
                            }
                            else -> error("Unsupported JSON escape: $escaped")
                        }
                    }
                    else -> {
                        require(c.code >= 0x20) { "Unescaped control character in JSON string." }
                        result.append(c)
                    }
                }
            }
            error("Unterminated JSON string.")
        }

        private fun numberValue(): JsonValue.NumberValue {
            val start = index
            if (source[index] == '-') index++
            require(index < source.length)
            if (source[index] == '0') index++ else {
                require(source[index] in '1'..'9') { "Invalid JSON number." }
                while (index < source.length && source[index].isDigit()) index++
            }
            if (index < source.length && source[index] == '.') {
                index++
                require(index < source.length && source[index].isDigit()) { "Invalid JSON number fraction." }
                while (index < source.length && source[index].isDigit()) index++
            }
            if (index < source.length && source[index] in "eE") {
                index++
                if (index < source.length && source[index] in "+-") index++
                require(index < source.length && source[index].isDigit()) { "Invalid JSON number exponent." }
                while (index < source.length && source[index].isDigit()) index++
            }
            return JsonValue.NumberValue(source.substring(start, index))
        }

        private fun <T : JsonValue> literal(text: String, value: T): T {
            require(source.startsWith(text, index)) { "Invalid JSON literal." }
            index += text.length
            return value
        }

        private fun whitespace() {
            while (index < source.length && source[index].isWhitespace()) index++
        }

        private fun consume(expected: Char): Boolean =
            if (index < source.length && source[index] == expected) {
                index++
                true
            } else false
    }
}

internal fun JsonValue.Object.string(name: String): String? =
    (fields[name] as? JsonValue.StringValue)?.value

internal fun JsonValue.Object.objectValue(name: String): JsonValue.Object? =
    fields[name] as? JsonValue.Object

internal fun JsonValue.Object.array(name: String): List<JsonValue> =
    (fields[name] as? JsonValue.Array)?.values.orEmpty()

internal fun JsonValue.Object.int(name: String): Int? =
    (fields[name] as? JsonValue.NumberValue)?.value?.toIntOrNull()

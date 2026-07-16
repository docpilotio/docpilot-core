package io.docpilot.provider.ollama

internal object OllamaJson {

    fun requestBody(
        model: String,
        messages: List<Pair<String, String>>,
        temperature: Double?,
        maxOutputTokens: Int?,
        jsonResponse: Boolean,
    ): String =
        buildString {
            append('{')
            append("\"model\":")
            append(string(model))
            append(",\"messages\":[")
            messages.forEachIndexed { index, (role, content) ->
                if (index > 0) append(',')
                append('{')
                append("\"role\":")
                append(string(role))
                append(",\"content\":")
                append(string(content))
                append('}')
            }
            append(']')
            append(",\"stream\":false")

            if (jsonResponse) {
                append(",\"format\":\"json\"")
            }

            if (temperature != null || maxOutputTokens != null) {
                append(",\"options\":{")
                var needsComma = false

                if (temperature != null) {
                    append("\"temperature\":")
                    append(temperature)
                    needsComma = true
                }

                if (maxOutputTokens != null) {
                    if (needsComma) append(',')
                    append("\"num_predict\":")
                    append(maxOutputTokens)
                }

                append('}')
            }

            append('}')
        }

    fun stringField(
        json: String,
        field: String,
    ): String? {
        val keyPosition = findKey(json, field) ?: return null
        var position = skipWhitespace(
            json,
            keyPosition + quotedKey(field).length,
        )

        if (position >= json.length || json[position] != ':') {
            return null
        }

        position = skipWhitespace(json, position + 1)

        if (position >= json.length || json[position] != '"') {
            return null
        }

        return parseString(json, position).first
    }

    fun nestedStringField(
        json: String,
        objectField: String,
        field: String,
    ): String? {
        val objectJson = objectField(json, objectField) ?: return null
        return stringField(objectJson, field)
    }

    fun intField(
        json: String,
        field: String,
    ): Int? {
        val keyPosition = findKey(json, field) ?: return null
        var position = skipWhitespace(
            json,
            keyPosition + quotedKey(field).length,
        )

        if (position >= json.length || json[position] != ':') {
            return null
        }

        position = skipWhitespace(json, position + 1)
        val end = generateSequence(position) { it + 1 }
            .takeWhile {
                it < json.length &&
                    (json[it].isDigit() || json[it] == '-')
            }
            .lastOrNull()
            ?.plus(1)
            ?: return null

        return json.substring(position, end).toIntOrNull()
    }

    fun errorMessage(json: String): String? =
        stringField(json, "error")

    private fun objectField(
        json: String,
        field: String,
    ): String? {
        val keyPosition = findKey(json, field) ?: return null
        var position = skipWhitespace(
            json,
            keyPosition + quotedKey(field).length,
        )

        if (position >= json.length || json[position] != ':') {
            return null
        }

        position = skipWhitespace(json, position + 1)

        if (position >= json.length || json[position] != '{') {
            return null
        }

        var depth = 0
        var inString = false
        var escaped = false

        for (index in position until json.length) {
            val character = json[index]

            if (inString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> inString = false
                }
                continue
            }

            when (character) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return json.substring(position, index + 1)
                    }
                }
            }
        }

        return null
    }

    private fun findKey(
        json: String,
        field: String,
    ): Int? {
        val key = quotedKey(field)
        var fromIndex = 0

        while (true) {
            val index = json.indexOf(key, fromIndex)
            if (index < 0) return null

            val afterKey = skipWhitespace(json, index + key.length)
            if (afterKey < json.length && json[afterKey] == ':') {
                return index
            }

            fromIndex = index + key.length
        }
    }

    private fun quotedKey(field: String): String =
        "\"$field\""

    private fun skipWhitespace(
        text: String,
        start: Int,
    ): Int {
        var index = start
        while (index < text.length && text[index].isWhitespace()) {
            index++
        }
        return index
    }

    private fun parseString(
        json: String,
        quotePosition: Int,
    ): Pair<String, Int> {
        val result = StringBuilder()
        var index = quotePosition + 1

        while (index < json.length) {
            val character = json[index++]

            when (character) {
                '"' -> return result.toString() to index
                '\\' -> {
                    require(index < json.length) {
                        "Invalid JSON escape sequence."
                    }
                    when (val escaped = json[index++]) {
                        '"' -> result.append('"')
                        '\\' -> result.append('\\')
                        '/' -> result.append('/')
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000C')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> {
                            require(index + 4 <= json.length) {
                                "Invalid JSON unicode escape."
                            }
                            result.append(
                                json.substring(index, index + 4)
                                    .toInt(16)
                                    .toChar(),
                            )
                            index += 4
                        }
                        else -> error(
                            "Unsupported JSON escape: $escaped",
                        )
                    }
                }
                else -> result.append(character)
            }
        }

        error("Unterminated JSON string.")
    }

    private fun string(value: String): String =
        buildString {
            append('"')
            value.forEach { character ->
                when (character) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else ->
                        if (character.code < 0x20) {
                            append(
                                "\\u%04x".format(character.code),
                            )
                        } else {
                            append(character)
                        }
                }
            }
            append('"')
        }
}

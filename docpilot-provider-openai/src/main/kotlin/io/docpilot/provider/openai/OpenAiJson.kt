package io.docpilot.provider.openai

internal object OpenAiJson {

    fun requestBody(
        model: String,
        messages: List<Pair<String, String>>,
        temperature: Double?,
        maxOutputTokens: Int?,
        jsonResponse: Boolean,
    ): String = buildString {
        append('{')
        append("\"model\":")
        append(string(model))
        append(",\"input\":[")
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
        temperature?.let {
            append(",\"temperature\":")
            append(it)
        }
        maxOutputTokens?.let {
            append(",\"max_output_tokens\":")
            append(it)
        }
        if (jsonResponse) {
            append(",\"text\":{\"format\":{\"type\":\"json_object\"}}")
        }
        append('}')
    }

    fun stringField(json: String, field: String): String? {
        val key = "\"$field\""
        var from = 0
        while (true) {
            val index = json.indexOf(key, from)
            if (index < 0) return null
            var position = skipWhitespace(json, index + key.length)
            if (position < json.length && json[position] == ':') {
                position = skipWhitespace(json, position + 1)
                if (position < json.length && json[position] == '"') {
                    return parseString(json, position).first
                }
            }
            from = index + key.length
        }
    }

    fun nestedStringField(json: String, objectField: String, field: String): String? =
        objectValue(json, objectField)?.let { stringField(it, field) }

    fun intField(json: String, field: String): Int? {
        val key = "\"$field\""
        val index = json.indexOf(key)
        if (index < 0) return null
        var position = skipWhitespace(json, index + key.length)
        if (position >= json.length || json[position] != ':') return null
        position = skipWhitespace(json, position + 1)
        var end = position
        if (end < json.length && json[end] == '-') end++
        while (end < json.length && json[end].isDigit()) end++
        return json.substring(position, end).toIntOrNull()
    }

    fun errorMessage(json: String): String? =
        nestedStringField(json, "error", "message")

    fun errorCode(json: String): String? =
        nestedStringField(json, "error", "code")

    private fun objectValue(json: String, field: String): String? {
        val key = "\"$field\""
        val index = json.indexOf(key)
        if (index < 0) return null
        var position = skipWhitespace(json, index + key.length)
        if (position >= json.length || json[position] != ':') return null
        position = skipWhitespace(json, position + 1)
        if (position >= json.length || json[position] != '{') return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in position until json.length) {
            val c = json[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return json.substring(position, i + 1)
                    }
                }
            }
        }
        return null
    }

    private fun skipWhitespace(text: String, start: Int): Int {
        var index = start
        while (index < text.length && text[index].isWhitespace()) index++
        return index
    }

    private fun parseString(json: String, quotePosition: Int): Pair<String, Int> {
        val result = StringBuilder()
        var index = quotePosition + 1
        while (index < json.length) {
            val c = json[index++]
            when (c) {
                '"' -> return result.toString() to index
                '\\' -> {
                    require(index < json.length) { "Invalid JSON escape." }
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
                            require(index + 4 <= json.length) { "Invalid unicode escape." }
                            result.append(json.substring(index, index + 4).toInt(16).toChar())
                            index += 4
                        }
                        else -> error("Unsupported JSON escape: $escaped")
                    }
                }
                else -> result.append(c)
            }
        }
        error("Unterminated JSON string.")
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
}

package io.docpilot.core.incremental.persistence

/**
 * Writes the versioned snapshot schema as stable, human-readable JSON.
 */
class JsonSnapshotWriter {

    fun write(
        value: StoredProjectSnapshot,
    ): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": ${value.schemaVersion},")
        appendLine("  \"createdAt\": \"${escape(value.createdAt.toString())}\",")
        appendLine("  \"files\": [")

        value.snapshot.files.forEachIndexed { index, file ->
            appendLine("    {")
            appendLine("      \"relativePath\": \"${escape(file.relativePath)}\",")
            appendLine("      \"contentSha256\": \"${escape(file.contentSha256)}\",")
            appendLine("      \"sizeBytes\": ${file.sizeBytes}")
            append("    }")
            if (index != value.snapshot.files.lastIndex) {
                append(',')
            }
            appendLine()
        }

        appendLine("  ]")
        append('}')
    }

    private fun escape(
        value: String,
    ): String = buildString {
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (character.code < 0x20) {
                        append("\\u%04x".format(character.code))
                    } else {
                        append(character)
                    }
                }
            }
        }
    }
}

package io.docpilot.cli.io

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories

class OutputWriter {
    fun write(path: Path, content: String): Path {
        val normalized = path.toAbsolutePath().normalize()
        normalized.parent?.createDirectories()
        Files.writeString(normalized, content, StandardCharsets.UTF_8)
        return normalized
    }
}

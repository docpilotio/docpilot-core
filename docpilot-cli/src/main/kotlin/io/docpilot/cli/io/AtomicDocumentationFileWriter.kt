package io.docpilot.cli.io

import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class AtomicDocumentationFileWriter {
    fun replace(path: Path, expectedContent: String, content: String) {
        val normalized = path.toAbsolutePath().normalize()
        require(Files.isRegularFile(normalized)) { "Documentation path is not a regular file: $normalized" }
        require(Files.readString(normalized, StandardCharsets.UTF_8) == expectedContent) {
            "Documentation changed before atomic replacement."
        }
        val temporary = Files.createTempFile(normalized.parent, ".docpilot-review-", ".tmp")
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8)
            require(Files.readString(normalized, StandardCharsets.UTF_8) == expectedContent) {
                "Documentation changed before atomic replacement."
            }
            try {
                Files.move(temporary, normalized, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

package io.docpilot.core.incremental.specification.review

import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

public interface DocumentationResource {
    public fun read(): String
    public fun replace(expectedCurrent: String, replacement: String)
}

public class FileDocumentationResource(private val path: Path) : DocumentationResource {
    override fun read(): String = Files.readString(path, StandardCharsets.UTF_8)

    override fun replace(expectedCurrent: String, replacement: String) {
        require(read() == expectedCurrent) { "Documentation changed before atomic replacement." }
        val directory = requireNotNull(path.toAbsolutePath().normalize().parent)
        val temporary = Files.createTempFile(directory, ".docpilot-document-", ".tmp")
        try {
            Files.writeString(temporary, replacement, StandardCharsets.UTF_8)
            require(read() == expectedCurrent) { "Documentation changed before atomic replacement." }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

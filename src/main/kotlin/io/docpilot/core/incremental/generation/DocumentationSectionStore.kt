package io.docpilot.core.incremental.generation

import io.docpilot.core.generator.architecture.plan.ArchitectureSection
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

interface DocumentationSectionStore {
    fun readDocument(path: Path): String
    fun read(document: String, section: ArchitectureSection): String?
    fun replace(document: String, generatedSections: List<GeneratedSection>): String
    fun writeAtomically(path: Path, document: String)
}

class FileDocumentationSectionStore : DocumentationSectionStore {
    override fun readDocument(path: Path): String =
        if (Files.exists(path)) Files.readString(path, StandardCharsets.UTF_8) else ""

    override fun read(document: String, section: ArchitectureSection): String? {
        val range = sectionRange(document, section.title) ?: return null
        return document.substring(range).trim().takeIf(String::isNotBlank)
    }

    override fun replace(document: String, generatedSections: List<GeneratedSection>): String {
        var merged = document
        generatedSections.forEach { generated ->
            val range = sectionRange(merged, generated.heading)
            merged = if (range == null) {
                val separator = if (merged.isBlank()) "" else "\n\n"
                merged.trimEnd() + separator + generated.markdown.trimEnd() + "\n"
            } else {
                merged.replaceRange(range, generated.markdown.trimEnd())
            }
        }
        return merged.trimEnd() + "\n"
    }

    override fun writeAtomically(path: Path, document: String) {
        path.parent?.let(Files::createDirectories)
        val temp = Files.createTempFile(path.parent ?: Path.of("."), path.fileName.toString(), ".tmp")
        try {
            Files.writeString(temp, document, StandardCharsets.UTF_8)
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private fun sectionRange(document: String, heading: String): IntRange? {
        val startMatch = Regex("(?m)^##\\s+${Regex.escape(heading)}\\s*$").find(document) ?: return null
        val next = Regex("(?m)^##\\s+.+$").find(document, startMatch.range.last + 1)
        val endExclusive = next?.range?.first ?: document.length
        return startMatch.range.first until endExclusive
    }
}

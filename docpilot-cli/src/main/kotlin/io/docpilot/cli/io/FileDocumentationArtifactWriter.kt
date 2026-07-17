package io.docpilot.cli.io

import io.docpilot.core.incremental.execution.DocumentationArtifactWriter
import io.docpilot.core.model.RenderedArtifact
import java.nio.file.Files
import java.nio.file.Path

/** File-system adapter for incremental documentation artifact output. */
class FileDocumentationArtifactWriter(
    outputRoot: Path,
    private val outputWriter: OutputWriter = OutputWriter(),
) : DocumentationArtifactWriter {
    private val normalizedOutputRoot = outputRoot.toAbsolutePath().normalize()

    override fun write(artifact: RenderedArtifact) {
        outputWriter.write(resolveSafely(artifact.relativePath), artifact.content)
    }

    override fun delete(relativePath: String) {
        Files.deleteIfExists(resolveSafely(relativePath))
    }

    private fun resolveSafely(relativePath: String): Path {
        require(relativePath.isNotBlank()) { "Artifact path must not be blank" }
        val resolved = normalizedOutputRoot.resolve(relativePath).normalize()
        require(resolved.startsWith(normalizedOutputRoot)) {
            "Artifact path escapes output root: $relativePath"
        }
        return resolved
    }
}

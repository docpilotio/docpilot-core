package io.docpilot.cli.io

import io.docpilot.core.model.RenderedArtifact
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class FileDocumentationArtifactWriterTest {
    @Test
    fun `writes and deletes artifact under output root`() {
        val root = createTempDirectory("docpilot-artifact-writer")
        val writer = FileDocumentationArtifactWriter(root)
        val artifact = RenderedArtifact("docs/project.md", "text/markdown", "content")

        writer.write(artifact)

        val output = root.resolve("docs/project.md")
        assertEquals("content", output.readText())

        writer.delete(artifact.relativePath)

        assertFalse(Files.exists(output))
    }

    @Test
    fun `rejects path escaping output root`() {
        val writer = FileDocumentationArtifactWriter(createTempDirectory("docpilot-artifact-writer"))

        assertFailsWith<IllegalArgumentException> {
            writer.write(RenderedArtifact("../outside.md", "text/markdown", "content"))
        }
    }
}

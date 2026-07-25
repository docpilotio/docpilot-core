package io.docpilot.release

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentationSyncInspectorTest {
    @Test
    fun `requires canonical completion documents`() {
        val root = Files.createTempDirectory("release-docs")
        val paths = listOf(
            "docs/rfc/RFC-0049-v0.5-Release-Provenance-and-Determinism-Gate.md",
            "docs/planning/RFC-0049-MAIN-PLANNING-UPDATE.md",
            "docs/handoffs/RFC-0049-COMPLETION-HANDOFF.md",
            "docs/roadmap/ROADMAP.md",
        )
        paths.forEach {
            Files.createDirectories(root.resolve(it).parent)
            Files.writeString(root.resolve(it), "RFC-0049 implementation complete")
        }

        assertTrue(DocumentationSyncInspector().inspect(root, implementationComplete = true))
        Files.delete(root.resolve(paths.last()))
        assertFalse(DocumentationSyncInspector().inspect(root, implementationComplete = true))
    }
}

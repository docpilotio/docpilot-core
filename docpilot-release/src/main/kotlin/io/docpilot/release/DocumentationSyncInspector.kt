package io.docpilot.release

import java.nio.file.Files
import java.nio.file.Path

public class DocumentationSyncInspector {
    public fun inspect(repository: Path, implementationComplete: Boolean): Boolean {
        val root = repository.toAbsolutePath().normalize()
        val required = listOf(
            "docs/rfc/RFC-0049-v0.5-Release-Provenance-and-Determinism-Gate.md",
            "docs/planning/RFC-0049-MAIN-PLANNING-UPDATE.md",
            "docs/handoffs/RFC-0049-COMPLETION-HANDOFF.md",
            "docs/roadmap/ROADMAP.md",
        )
        if (required.any { !Files.isRegularFile(root.resolve(it)) }) return false
        val rfc = Files.readString(root.resolve(required[0]))
        val planning = Files.readString(root.resolve(required[1]))
        val handoff = Files.readString(root.resolve(required[2]))
        val roadmap = Files.readString(root.resolve(required[3]))
        if (listOf(rfc, planning, handoff, roadmap).any { "RFC-0049" !in it }) return false
        return if (implementationComplete) {
            "Implementation is not yet started" !in rfc &&
                "NOT_STARTED" !in planning &&
                Regex("(?i)implementation.*complete").containsMatchIn(handoff)
        } else {
            true
        }
    }
}

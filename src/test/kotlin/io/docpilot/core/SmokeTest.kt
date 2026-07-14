package io.docpilot.core

import io.docpilot.core.loader.LocalProjectLoader
import io.docpilot.core.scanner.LocalSourceScanner
import io.docpilot.core.summary.DefaultProjectSummaryBuilder
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class SmokeTest {
    @Test
    fun `pipeline works`() {
        val root = Files.createTempDirectory("docpilot-smoke")
        root.resolve("settings.gradle.kts").writeText("")
        root.resolve("Main.kt").writeText("class Main")

        val summary = DefaultProjectSummaryBuilder().build(
            LocalSourceScanner().scan(
                LocalProjectLoader().load(root),
            ),
        )

        assertEquals(0, summary.totalDirectories)
        assertEquals(2, summary.totalFiles)
    }
}

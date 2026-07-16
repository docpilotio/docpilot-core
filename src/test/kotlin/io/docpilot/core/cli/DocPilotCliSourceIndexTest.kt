package io.docpilot.core.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocPilotCliSourceIndexTest {

    @Test
    fun `analyze generates project summary and source index`() {
        val project =
            Files.createTempDirectory("docpilot-cli-rfc0010")

        project.resolve("settings.gradle.kts")
            .writeText("")

        project.resolve(
            "app/src/main/kotlin/example/Tasks.kt",
        )
            .also { it.parent.createDirectories() }
            .writeText(
                """
                package example.tasks

                import kotlinx.coroutines.flow.Flow

                internal class TasksViewModel
                fun loadTasks()
                """.trimIndent(),
            )

        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        val exitCode = runCli(
            args = arrayOf(
                "analyze",
                project.toString(),
            ),
            out = PrintStream(
                stdout,
                true,
                StandardCharsets.UTF_8,
            ),
            err = PrintStream(
                stderr,
                true,
                StandardCharsets.UTF_8,
            ),
        )

        val projectSummary =
            project.resolve("docs/project-summary.md")
        val sourceIndex =
            project.resolve("docs/source-index.md")

        assertEquals(0, exitCode)
        assertTrue(projectSummary.exists())
        assertTrue(sourceIndex.exists())
        assertTrue(
            projectSummary.readText()
                .contains("# Project Summary"),
        )
        assertTrue(
            sourceIndex.readText()
                .contains("# Source Index"),
        )
        assertTrue(
            sourceIndex.readText()
                .contains("`TasksViewModel`"),
        )
        assertTrue(
            stdout.toString(StandardCharsets.UTF_8)
                .contains("project-summary.md"),
        )
        assertTrue(
            stdout.toString(StandardCharsets.UTF_8)
                .contains("source-index.md"),
        )
        assertEquals(
            "",
            stderr.toString(StandardCharsets.UTF_8),
        )
    }

    @Test
    fun `invalid arguments return usage error`() {
        val stderr = ByteArrayOutputStream()

        val exitCode = runCli(
            args = arrayOf("analyze"),
            out = PrintStream(
                ByteArrayOutputStream(),
            ),
            err = PrintStream(
                stderr,
                true,
                StandardCharsets.UTF_8,
            ),
        )

        assertEquals(2, exitCode)
        assertTrue(
            stderr.toString(StandardCharsets.UTF_8)
                .contains(
                    "Usage: docpilot analyze <project-path>",
                ),
        )
    }
}

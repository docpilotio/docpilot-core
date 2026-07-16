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

class DocPilotCliKnowledgeGraphTest {

    @Test
    fun `analyze generates all three artifacts`() {
        val project =
            Files.createTempDirectory("docpilot-cli-rfc0011")

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
        val knowledgeGraph =
            project.resolve("docs/knowledge-graph.json")

        assertEquals(0, exitCode)
        assertTrue(projectSummary.exists())
        assertTrue(sourceIndex.exists())
        assertTrue(knowledgeGraph.exists())

        val graphContent = knowledgeGraph.readText()

        assertTrue(
            graphContent.contains(
                "\"schemaVersion\": \"0.1\"",
            ),
        )
        assertTrue(
            graphContent.contains(
                "\"name\": \"TasksViewModel\"",
            ),
        )
        assertTrue(
            graphContent.contains(
                "\"relationship\": \"DECLARES\"",
            ),
        )

        val output =
            stdout.toString(StandardCharsets.UTF_8)

        assertTrue(output.contains("project-summary.md"))
        assertTrue(output.contains("source-index.md"))
        assertTrue(output.contains("knowledge-graph.json"))
        assertEquals(
            "",
            stderr.toString(StandardCharsets.UTF_8),
        )
    }
}

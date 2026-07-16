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

class DocPilotCliPromptPackageTest {

    @Test
    fun `analyze generates prompt package artifacts`() {
        val project =
            Files.createTempDirectory("docpilot-cli-prompt")

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

        val overview =
            project.resolve(
                "prompt-package/overview.md",
            )
        val graph =
            project.resolve(
                "prompt-package/knowledge-graph.json",
            )
        val evidence =
            project.resolve(
                "prompt-package/evidence.json",
            )
        val instructions =
            project.resolve(
                "prompt-package/instructions.md",
            )

        assertEquals(0, exitCode)
        assertTrue(overview.exists())
        assertTrue(graph.exists())
        assertTrue(evidence.exists())
        assertTrue(instructions.exists())

        assertTrue(
            overview.readText()
                .contains("# DocPilot Prompt Package"),
        )
        assertTrue(
            graph.readText()
                .contains("\"schemaVersion\": \"0.1\""),
        )
        assertTrue(
            evidence.readText()
                .contains(
                    "\"type\": \"SYMBOL_DECLARATION\"",
                ),
        )
        assertTrue(
            instructions.readText()
                .contains("Do not invent missing relationships."),
        )

        val output = stdout
            .toString(StandardCharsets.UTF_8)
            .replace('\\', '/')

        assertTrue(
            output.contains(
                "prompt-package/overview.md",
            ),
        )
        assertTrue(
            output.contains(
                "prompt-package/evidence.json",
            ),
        )
        assertEquals(
            "",
            stderr.toString(StandardCharsets.UTF_8),
        )
    }
}

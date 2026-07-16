package io.docpilot.core.cli

import io.docpilot.core.api.DocPilotPlugin
import io.docpilot.core.model.RenderedArtifact
import io.docpilot.core.model.plugin.PluginCategory
import io.docpilot.core.model.plugin.PluginContext
import io.docpilot.core.model.plugin.PluginDescriptor
import io.docpilot.core.model.plugin.PluginId
import io.docpilot.core.model.plugin.PluginMessage
import io.docpilot.core.model.plugin.PluginMessageLevel
import io.docpilot.core.model.plugin.PluginResult
import io.docpilot.core.model.plugin.PluginStatus
import io.docpilot.core.plugin.DefaultPluginPipeline
import io.docpilot.core.plugin.DefaultPluginRuntime
import io.docpilot.core.plugin.InMemoryPluginRegistry
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocPilotCliAnalysisPluginTest {

    @Test
    fun `analyze executes analysis plugins before output stage`() {
        val project =
            Files.createTempDirectory("docpilot-analysis-plugin")

        project.resolve("settings.gradle.kts")
            .writeText("")

        val executionOrder = mutableListOf<String>()

        val analysisPlugin = plugin(
            id = "docpilot.analysis.test",
            category = PluginCategory.ANALYSIS,
        ) { context ->
            executionOrder += "analysis"
            assertTrue(context.sourceIndex != null)
            assertTrue(context.knowledge == null)
            assertTrue(context.promptPackage == null)

            PluginResult(
                status = PluginStatus.SUCCESS,
                artifacts = listOf(
                    RenderedArtifact(
                        relativePath =
                            "plugin-analysis/result.md",
                        mediaType = "text/markdown",
                        content = "# Analysis Plugin\n",
                    ),
                ),
                messages = listOf(
                    PluginMessage(
                        level = PluginMessageLevel.INFO,
                        text = "Analysis completed.",
                    ),
                ),
            )
        }

        val outputPlugin = plugin(
            id = "docpilot.output.test",
            category = PluginCategory.OUTPUT,
        ) { context ->
            executionOrder += "output"
            assertTrue(context.knowledge != null)
            assertTrue(context.promptPackage != null)

            PluginResult(
                status = PluginStatus.SUCCESS,
            )
        }

        val registry = InMemoryPluginRegistry(
            listOf(outputPlugin, analysisPlugin),
        )
        val runtime = DefaultPluginRuntime(
            registry = registry,
            pipeline = DefaultPluginPipeline(registry),
        )
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        val exitCode = runAnalyzeCommand(
            projectArgument = project.toString(),
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
            pluginRuntime = runtime,
        )

        val artifact =
            project.resolve("plugin-analysis/result.md")

        assertEquals(0, exitCode)
        assertEquals(
            listOf("analysis", "output"),
            executionOrder,
        )
        assertTrue(artifact.exists())
        assertTrue(
            artifact.readText()
                .contains("# Analysis Plugin"),
        )
        assertTrue(
            stdout.toString(StandardCharsets.UTF_8)
                .contains("Analysis completed."),
        )
        assertEquals(
            "",
            stderr.toString(StandardCharsets.UTF_8),
        )
    }

    @Test
    fun `failed analysis plugin stops later pipeline stages`() {
        val project =
            Files.createTempDirectory(
                "docpilot-analysis-plugin-failure",
            )

        project.resolve("settings.gradle.kts")
            .writeText("")

        var outputExecuted = false

        val analysisPlugin = plugin(
            id = "docpilot.analysis.failed",
            category = PluginCategory.ANALYSIS,
        ) {
            PluginResult(
                status = PluginStatus.FAILED,
                messages = listOf(
                    PluginMessage(
                        level = PluginMessageLevel.ERROR,
                        text = "Analysis plugin failed.",
                    ),
                ),
            )
        }

        val outputPlugin = plugin(
            id = "docpilot.output.should-not-run",
            category = PluginCategory.OUTPUT,
        ) {
            outputExecuted = true
            PluginResult(
                status = PluginStatus.SUCCESS,
            )
        }

        val registry = InMemoryPluginRegistry(
            listOf(analysisPlugin, outputPlugin),
        )
        val runtime = DefaultPluginRuntime(
            registry = registry,
            pipeline = DefaultPluginPipeline(registry),
        )

        val exitCode = runAnalyzeCommand(
            projectArgument = project.toString(),
            out = PrintStream(ByteArrayOutputStream()),
            err = PrintStream(ByteArrayOutputStream()),
            pluginRuntime = runtime,
        )

        assertEquals(1, exitCode)
        assertTrue(!outputExecuted)
    }

    private fun plugin(
        id: String,
        category: PluginCategory,
        execution: (PluginContext) -> PluginResult,
    ): DocPilotPlugin =
        object : DocPilotPlugin {
            override val descriptor =
                PluginDescriptor(
                    id = PluginId(id),
                    displayName = id,
                    category = category,
                    version = "0.1.0",
                    description = "Test plugin.",
                )

            override fun execute(
                context: PluginContext,
            ): PluginResult =
                execution(context)
        }
}

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

class DocPilotCliOutputPluginTest {

    @Test
    fun `analyze executes output plugins and writes artifacts`() {
        val project =
            Files.createTempDirectory("docpilot-output-plugin")

        project.resolve("settings.gradle.kts")
            .writeText("")

        val plugin = object : DocPilotPlugin {
            override val descriptor =
                PluginDescriptor(
                    id = PluginId("docpilot.output.test"),
                    displayName = "Test Output",
                    category = PluginCategory.OUTPUT,
                    version = "0.1.0",
                    description = "CLI integration test.",
                )

            override fun execute(
                context: PluginContext,
            ): PluginResult {
                assertTrue(context.sourceIndex != null)
                assertTrue(context.knowledge != null)
                assertTrue(context.promptPackage != null)

                return PluginResult(
                    status = PluginStatus.SUCCESS,
                    artifacts = listOf(
                        RenderedArtifact(
                            relativePath =
                                "plugin-output/result.md",
                            mediaType = "text/markdown",
                            content = "# Plugin Result\n",
                        ),
                    ),
                    messages = listOf(
                        PluginMessage(
                            level = PluginMessageLevel.INFO,
                            text = "Output generated.",
                        ),
                    ),
                )
            }
        }

        val registry =
            InMemoryPluginRegistry(listOf(plugin))
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

        val pluginArtifact =
            project.resolve("plugin-output/result.md")

        assertEquals(0, exitCode)
        assertTrue(pluginArtifact.exists())
        assertTrue(
            pluginArtifact.readText()
                .contains("# Plugin Result"),
        )
        assertTrue(
            stdout.toString(StandardCharsets.UTF_8)
                .contains(
                    "Plugin: docpilot.output.test [SUCCESS]",
                ),
        )
        assertTrue(
            stdout.toString(StandardCharsets.UTF_8)
                .contains("Output generated."),
        )
        assertEquals(
            "",
            stderr.toString(StandardCharsets.UTF_8),
        )
    }

    @Test
    fun `failed output plugin returns nonzero exit code`() {
        val project =
            Files.createTempDirectory("docpilot-failed-plugin")

        project.resolve("settings.gradle.kts")
            .writeText("")

        val plugin = object : DocPilotPlugin {
            override val descriptor =
                PluginDescriptor(
                    id = PluginId("docpilot.output.failed"),
                    displayName = "Failed Output",
                    category = PluginCategory.OUTPUT,
                    version = "0.1.0",
                    description = "Failed plugin test.",
                )

            override fun execute(
                context: PluginContext,
            ): PluginResult =
                PluginResult(
                    status = PluginStatus.FAILED,
                    messages = listOf(
                        PluginMessage(
                            level = PluginMessageLevel.ERROR,
                            text = "Output failed.",
                        ),
                    ),
                )
        }

        val registry =
            InMemoryPluginRegistry(listOf(plugin))
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
    }
}

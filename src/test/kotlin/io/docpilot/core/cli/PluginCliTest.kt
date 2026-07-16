package io.docpilot.core.cli

import io.docpilot.core.api.DocPilotPlugin
import io.docpilot.core.model.plugin.PluginCategory
import io.docpilot.core.model.plugin.PluginContext
import io.docpilot.core.model.plugin.PluginDescriptor
import io.docpilot.core.model.plugin.PluginId
import io.docpilot.core.model.plugin.PluginResult
import io.docpilot.core.model.plugin.PluginStatus
import io.docpilot.core.plugin.DefaultPluginRuntime
import io.docpilot.core.plugin.DefaultPluginPipeline
import io.docpilot.core.plugin.InMemoryPluginRegistry
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginCliTest {

    @Test
    fun `prints message when no plugins are discovered`() {
        val output = ByteArrayOutputStream()
        val registry = InMemoryPluginRegistry(emptyList())

        val exitCode = runPluginsCommand(
            out = PrintStream(
                output,
                true,
                StandardCharsets.UTF_8,
            ),
            runtime = DefaultPluginRuntime(
                registry = registry,
                pipeline = DefaultPluginPipeline(registry),
            ),
        )

        assertEquals(0, exitCode)
        assertTrue(
            output.toString(StandardCharsets.UTF_8)
                .contains("No plugins discovered."),
        )
    }

    @Test
    fun `lists discovered plugins in registry order`() {
        val registry = InMemoryPluginRegistry(
            listOf(
                plugin(
                    id = "docpilot.output.zeta",
                    category = PluginCategory.OUTPUT,
                ),
                plugin(
                    id = "docpilot.analysis.alpha",
                    category = PluginCategory.ANALYSIS,
                ),
            ),
        )
        val output = ByteArrayOutputStream()

        val exitCode = runPluginsCommand(
            out = PrintStream(
                output,
                true,
                StandardCharsets.UTF_8,
            ),
            runtime = DefaultPluginRuntime(
                registry = registry,
                pipeline = DefaultPluginPipeline(registry),
            ),
        )

        val text =
            output.toString(StandardCharsets.UTF_8)

        assertEquals(0, exitCode)
        assertTrue(
            text.indexOf("docpilot.analysis.alpha") <
                text.indexOf("docpilot.output.zeta"),
        )
        assertTrue(text.contains("ANALYSIS"))
        assertTrue(text.contains("OUTPUT"))
    }

    @Test
    fun `runCli supports plugins command`() {
        val output = ByteArrayOutputStream()

        val exitCode = runCli(
            args = arrayOf("plugins"),
            out = PrintStream(output),
            err = PrintStream(ByteArrayOutputStream()),
        )

        assertEquals(0, exitCode)
    }

    private fun plugin(
        id: String,
        category: PluginCategory,
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
                PluginResult(
                    status = PluginStatus.SUCCESS,
                )
        }
}

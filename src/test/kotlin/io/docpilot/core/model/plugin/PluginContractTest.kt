package io.docpilot.core.model.plugin

import io.docpilot.core.api.DocPilotPlugin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginContractTest {

    @Test
    fun `executes a valid output plugin`() {
        val plugin = object : DocPilotPlugin {
            override val descriptor =
                PluginDescriptor(
                    id = PluginId("docpilot.output.sample"),
                    displayName = "Sample Output",
                    category = PluginCategory.OUTPUT,
                    version = "0.1.0",
                    description = "Test output plugin.",
                )

            override fun execute(
                context: PluginContext,
            ): PluginResult =
                PluginResult(
                    status = PluginStatus.SUCCESS,
                    messages = listOf(
                        PluginMessage(
                            level = PluginMessageLevel.INFO,
                            text = "Completed.",
                        ),
                    ),
                )
        }

        val result = plugin.execute(PluginContext())

        assertEquals(
            PluginCategory.OUTPUT,
            plugin.descriptor.category,
        )
        assertTrue(result.isSuccess)
    }

    @Test
    fun `rejects invalid plugin IDs and versions`() {
        assertFailsWith<IllegalArgumentException> {
            PluginId("Invalid Plugin")
        }

        assertFailsWith<IllegalArgumentException> {
            PluginDescriptor(
                id = PluginId("docpilot.sample"),
                displayName = "Sample",
                category = PluginCategory.ANALYSIS,
                version = "1.0",
                description = "Invalid version.",
            )
        }
    }

    @Test
    fun `failed result requires an error message`() {
        assertFailsWith<IllegalArgumentException> {
            PluginResult(
                status = PluginStatus.FAILED,
                messages = listOf(
                    PluginMessage(
                        level = PluginMessageLevel.WARNING,
                        text = "Only a warning.",
                    ),
                ),
            )
        }

        val result = PluginResult(
            status = PluginStatus.FAILED,
            messages = listOf(
                PluginMessage(
                    level = PluginMessageLevel.ERROR,
                    text = "Execution failed.",
                ),
            ),
        )

        assertFalse(result.isSuccess)
    }

    @Test
    fun `rejects duplicate artifact paths`() {
        val artifact =
            io.docpilot.core.model.RenderedArtifact(
                relativePath = "output/result.md",
                mediaType = "text/markdown",
                content = "result",
            )

        assertFailsWith<IllegalArgumentException> {
            PluginResult(
                status = PluginStatus.SUCCESS,
                artifacts = listOf(
                    artifact,
                    artifact.copy(content = "other"),
                ),
            )
        }
    }
}

package io.docpilot.core.plugin

import io.docpilot.core.api.DocPilotPlugin
import io.docpilot.core.model.plugin.PluginCategory
import io.docpilot.core.model.plugin.PluginContext
import io.docpilot.core.model.plugin.PluginDescriptor
import io.docpilot.core.model.plugin.PluginId
import io.docpilot.core.model.plugin.PluginMessage
import io.docpilot.core.model.plugin.PluginMessageLevel
import io.docpilot.core.model.plugin.PluginResult
import io.docpilot.core.model.plugin.PluginStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultPluginExecutorTest {

    @Test
    fun `executes registered plugin`() {
        val plugin = plugin(
            id = "docpilot.output.success",
        ) {
            PluginResult(
                status = PluginStatus.SUCCESS,
                messages = listOf(
                    PluginMessage(
                        level = PluginMessageLevel.INFO,
                        text = "Executed.",
                    ),
                ),
            )
        }

        val executor = DefaultPluginExecutor(
            InMemoryPluginRegistry(
                plugins = listOf(plugin),
            ),
        )

        val result = executor.execute(
            pluginId = plugin.descriptor.id,
            context = PluginContext(),
        )

        assertTrue(result.isSuccess)
        assertEquals(
            "Executed.",
            result.messages.single().text,
        )
    }

    @Test
    fun `returns failed result when plugin is missing`() {
        val executor = DefaultPluginExecutor(
            InMemoryPluginRegistry(
                plugins = emptyList(),
            ),
        )

        val result = executor.execute(
            pluginId = PluginId("docpilot.missing"),
            context = PluginContext(),
        )

        assertFalse(result.isSuccess)
        assertEquals(
            PluginStatus.FAILED,
            result.status,
        )
        assertTrue(
            result.messages.single().text
                .contains("Plugin not found"),
        )
    }

    @Test
    fun `converts plugin exception into failed result`() {
        val plugin = plugin(
            id = "docpilot.output.throwing",
        ) {
            error("Simulated failure")
        }

        val executor = DefaultPluginExecutor(
            InMemoryPluginRegistry(
                plugins = listOf(plugin),
            ),
        )

        val result = executor.execute(
            pluginId = plugin.descriptor.id,
            context = PluginContext(),
        )

        assertEquals(
            PluginStatus.FAILED,
            result.status,
        )
        assertEquals(
            PluginMessageLevel.ERROR,
            result.messages.single().level,
        )
        assertTrue(
            result.messages.single().text
                .contains("Simulated failure"),
        )
    }

    private fun plugin(
        id: String,
        execution: (PluginContext) -> PluginResult,
    ): DocPilotPlugin =
        object : DocPilotPlugin {
            override val descriptor =
                PluginDescriptor(
                    id = PluginId(id),
                    displayName = id,
                    category = PluginCategory.OUTPUT,
                    version = "0.1.0",
                    description = "Test plugin.",
                )

            override fun execute(
                context: PluginContext,
            ): PluginResult =
                execution(context)
        }
}

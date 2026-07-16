package io.docpilot.core.plugin

import io.docpilot.core.api.DocPilotPlugin
import io.docpilot.core.model.plugin.PluginCategory
import io.docpilot.core.model.plugin.PluginContext
import io.docpilot.core.model.plugin.PluginDescriptor
import io.docpilot.core.model.plugin.PluginId
import io.docpilot.core.model.plugin.PluginResult
import io.docpilot.core.model.plugin.PluginStatus
import java.net.URLClassLoader
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceLoaderPluginLoaderTest {

    @Test
    fun `returns empty list when no service providers exist`() {
        URLClassLoader(
            emptyArray(),
            null,
        ).use { classLoader ->
            val plugins =
                ServiceLoaderPluginLoader(
                    classLoader,
                ).load()

            assertTrue(plugins.isEmpty())
        }
    }

    @Test
    fun `default runtime builds empty registry safely`() {
        val runtime = DefaultPluginRuntime.discover(
            loader = {
                emptyList()
            },
        )

        assertTrue(runtime.registry.all().isEmpty())
    }

    @Test
    fun `loader contract preserves deterministic order`() {
        val runtime = DefaultPluginRuntime.discover(
            loader = {
                listOf(
                    plugin("docpilot.output.zeta"),
                    plugin("docpilot.output.alpha"),
                )
            },
        )

        assertEquals(
            listOf(
                "docpilot.output.alpha",
                "docpilot.output.zeta",
            ),
            runtime.registry.all().map {
                it.descriptor.id.value
            },
        )
    }

    private fun plugin(
        id: String,
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
                PluginResult(
                    status = PluginStatus.SUCCESS,
                )
        }
}

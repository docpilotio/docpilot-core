package io.docpilot.core.plugin

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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultPluginPipelineTest {

    @Test
    fun `executes category plugins in deterministic order`() {
        val second = plugin(
            id = "docpilot.output.second",
            category = PluginCategory.OUTPUT,
        )
        val first = plugin(
            id = "docpilot.output.first",
            category = PluginCategory.OUTPUT,
        )
        val analysis = plugin(
            id = "docpilot.analysis.sample",
            category = PluginCategory.ANALYSIS,
        )

        val registry = InMemoryPluginRegistry(
            listOf(second, analysis, first),
        )

        val result = DefaultPluginPipeline(registry)
            .execute(
                category = PluginCategory.OUTPUT,
                context = PluginContext(),
            )

        assertEquals(
            listOf(
                "docpilot.output.first",
                "docpilot.output.second",
            ),
            result.executions.map {
                it.pluginId.value
            },
        )
        assertEquals(
            PluginStatus.SUCCESS,
            result.status,
        )
    }

    @Test
    fun `aggregates artifacts and messages`() {
        val plugin = object : DocPilotPlugin {
            override val descriptor =
                PluginDescriptor(
                    id = PluginId("docpilot.output.sample"),
                    displayName = "Sample",
                    category = PluginCategory.OUTPUT,
                    version = "0.1.0",
                    description = "Sample output plugin.",
                )

            override fun execute(
                context: PluginContext,
            ): PluginResult =
                PluginResult(
                    status = PluginStatus.PARTIAL,
                    artifacts = listOf(
                        RenderedArtifact(
                            relativePath = "plugin/result.md",
                            mediaType = "text/markdown",
                            content = "result",
                        ),
                    ),
                    messages = listOf(
                        PluginMessage(
                            level =
                                PluginMessageLevel.WARNING,
                            text = "Partial result.",
                        ),
                    ),
                )
        }

        val result = DefaultPluginPipeline(
            InMemoryPluginRegistry(listOf(plugin)),
        ).execute(
            category = PluginCategory.OUTPUT,
            context = PluginContext(),
        )

        assertEquals(
            PluginStatus.PARTIAL,
            result.status,
        )
        assertEquals(1, result.artifacts.size)
        assertEquals(1, result.messages.size)
    }

    @Test
    fun `empty category succeeds with no executions`() {
        val result = DefaultPluginPipeline(
            InMemoryPluginRegistry(emptyList()),
        ).execute(
            category = PluginCategory.ANALYSIS,
            context = PluginContext(),
        )

        assertEquals(
            PluginStatus.SUCCESS,
            result.status,
        )
        assertTrue(result.executions.isEmpty())
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

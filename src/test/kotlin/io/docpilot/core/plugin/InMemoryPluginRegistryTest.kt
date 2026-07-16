package io.docpilot.core.plugin

import io.docpilot.core.api.DocPilotPlugin
import io.docpilot.core.api.PluginValidationResult
import io.docpilot.core.api.PluginValidator
import io.docpilot.core.model.plugin.PluginCategory
import io.docpilot.core.model.plugin.PluginContext
import io.docpilot.core.model.plugin.PluginDescriptor
import io.docpilot.core.model.plugin.PluginId
import io.docpilot.core.model.plugin.PluginResult
import io.docpilot.core.model.plugin.PluginStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class InMemoryPluginRegistryTest {

    @Test
    fun `registers plugins in deterministic ID order`() {
        val output = plugin(
            id = "docpilot.output.sample",
            category = PluginCategory.OUTPUT,
        )
        val analysis = plugin(
            id = "docpilot.analysis.sample",
            category = PluginCategory.ANALYSIS,
        )

        val registry = InMemoryPluginRegistry(
            listOf(output, analysis),
        )

        assertEquals(
            listOf(
                "docpilot.analysis.sample",
                "docpilot.output.sample",
            ),
            registry.all().map {
                it.descriptor.id.value
            },
        )
        assertEquals(
            analysis,
            registry.find(
                PluginId("docpilot.analysis.sample"),
            ),
        )
        assertEquals(
            listOf(output),
            registry.byCategory(
                PluginCategory.OUTPUT,
            ),
        )
        assertNull(
            registry.find(
                PluginId("docpilot.missing"),
            ),
        )
    }

    @Test
    fun `rejects duplicate plugin IDs`() {
        val first = plugin(
            id = "docpilot.sample",
            category = PluginCategory.ANALYSIS,
        )
        val second = plugin(
            id = "docpilot.sample",
            category = PluginCategory.OUTPUT,
        )

        assertFailsWith<IllegalArgumentException> {
            InMemoryPluginRegistry(
                listOf(first, second),
            )
        }
    }

    @Test
    fun `rejects plugins failing validation`() {
        val rejectingValidator =
            PluginValidator {
                PluginValidationResult.Invalid(
                    listOf("Rejected for test."),
                )
            }

        assertFailsWith<IllegalArgumentException> {
            InMemoryPluginRegistry(
                plugins = listOf(
                    plugin(
                        id = "docpilot.invalid",
                        category =
                            PluginCategory.ANALYSIS,
                    ),
                ),
                validator = rejectingValidator,
            )
        }
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

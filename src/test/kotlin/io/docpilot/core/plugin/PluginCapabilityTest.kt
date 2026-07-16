package io.docpilot.core.plugin

import io.docpilot.core.api.DocPilotPlugin
import io.docpilot.core.model.plugin.PluginCapabilities
import io.docpilot.core.model.plugin.PluginCapability
import io.docpilot.core.model.plugin.PluginCapabilityId
import io.docpilot.core.model.plugin.PluginCategory
import io.docpilot.core.model.plugin.PluginContext
import io.docpilot.core.model.plugin.PluginDescriptor
import io.docpilot.core.model.plugin.PluginId
import io.docpilot.core.model.plugin.PluginInput
import io.docpilot.core.model.plugin.PluginResult
import io.docpilot.core.model.plugin.PluginStatus
import io.docpilot.core.model.source.SourceIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginCapabilityTest {

    @Test
    fun `supports custom string based capability IDs`() {
        val id = PluginCapabilityId(
            "example.capability.custom-report",
        )

        assertEquals(
            "example.capability.custom-report",
            id.value,
        )

        assertFailsWith<IllegalArgumentException> {
            PluginCapabilityId("Invalid Capability")
        }
    }

    @Test
    fun `plugin may expose multiple capabilities`() {
        val plugin = plugin(
            id = "docpilot.output.architecture",
            capabilities = setOf(
                PluginCapability(
                    id = PluginCapabilities.ARCHITECTURE_SPEC,
                    requiredInputs = setOf(
                        PluginInput.KNOWLEDGE,
                        PluginInput.PROMPT_PACKAGE,
                    ),
                ),
                PluginCapability(
                    id = PluginCapabilities.MARKDOWN,
                ),
            ),
        )

        assertEquals(
            2,
            plugin.descriptor.capabilities.size,
        )
    }

    @Test
    fun `registry finds plugins by capability`() {
        val architecture = plugin(
            id = "docpilot.output.architecture",
            capabilities = setOf(
                PluginCapability(
                    PluginCapabilities.ARCHITECTURE_SPEC,
                ),
            ),
        )
        val markdown = plugin(
            id = "docpilot.output.markdown",
            capabilities = setOf(
                PluginCapability(
                    PluginCapabilities.MARKDOWN,
                ),
            ),
        )

        val registry = InMemoryPluginRegistry(
            listOf(markdown, architecture),
        )

        assertEquals(
            listOf(architecture),
            registry.byCapability(
                PluginCapabilities.ARCHITECTURE_SPEC,
            ),
        )
    }

    @Test
    fun `matcher verifies required inputs`() {
        val capability = PluginCapability(
            id = PluginCapabilities.ARCHITECTURE_SPEC,
            requiredInputs = setOf(
                PluginInput.SOURCE_INDEX,
                PluginInput.KNOWLEDGE,
            ),
        )
        val matcher =
            DefaultPluginCapabilityMatcher()

        assertFalse(
            matcher.matches(
                capability = capability,
                context = PluginContext(
                    sourceIndex = SourceIndex(
                        files = emptyList(),
                    ),
                ),
            ),
        )

        assertTrue(
            matcher.matches(
                capability = PluginCapability(
                    id = PluginCapabilities.KNOWLEDGE_EXPORT,
                    requiredInputs = setOf(
                        PluginInput.SOURCE_INDEX,
                    ),
                ),
                context = PluginContext(
                    sourceIndex = SourceIndex(
                        files = emptyList(),
                    ),
                ),
            ),
        )
    }

    private fun plugin(
        id: String,
        capabilities: Set<PluginCapability>,
    ): DocPilotPlugin =
        object : DocPilotPlugin {
            override val descriptor =
                PluginDescriptor(
                    id = PluginId(id),
                    displayName = id,
                    category = PluginCategory.OUTPUT,
                    version = "0.1.0",
                    description = "Capability test plugin.",
                    capabilities = capabilities,
                )

            override fun execute(
                context: PluginContext,
            ): PluginResult =
                PluginResult(
                    status = PluginStatus.SUCCESS,
                )
        }
}

package io.docpilot.core.plugin

import io.docpilot.core.api.DocPilotPlugin
import io.docpilot.core.model.plugin.PluginCapabilities
import io.docpilot.core.model.plugin.PluginCapability
import io.docpilot.core.model.plugin.PluginCategory
import io.docpilot.core.model.plugin.PluginContext
import io.docpilot.core.model.plugin.PluginDescriptor
import io.docpilot.core.model.plugin.PluginId
import io.docpilot.core.model.plugin.PluginInput
import io.docpilot.core.model.plugin.PluginResolutionResult
import io.docpilot.core.model.plugin.PluginResult
import io.docpilot.core.model.plugin.PluginStatus
import io.docpilot.core.model.source.SourceIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultPluginCapabilityResolverTest {

    @Test
    fun `resolves matching capability providers in plugin ID order`() {
        val second = plugin(
            id = "docpilot.output.zeta",
            capability = PluginCapability(
                id = PluginCapabilities.ARCHITECTURE_SPEC,
                requiredInputs = setOf(
                    PluginInput.SOURCE_INDEX,
                ),
            ),
        )
        val first = plugin(
            id = "docpilot.output.alpha",
            capability = PluginCapability(
                id = PluginCapabilities.ARCHITECTURE_SPEC,
                requiredInputs = setOf(
                    PluginInput.SOURCE_INDEX,
                ),
            ),
        )

        val resolver = DefaultPluginCapabilityResolver(
            registry = InMemoryPluginRegistry(
                listOf(second, first),
            ),
        )

        val result = resolver.resolve(
            capabilityId =
                PluginCapabilities.ARCHITECTURE_SPEC,
            context = PluginContext(
                sourceIndex = SourceIndex(
                    files = emptyList(),
                ),
            ),
        )

        val resolved =
            assertIs<PluginResolutionResult.Resolved>(result)

        assertEquals(
            listOf(
                "docpilot.output.alpha",
                "docpilot.output.zeta",
            ),
            resolved.plugins.map {
                it.descriptor.id.value
            },
        )
    }

    @Test
    fun `returns unavailable when capability has no providers`() {
        val resolver = DefaultPluginCapabilityResolver(
            registry = InMemoryPluginRegistry(
                emptyList(),
            ),
        )

        val result = resolver.resolve(
            capabilityId =
                PluginCapabilities.CLASS_DIAGRAM,
            context = PluginContext(),
        )

        val unavailable =
            assertIs<PluginResolutionResult.Unavailable>(
                result,
            )

        assertTrue(
            unavailable.reasons.single()
                .contains("No plugin provides capability"),
        )
    }

    @Test
    fun `filters providers whose required inputs are missing`() {
        val provider = plugin(
            id = "docpilot.output.architecture",
            capability = PluginCapability(
                id = PluginCapabilities.ARCHITECTURE_SPEC,
                requiredInputs = setOf(
                    PluginInput.KNOWLEDGE,
                    PluginInput.PROMPT_PACKAGE,
                ),
            ),
        )

        val resolver = DefaultPluginCapabilityResolver(
            registry = InMemoryPluginRegistry(
                listOf(provider),
            ),
        )

        val result = resolver.resolve(
            capabilityId =
                PluginCapabilities.ARCHITECTURE_SPEC,
            context = PluginContext(
                sourceIndex = SourceIndex(
                    files = emptyList(),
                ),
            ),
        )

        val unavailable =
            assertIs<PluginResolutionResult.Unavailable>(
                result,
            )

        assertTrue(
            unavailable.reasons.single()
                .contains("KNOWLEDGE"),
        )
        assertTrue(
            unavailable.reasons.single()
                .contains("PROMPT_PACKAGE"),
        )
    }

    private fun plugin(
        id: String,
        capability: PluginCapability,
    ): DocPilotPlugin =
        object : DocPilotPlugin {
            override val descriptor =
                PluginDescriptor(
                    id = PluginId(id),
                    displayName = id,
                    category = PluginCategory.OUTPUT,
                    version = "0.1.0",
                    description = "Resolver test plugin.",
                    capabilities = setOf(capability),
                )

            override fun execute(
                context: PluginContext,
            ): PluginResult =
                PluginResult(
                    status = PluginStatus.SUCCESS,
                )
        }
}

package io.docpilot.core.plugin

import io.docpilot.core.api.DocPilotPlugin
import io.docpilot.core.model.plugin.PluginCategory
import io.docpilot.core.model.plugin.PluginContext
import io.docpilot.core.model.plugin.PluginDescriptor
import io.docpilot.core.model.plugin.PluginId
import io.docpilot.core.model.plugin.PluginResolutionResult
import io.docpilot.core.model.plugin.PluginResult
import io.docpilot.core.model.plugin.PluginStatus
import io.docpilot.core.model.selection.SelectionContext
import io.docpilot.core.model.selection.SelectionReason
import io.docpilot.core.model.selection.SelectionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DefaultPluginSelectorTest {

    @Test
    fun `selects explicitly requested plugin`() {
        val ollama = plugin("docpilot.ai.ollama")
        val openAi = plugin("docpilot.ai.openai")

        val result = DefaultPluginSelector().select(
            resolution = PluginResolutionResult.Resolved(
                plugins = listOf(ollama, openAi),
            ),
            context = SelectionContext(
                explicitCandidateId =
                    "docpilot.ai.openai",
                priorities = mapOf(
                    "docpilot.ai.ollama" to 100,
                    "docpilot.ai.openai" to 90,
                ),
            ),
        )

        val selected =
            assertIs<
                SelectionResult.Selected<DocPilotPlugin>
            >(result)

        assertEquals(
            openAi,
            selected.candidate,
        )
        assertEquals(
            SelectionReason.EXPLICIT,
            selected.reason,
        )
    }

    @Test
    fun `preserves resolver unavailable reasons`() {
        val result = DefaultPluginSelector().select(
            resolution =
                PluginResolutionResult.Unavailable(
                    capabilityId =
                        io.docpilot.core.model.plugin
                            .PluginCapabilities
                            .ARCHITECTURE_SPEC,
                    reasons = listOf(
                        "No matching capability provider.",
                    ),
                ),
        )

        val unavailable =
            assertIs<SelectionResult.Unavailable>(result)

        assertEquals(
            listOf("No matching capability provider."),
            unavailable.reasons,
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
                    description = "Selector test plugin.",
                )

            override fun execute(
                context: PluginContext,
            ): PluginResult =
                PluginResult(
                    status = PluginStatus.SUCCESS,
                )
        }
}

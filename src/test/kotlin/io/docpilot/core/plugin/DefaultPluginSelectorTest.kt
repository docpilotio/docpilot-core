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
import io.docpilot.core.model.selection.SelectionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DefaultPluginSelectorTest {

    @Test
    fun `selects explicit plugin with PluginId`() {
        val ollama = plugin("docpilot.ai.ollama")
        val openAi = plugin("docpilot.ai.openai")

        val result = DefaultPluginSelector().select(
            resolution = PluginResolutionResult.Resolved(
                listOf(ollama, openAi),
            ),
            context = SelectionContext(
                explicitCandidateId =
                    PluginId("docpilot.ai.openai"),
            ),
        )

        val selected =
            assertIs<
                SelectionResult.Selected<DocPilotPlugin, PluginId>
            >(result)

        assertEquals(openAi, selected.candidate)
        assertEquals(
            PluginId("docpilot.ai.openai"),
            selected.candidateId,
        )
    }

    private fun plugin(id: String): DocPilotPlugin =
        object : DocPilotPlugin {
            override val descriptor = PluginDescriptor(
                id = PluginId(id),
                displayName = id,
                category = PluginCategory.OUTPUT,
                version = "0.1.0",
                description = "Test plugin.",
            )

            override fun execute(
                context: PluginContext,
            ) = PluginResult(
                status = PluginStatus.SUCCESS,
            )
        }
}

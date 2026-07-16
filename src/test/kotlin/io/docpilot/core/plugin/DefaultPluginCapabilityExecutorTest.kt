package io.docpilot.core.plugin

import io.docpilot.core.api.DocPilotPlugin
import io.docpilot.core.model.plugin.PluginCapabilities
import io.docpilot.core.model.plugin.PluginCapability
import io.docpilot.core.model.plugin.PluginCategory
import io.docpilot.core.model.plugin.PluginContext
import io.docpilot.core.model.plugin.PluginDescriptor
import io.docpilot.core.model.plugin.PluginId
import io.docpilot.core.model.plugin.PluginInput
import io.docpilot.core.model.plugin.PluginMessage
import io.docpilot.core.model.plugin.PluginMessageLevel
import io.docpilot.core.model.plugin.PluginResult
import io.docpilot.core.model.plugin.PluginStatus
import io.docpilot.core.model.selection.SelectionContext
import io.docpilot.core.model.source.SourceIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultPluginCapabilityExecutorTest {

    @Test
    fun `resolves selects and executes explicit plugin`() {
        val ollama = plugin(
            id = "docpilot.ai.ollama",
            message = "ollama",
        )
        val openAi = plugin(
            id = "docpilot.ai.openai",
            message = "openai",
        )

        val registry = InMemoryPluginRegistry(
            plugins = listOf(ollama, openAi),
        )
        val capabilityExecutor =
            DefaultPluginCapabilityExecutor.create(
                registry = registry,
            )

        val result = capabilityExecutor.execute(
            capabilityId =
                PluginCapabilities.ARCHITECTURE_SPEC,
            pluginContext = PluginContext(
                sourceIndex = SourceIndex(
                    files = emptyList(),
                ),
            ),
            selectionContext = SelectionContext(
                explicitCandidateId =
                    PluginId("docpilot.ai.openai"),
            ),
        )

        assertEquals(
            PluginStatus.SUCCESS,
            result.status,
        )
        assertEquals(
            "openai",
            result.messages.single().text,
        )
    }

    @Test
    fun `returns failed result when capability is unavailable`() {
        val registry = InMemoryPluginRegistry(
            plugins = emptyList(),
        )

        val result =
            DefaultPluginCapabilityExecutor.create(
                registry = registry,
            ).execute(
                capabilityId =
                    PluginCapabilities.ARCHITECTURE_SPEC,
                pluginContext = PluginContext(),
            )

        assertEquals(
            PluginStatus.FAILED,
            result.status,
        )
        assertTrue(
            result.messages.single().text
                .contains("No plugin provides capability"),
        )
    }

    @Test
    fun `returns failed result when required inputs are missing`() {
        val provider = plugin(
            id = "docpilot.ai.ollama",
            message = "ollama",
            requiredInputs = setOf(
                PluginInput.KNOWLEDGE,
            ),
        )

        val registry = InMemoryPluginRegistry(
            plugins = listOf(provider),
        )

        val result =
            DefaultPluginCapabilityExecutor.create(
                registry = registry,
            ).execute(
                capabilityId =
                    PluginCapabilities.ARCHITECTURE_SPEC,
                pluginContext = PluginContext(),
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
                .contains("KNOWLEDGE"),
        )
    }

    private fun plugin(
        id: String,
        message: String,
        requiredInputs: Set<PluginInput> =
            setOf(PluginInput.SOURCE_INDEX),
    ): DocPilotPlugin =
        object : DocPilotPlugin {
            override val descriptor =
                PluginDescriptor(
                    id = PluginId(id),
                    displayName = id,
                    category = PluginCategory.OUTPUT,
                    version = "0.1.0",
                    description =
                        "Capability executor test plugin.",
                    capabilities = setOf(
                        PluginCapability(
                            id =
                                PluginCapabilities
                                    .ARCHITECTURE_SPEC,
                            requiredInputs =
                                requiredInputs,
                        ),
                    ),
                )

            override fun execute(
                context: PluginContext,
            ): PluginResult =
                PluginResult(
                    status = PluginStatus.SUCCESS,
                    messages = listOf(
                        PluginMessage(
                            level =
                                PluginMessageLevel.INFO,
                            text = message,
                        ),
                    ),
                )
        }
}

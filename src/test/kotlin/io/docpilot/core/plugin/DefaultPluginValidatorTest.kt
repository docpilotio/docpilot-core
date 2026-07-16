package io.docpilot.core.plugin

import io.docpilot.core.api.DocPilotPlugin
import io.docpilot.core.api.PluginValidationResult
import io.docpilot.core.model.plugin.PluginCategory
import io.docpilot.core.model.plugin.PluginContext
import io.docpilot.core.model.plugin.PluginDescriptor
import io.docpilot.core.model.plugin.PluginId
import io.docpilot.core.model.plugin.PluginResult
import io.docpilot.core.model.plugin.PluginStatus
import kotlin.test.Test
import kotlin.test.assertIs

class DefaultPluginValidatorTest {

    @Test
    fun `accepts valid plugin`() {
        val plugin = object : DocPilotPlugin {
            override val descriptor =
                PluginDescriptor(
                    id = PluginId("docpilot.valid"),
                    displayName = "Valid",
                    category = PluginCategory.ANALYSIS,
                    version = "0.1.0",
                    description = "Valid plugin.",
                )

            override fun execute(
                context: PluginContext,
            ): PluginResult =
                PluginResult(
                    status = PluginStatus.SUCCESS,
                )
        }

        val result =
            DefaultPluginValidator().validate(plugin)

        assertIs<PluginValidationResult.Valid>(result)
    }
}

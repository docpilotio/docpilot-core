package io.docpilot.core.ai

import io.docpilot.core.api.AiProviderPlugin
import io.docpilot.core.api.PluginRegistry

object PluginBackedAiProviderRegistry {
    fun create(
        pluginRegistry: PluginRegistry,
    ): InMemoryAiProviderRegistry =
        InMemoryAiProviderRegistry(
            providers = pluginRegistry.all()
                .filterIsInstance<AiProviderPlugin>()
                .map { it.provider },
        )
}

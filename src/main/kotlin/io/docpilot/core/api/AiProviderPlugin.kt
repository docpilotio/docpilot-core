package io.docpilot.core.api

/**
 * Plugin bridge exposing one AI provider to the DocPilot plugin platform.
 */
interface AiProviderPlugin : DocPilotPlugin {
    val provider: AiProvider
}

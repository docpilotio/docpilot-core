package io.docpilot.core.api

/**
 * Discovers plugins available to the current DocPilot runtime.
 */
fun interface PluginLoader {
    fun load(): List<DocPilotPlugin>
}

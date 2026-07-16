package io.docpilot.core.model.prompt

import io.docpilot.core.model.RenderedArtifact

/**
 * Deterministic package of artifacts prepared for a future AI provider.
 */
data class PromptPackage(
    val artifacts: List<RenderedArtifact>,
) {
    init {
        require(
            artifacts.map { it.relativePath }
                .distinct()
                .size == artifacts.size,
        ) {
            "Prompt package artifact paths must be unique."
        }
    }

    val artifactCount: Int
        get() = artifacts.size

    fun artifact(relativePath: String): RenderedArtifact? =
        artifacts.firstOrNull {
            it.relativePath == relativePath
        }
}

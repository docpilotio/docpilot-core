package io.docpilot.core.model

data class RenderedArtifact(
    val relativePath: String,
    val mediaType: String,
    val content: String,
)

package io.docpilot.core.model

/**
 * Renderer output that can be written to a file by a CLI or IDE integration.
 */
public data class RenderedArtifact(
    public val relativePath: String,
    public val mediaType: String,
    public val content: String,
) {
    init {
        require(relativePath.isNotBlank()) {
            "relativePath must not be blank."
        }
        require(mediaType.isNotBlank()) {
            "mediaType must not be blank."
        }
    }
}

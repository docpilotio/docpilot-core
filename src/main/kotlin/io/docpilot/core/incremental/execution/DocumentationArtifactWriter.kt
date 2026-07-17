package io.docpilot.core.incremental.execution

import io.docpilot.core.model.RenderedArtifact

/** Output port used by the application execution layer. */
public interface DocumentationArtifactWriter {
    public fun write(artifact: RenderedArtifact)

    public fun delete(relativePath: String)
}

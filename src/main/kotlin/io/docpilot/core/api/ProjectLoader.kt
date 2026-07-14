package io.docpilot.core.api

import io.docpilot.core.model.ProjectRoot
import java.nio.file.Path

/**
 * Loads and validates a software project root.
 */
public fun interface ProjectLoader {
    public fun load(path: Path): ProjectRoot
}

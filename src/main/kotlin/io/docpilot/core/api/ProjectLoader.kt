package io.docpilot.core.api

import io.docpilot.core.model.ProjectRoot
import java.nio.file.Path

fun interface ProjectLoader {
    fun load(path: Path): ProjectRoot
}

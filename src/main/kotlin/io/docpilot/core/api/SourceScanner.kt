package io.docpilot.core.api

import io.docpilot.core.model.ProjectInventory
import io.docpilot.core.model.ProjectRoot

fun interface SourceScanner {
    fun scan(project: ProjectRoot): ProjectInventory
}

package io.docpilot.core.api

import io.docpilot.core.model.ProjectInventory
import io.docpilot.core.model.source.SourceIndex

fun interface ProjectSourceIndexer {
    fun index(inventory: ProjectInventory): SourceIndex
}

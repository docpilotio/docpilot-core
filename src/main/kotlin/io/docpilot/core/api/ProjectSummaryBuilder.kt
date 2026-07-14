package io.docpilot.core.api

import io.docpilot.core.model.ProjectInventory
import io.docpilot.core.model.ProjectSummary

fun interface ProjectSummaryBuilder {
    fun build(inventory: ProjectInventory): ProjectSummary
}

package io.docpilot.core.api

import io.docpilot.core.model.ProjectInventory
import io.docpilot.core.model.ProjectSummary

/**
 * Builds a deterministic, evidence-based summary from a project inventory.
 */
public fun interface ProjectSummaryBuilder {
    public fun build(inventory: ProjectInventory): ProjectSummary
}

package io.docpilot.core.api

import io.docpilot.core.model.ProjectInventory
import io.docpilot.core.model.ProjectRoot

/**
 * Inventories the files and directories contained in a validated project root.
 */
public fun interface SourceScanner {
    public fun scan(project: ProjectRoot): ProjectInventory
}

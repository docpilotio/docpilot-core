package io.docpilot.core.incremental

/**
 * Classification of one file between two project snapshots.
 */
enum class ProjectFileChangeType {
    ADDED,
    MODIFIED,
    REMOVED,
    UNCHANGED,
}

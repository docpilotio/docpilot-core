package io.docpilot.core.incremental

/**
 * Deterministically ordered difference between two project snapshots.
 */
data class ProjectChangeSet(
    val changes: List<ProjectFileChange>,
) {
    init {
        val paths = changes.map { it.relativePath }

        require(paths.distinct().size == paths.size) {
            "Project change paths must be unique."
        }
        require(changes == changes.sortedBy { it.relativePath }) {
            "Project changes must be sorted by relativePath."
        }
    }

    val added: List<ProjectFileChange> =
        changes.filter { it.type == ProjectFileChangeType.ADDED }

    val modified: List<ProjectFileChange> =
        changes.filter { it.type == ProjectFileChangeType.MODIFIED }

    val removed: List<ProjectFileChange> =
        changes.filter { it.type == ProjectFileChangeType.REMOVED }

    val unchanged: List<ProjectFileChange> =
        changes.filter { it.type == ProjectFileChangeType.UNCHANGED }

    val hasChanges: Boolean =
        added.isNotEmpty() ||
            modified.isNotEmpty() ||
            removed.isNotEmpty()
}

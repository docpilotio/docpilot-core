package io.docpilot.core.incremental

/**
 * Deterministic content snapshot of selected project files.
 */
data class ProjectSnapshot(
    val files: List<SourceFileFingerprint>,
) {
    init {
        val paths = files.map { it.relativePath }

        require(paths.distinct().size == paths.size) {
            "Project snapshot relative paths must be unique."
        }
        require(files == files.sortedBy { it.relativePath }) {
            "Project snapshot files must be sorted by relativePath."
        }
    }

    val filesByRelativePath: Map<String, SourceFileFingerprint> =
        files.associateBy { it.relativePath }
}

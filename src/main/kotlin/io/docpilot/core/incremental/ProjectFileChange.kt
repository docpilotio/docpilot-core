package io.docpilot.core.incremental

/**
 * Change information for one normalized project-relative path.
 */
data class ProjectFileChange(
    val relativePath: String,
    val type: ProjectFileChangeType,
    val previous: SourceFileFingerprint?,
    val current: SourceFileFingerprint?,
) {
    init {
        require(relativePath.isNotBlank()) {
            "Project file change relativePath must not be blank."
        }
        require(
            previous?.relativePath == null ||
                previous.relativePath == relativePath,
        ) {
            "Previous fingerprint path must match change path."
        }
        require(
            current?.relativePath == null ||
                current.relativePath == relativePath,
        ) {
            "Current fingerprint path must match change path."
        }

        when (type) {
            ProjectFileChangeType.ADDED -> {
                require(previous == null && current != null)
            }
            ProjectFileChangeType.MODIFIED,
            ProjectFileChangeType.UNCHANGED,
            -> {
                require(previous != null && current != null)
            }
            ProjectFileChangeType.REMOVED -> {
                require(previous != null && current == null)
            }
        }
    }
}

package io.docpilot.core.incremental

/**
 * Content-hash based project change detector.
 */
class DefaultProjectChangeDetector : ProjectChangeDetector {

    override fun detect(
        previous: ProjectSnapshot,
        current: ProjectSnapshot,
    ): ProjectChangeSet {
        val previousByPath = previous.filesByRelativePath
        val currentByPath = current.filesByRelativePath

        val allPaths = (
            previousByPath.keys +
                currentByPath.keys
            )
            .toSortedSet()

        val changes = allPaths.map { relativePath ->
            val previousFile = previousByPath[relativePath]
            val currentFile = currentByPath[relativePath]

            val type = when {
                previousFile == null ->
                    ProjectFileChangeType.ADDED

                currentFile == null ->
                    ProjectFileChangeType.REMOVED

                previousFile.contentSha256 !=
                    currentFile.contentSha256 ->
                    ProjectFileChangeType.MODIFIED

                else ->
                    ProjectFileChangeType.UNCHANGED
            }

            ProjectFileChange(
                relativePath = relativePath,
                type = type,
                previous = previousFile,
                current = currentFile,
            )
        }

        return ProjectChangeSet(changes)
    }
}

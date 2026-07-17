package io.docpilot.core.incremental

/**
 * Compares two deterministic project snapshots.
 */
fun interface ProjectChangeDetector {

    fun detect(
        previous: ProjectSnapshot,
        current: ProjectSnapshot,
    ): ProjectChangeSet
}

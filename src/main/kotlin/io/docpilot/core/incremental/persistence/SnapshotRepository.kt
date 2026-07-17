package io.docpilot.core.incremental.persistence

/**
 * Stores the latest and previous project snapshots.
 */
interface SnapshotRepository {

    fun loadLatest(): StoredProjectSnapshot?

    fun loadPrevious(): StoredProjectSnapshot?

    fun save(
        snapshot: StoredProjectSnapshot,
    )
}

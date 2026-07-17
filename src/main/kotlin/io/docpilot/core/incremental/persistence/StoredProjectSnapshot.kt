package io.docpilot.core.incremental.persistence

import io.docpilot.core.incremental.ProjectSnapshot
import java.time.Instant

/**
 * Persisted snapshot envelope.
 *
 * [createdAt] is operational metadata and is not used for change detection.
 */
data class StoredProjectSnapshot(
    val schemaVersion: Int = SnapshotFormat.CURRENT_SCHEMA_VERSION,
    val createdAt: Instant,
    val snapshot: ProjectSnapshot,
) {
    init {
        require(schemaVersion > 0) {
            "Snapshot schemaVersion must be greater than zero."
        }
    }
}

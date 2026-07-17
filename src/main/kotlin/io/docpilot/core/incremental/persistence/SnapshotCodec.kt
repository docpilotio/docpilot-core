package io.docpilot.core.incremental.persistence

/**
 * Serializes and deserializes stored project snapshots.
 */
interface SnapshotCodec {

    fun encode(
        value: StoredProjectSnapshot,
    ): String

    fun decode(
        value: String,
    ): StoredProjectSnapshot
}

package io.docpilot.core.incremental.persistence

/**
 * Deterministic JSON codec for the RFC-0028 snapshot schema.
 *
 * JSON writing and reading are delegated to separate stateless components so
 * parsing code never depends on an outer class receiver.
 */
class JsonSnapshotCodec(
    private val writer: JsonSnapshotWriter = JsonSnapshotWriter(),
    private val reader: JsonSnapshotReader = JsonSnapshotReader(),
) : SnapshotCodec {

    override fun encode(
        value: StoredProjectSnapshot,
    ): String = writer.write(value)

    override fun decode(
        value: String,
    ): StoredProjectSnapshot = reader.read(value)
}

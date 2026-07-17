package io.docpilot.core.incremental.specification.snapshot

import io.docpilot.core.model.ProjectSpecification

public object SpecificationSnapshotFormat {
    public const val CURRENT_VERSION: Int = 1
    public const val SUPPORTED_DIR_SCHEMA_VERSION: String = "0.3"
    public const val DEFAULT_RELATIVE_PATH: String = ".docpilot/snapshots/specification.json"
}

public data class SnapshotProjectIdentity(public val projectId: String) {
    init { require(projectId.isNotBlank()) { "Snapshot project id must not be blank." } }
}

public data class SnapshotIntegrity(
    public val algorithm: String = "SHA-256",
    public val payloadSha256: String,
) {
    init {
        require(algorithm == "SHA-256") { "Unsupported snapshot integrity algorithm: $algorithm" }
        require(payloadSha256.matches(Regex("[0-9a-f]{64}"))) { "Snapshot SHA-256 must be 64 lowercase hexadecimal characters." }
    }
}

public data class StoredSpecificationSnapshot(
    public val snapshotFormatVersion: Int,
    public val dirSchemaVersion: String,
    public val projectIdentity: SnapshotProjectIdentity,
    public val specification: ProjectSpecification,
    public val integrity: SnapshotIntegrity,
)

public enum class SnapshotValidationFailure {
    CORRUPTED,
    UNSUPPORTED_VERSION,
    SCHEMA_MISMATCH,
    PROJECT_MISMATCH,
    INTEGRITY_MISMATCH,
    INVALID_SPECIFICATION,
}

public sealed interface SpecificationSnapshotLoadResult {
    public data object NotFound : SpecificationSnapshotLoadResult
    public data class Valid(val snapshot: StoredSpecificationSnapshot) : SpecificationSnapshotLoadResult
    public data class Invalid(val reason: SnapshotValidationFailure, val message: String) : SpecificationSnapshotLoadResult
}

public interface SpecificationSnapshotRepository {
    public fun load(expectedProjectId: String): SpecificationSnapshotLoadResult
    public fun save(specification: ProjectSpecification)
}

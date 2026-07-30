package io.docpilot.core.incremental.specification.snapshot

import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.specification.ProjectSpecificationValidator

/** Explicit, lossless DIR 0.3 to 0.4 migration. Existing entity IDs and order are preserved. */
public object SpecificationSnapshotMigration {
    public fun migrateDir03To04(specification: ProjectSpecification): ProjectSpecification {
        require(specification.schemaVersion == SpecificationSnapshotFormat.LEGACY_DIR_SCHEMA_VERSION) {
            "Only DIR 0.3 specifications can be migrated to DIR 0.4."
        }
        ProjectSpecificationValidator.validate(specification)
        return specification.copy(
            schemaVersion = SpecificationSnapshotFormat.DIR_0_4_SCHEMA_VERSION,
            features = emptyList(),
            entryPoints = emptyList(),
            scenarios = emptyList(),
        ).also(ProjectSpecificationValidator::validate)
    }

    public fun migrate(snapshot: StoredSpecificationSnapshot): StoredSpecificationSnapshot {
        require(snapshot.snapshotFormatVersion == SpecificationSnapshotFormat.LEGACY_VERSION)
        val specification = migrateDir03To04(snapshot.specification)
        val payload = JsonSpecificationSnapshotCodec().encodePayload(specification)
        return StoredSpecificationSnapshot(
            snapshotFormatVersion = SpecificationSnapshotFormat.DIR_0_4_VERSION,
            dirSchemaVersion = SpecificationSnapshotFormat.DIR_0_4_SCHEMA_VERSION,
            projectIdentity = snapshot.projectIdentity,
            specification = specification,
            integrity = SnapshotIntegrity(payloadSha256 = SpecificationSnapshotIntegrity.sha256(payload)),
        )
    }
}

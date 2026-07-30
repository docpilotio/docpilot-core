package io.docpilot.core.incremental.specification.snapshot

import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.specification.FeatureDir04SpecificationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FeatureSnapshotFormat2Test {
    private val codec = JsonSpecificationSnapshotCodec()

    @Test
    fun `format 2 is deterministic and round trips DIR 0_4`() {
        val specification = FeatureDir04SpecificationTest().specification()
        val encoded = codec.encode(specification)
        assertEquals(encoded, codec.encode(specification))
        assertEquals(
            encoded,
            codec.encode(
                specification.copy(
                    features = specification.features.reversed(),
                    entryPoints = specification.entryPoints.reversed(),
                    scenarios = specification.scenarios.reversed(),
                ),
            ),
        )
        assertTrue(encoded.contains("\"snapshotFormatVersion\": 2"))
        val loaded = assertIs<SpecificationSnapshotLoadResult.Valid>(codec.decode(encoded, specification.project.id))
        assertEquals(specification, loaded.snapshot.specification)
    }

    @Test
    fun `legacy format 1 remains readable and migrates explicitly with stable ids`() {
        val legacy = ProjectSpecification("0.3", ProjectDescriptor("project:legacy", "Legacy"))
        val encoded = codec.encode(legacy)
        assertTrue(encoded.contains("\"snapshotFormatVersion\": 1"))
        val loaded = assertIs<SpecificationSnapshotLoadResult.Valid>(codec.decode(encoded, legacy.project.id)).snapshot
        val migrated = SpecificationSnapshotMigration.migrate(loaded)
        assertEquals(2, migrated.snapshotFormatVersion)
        assertEquals("0.4", migrated.specification.schemaVersion)
        assertEquals(legacy.project.id, migrated.specification.project.id)
        assertTrue(migrated.specification.features.isEmpty())
        assertTrue(migrated.specification.entryPoints.isEmpty())
        assertTrue(migrated.specification.scenarios.isEmpty())
    }
}

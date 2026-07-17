package io.docpilot.core.incremental.specification.snapshot

import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.ProjectSpecification
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FileSpecificationSnapshotRepositoryTest {
    @Test
    fun `saves and loads specification snapshot`() {
        val root = Files.createTempDirectory("docpilot-spec-snapshot")
        val repository = FileSpecificationSnapshotRepository(root)
        val specification = ProjectSpecification("0.3", ProjectDescriptor("project:sample", "Sample"))

        assertIs<SpecificationSnapshotLoadResult.NotFound>(repository.load(specification.project.id))
        repository.save(specification)

        val loaded = assertIs<SpecificationSnapshotLoadResult.Valid>(repository.load(specification.project.id))
        assertEquals(specification, loaded.snapshot.specification)
        assertTrue(Files.exists(root.resolve(".docpilot/snapshots/specification.json")))
    }
}

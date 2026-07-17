package io.docpilot.core.incremental.specification.snapshot

import io.docpilot.core.incremental.execution.*
import io.docpilot.core.incremental.specification.IncrementalDocumentationEngine
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.ProjectSpecification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpecificationSnapshotExecutionCoordinatorTest {
    @Test
    fun `creates snapshot after successful full regeneration`() {
        val repository = InMemoryRepository(SpecificationSnapshotLoadResult.NotFound)
        val coordinator = coordinator(repository, IncrementalExecutionMode.FULL_REGENERATION)

        val result = coordinator.execute(specification())

        assertTrue(result.snapshotSaved)
        assertEquals(1, repository.saveCalls)
    }

    @Test
    fun `does not save after failed execution`() {
        val repository = InMemoryRepository(SpecificationSnapshotLoadResult.NotFound)
        val coordinator = coordinator(repository, IncrementalExecutionMode.FAILED)

        val result = coordinator.execute(specification())

        assertFalse(result.snapshotSaved)
        assertEquals(0, repository.saveCalls)
        assertEquals(SnapshotExecutionFailureStage.DOCUMENTATION_EXECUTION, result.failureStage)
    }

    @Test
    fun `does not rewrite valid snapshot when no changes exist`() {
        val specification = specification()
        val stored = StoredSpecificationSnapshot(1, "0.3", SnapshotProjectIdentity(specification.project.id), specification, SnapshotIntegrity(payloadSha256 = "0".repeat(64)))
        val repository = InMemoryRepository(SpecificationSnapshotLoadResult.Valid(stored))
        val coordinator = coordinator(repository, IncrementalExecutionMode.NO_CHANGES)

        val result = coordinator.execute(specification)

        assertFalse(result.snapshotSaved)
        assertEquals(0, repository.saveCalls)
    }

    private fun coordinator(repository: InMemoryRepository, mode: IncrementalExecutionMode) = SpecificationSnapshotExecutionCoordinator(
        repository,
        IncrementalDocumentationEngine(),
        IncrementalDocumentationExecutor { IncrementalDocumentationExecutionResult(mode = mode, errorMessage = if (mode == IncrementalExecutionMode.FAILED) "failed" else null) },
    )

    private fun specification() = ProjectSpecification("0.3", ProjectDescriptor("project:sample", "Sample"))

    private class InMemoryRepository(private val loadResult: SpecificationSnapshotLoadResult) : SpecificationSnapshotRepository {
        var saveCalls = 0
        override fun load(expectedProjectId: String) = loadResult
        override fun save(specification: ProjectSpecification) { saveCalls++ }
    }
}

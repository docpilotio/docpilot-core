package io.docpilot.core.specification.finding

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FindingRegistryTest {
    private fun finding(id: String, subject: String = "component:A") = Finding(
        id = FindingId(id), subjectStableId = subject, semanticKey = "k-$id", category = "reliability",
        severity = FindingSeverity.MEDIUM, summary = "summary for $id", evidenceRefs = setOf("evidence:e1"),
    )

    @Test
    fun `load is NotFound before any merge`() {
        val repository = FileFindingRegistryRepository(createTempDirectory("docpilot-finding-registry"))

        assertEquals(FindingRegistryLoadResult.NotFound, repository.load("project-a"))
    }

    @Test
    fun `merge persists and round-trips through load`() {
        val root = createTempDirectory("docpilot-finding-registry")
        val repository = FileFindingRegistryRepository(root)

        repository.merge("project-a", listOf(finding("finding:1")))
        val loaded = repository.load("project-a")

        assertIs<FindingRegistryLoadResult.Valid>(loaded)
        assertEquals(listOf(finding("finding:1")), loaded.registry.findings)
        assertTrue(Files.isRegularFile(root.resolve(FindingRegistryFormat.DEFAULT_RELATIVE_PATH)))
    }

    @Test
    fun `merge does not duplicate an already-present finding id`() {
        val root = createTempDirectory("docpilot-finding-registry")
        val repository = FileFindingRegistryRepository(root)

        repository.merge("project-a", listOf(finding("finding:1")))
        val merged = repository.merge("project-a", listOf(finding("finding:1"), finding("finding:2")))

        assertEquals(2, merged.size)
        assertEquals(listOf("finding:1", "finding:2"), merged.map { it.id.value })
    }

    @Test
    fun `separate repository instances accumulate across invocations`() {
        val root = createTempDirectory("docpilot-finding-registry")

        FileFindingRegistryRepository(root).merge("project-a", listOf(finding("finding:1")))
        FileFindingRegistryRepository(root).merge("project-a", listOf(finding("finding:2")))
        val loaded = FileFindingRegistryRepository(root).load("project-a")

        assertIs<FindingRegistryLoadResult.Valid>(loaded)
        assertEquals(setOf("finding:1", "finding:2"), loaded.registry.findings.map { it.id.value }.toSet())
    }

    @Test
    fun `load reports a project id mismatch`() {
        val root = createTempDirectory("docpilot-finding-registry")
        FileFindingRegistryRepository(root).merge("project-a", listOf(finding("finding:1")))

        val loaded = FileFindingRegistryRepository(root).load("project-b")

        assertIs<FindingRegistryLoadResult.Invalid>(loaded)
        assertEquals(FindingRegistryValidationFailure.PROJECT_MISMATCH, loaded.reason)
    }

    @Test
    fun `load fails closed on a tampered payload`() {
        val root = createTempDirectory("docpilot-finding-registry")
        FileFindingRegistryRepository(root).merge("project-a", listOf(finding("finding:1")))
        val path = root.resolve(FindingRegistryFormat.DEFAULT_RELATIVE_PATH)
        val tampered = Files.readString(path, StandardCharsets.UTF_8).replace("summary for finding:1", "tampered")
        Files.writeString(path, tampered, StandardCharsets.UTF_8)

        val loaded = FileFindingRegistryRepository(root).load("project-a")

        assertIs<FindingRegistryLoadResult.Invalid>(loaded)
        assertEquals(FindingRegistryValidationFailure.INTEGRITY_MISMATCH, loaded.reason)
    }
}

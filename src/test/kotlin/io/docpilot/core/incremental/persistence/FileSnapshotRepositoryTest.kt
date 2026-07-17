package io.docpilot.core.incremental.persistence

import io.docpilot.core.incremental.ProjectSnapshot
import io.docpilot.core.incremental.SourceFileFingerprint
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileSnapshotRepositoryTest {

    @Test
    fun `missing files return null`() {
        val projectRoot =
            Files.createTempDirectory("docpilot-repository")
        val repository = FileSnapshotRepository(projectRoot)

        assertNull(repository.loadLatest())
        assertNull(repository.loadPrevious())
    }

    @Test
    fun `first save creates latest snapshot`() {
        val projectRoot =
            Files.createTempDirectory("docpilot-repository")
        val repository = FileSnapshotRepository(projectRoot)
        val snapshot = storedSnapshot("a")

        repository.save(snapshot)

        assertEquals(snapshot, repository.loadLatest())
        assertNull(repository.loadPrevious())
        assertTrue(
            Files.exists(
                projectRoot.resolve(
                    ".docpilot/snapshots/latest.json",
                ),
            ),
        )
    }

    @Test
    fun `second save rotates latest to previous`() {
        val projectRoot =
            Files.createTempDirectory("docpilot-repository")
        val repository = FileSnapshotRepository(projectRoot)
        val first = storedSnapshot("a")
        val second = storedSnapshot("b")

        repository.save(first)
        repository.save(second)

        assertEquals(second, repository.loadLatest())
        assertEquals(first, repository.loadPrevious())
    }

    private fun storedSnapshot(
        marker: String,
    ): StoredProjectSnapshot =
        StoredProjectSnapshot(
            createdAt = Instant.parse(
                if (marker == "a") {
                    "2026-07-17T00:00:00Z"
                } else {
                    "2026-07-17T01:00:00Z"
                },
            ),
            snapshot = ProjectSnapshot(
                files = listOf(
                    SourceFileFingerprint(
                        relativePath = "src/Sample.kt",
                        contentSha256 = marker.repeat(64),
                        sizeBytes = 10,
                    ),
                ),
            ),
        )
}

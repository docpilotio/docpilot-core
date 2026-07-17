package io.docpilot.core.incremental

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultProjectChangeDetectorTest {

    private val detector = DefaultProjectChangeDetector()

    @Test
    fun `detects added modified removed and unchanged files`() {
        val previous = ProjectSnapshot(
            files = listOf(
                fingerprint("Modified.kt", "a"),
                fingerprint("Removed.kt", "b"),
                fingerprint("Unchanged.kt", "c"),
            ),
        )
        val current = ProjectSnapshot(
            files = listOf(
                fingerprint("Added.kt", "d"),
                fingerprint("Modified.kt", "e"),
                fingerprint("Unchanged.kt", "c"),
            ),
        )

        val changes = detector.detect(previous, current)

        assertEquals(
            listOf("Added.kt"),
            changes.added.map { it.relativePath },
        )
        assertEquals(
            listOf("Modified.kt"),
            changes.modified.map { it.relativePath },
        )
        assertEquals(
            listOf("Removed.kt"),
            changes.removed.map { it.relativePath },
        )
        assertEquals(
            listOf("Unchanged.kt"),
            changes.unchanged.map { it.relativePath },
        )
        assertTrue(changes.hasChanges)
    }

    @Test
    fun `identical snapshots have no changes`() {
        val snapshot = ProjectSnapshot(
            files = listOf(
                fingerprint("Sample.kt", "a"),
            ),
        )

        val changes = detector.detect(snapshot, snapshot)

        assertFalse(changes.hasChanges)
        assertEquals(1, changes.unchanged.size)
    }

    private fun fingerprint(
        relativePath: String,
        marker: String,
    ): SourceFileFingerprint =
        SourceFileFingerprint(
            relativePath = relativePath,
            contentSha256 = marker.repeat(64),
            sizeBytes = 1,
        )
}

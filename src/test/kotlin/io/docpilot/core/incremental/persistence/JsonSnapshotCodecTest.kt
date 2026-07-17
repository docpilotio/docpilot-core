package io.docpilot.core.incremental.persistence

import io.docpilot.core.incremental.ProjectSnapshot
import io.docpilot.core.incremental.SourceFileFingerprint
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsonSnapshotCodecTest {

    private val codec = JsonSnapshotCodec()

    @Test
    fun `snapshot round trips through json`() {
        val expected = StoredProjectSnapshot(
            createdAt = Instant.parse("2026-07-17T00:00:00Z"),
            snapshot = ProjectSnapshot(
                files = listOf(
                    fingerprint(
                        relativePath = "src/A.kt",
                        marker = "a",
                        sizeBytes = 12,
                    ),
                    fingerprint(
                        relativePath = "src/B.kt",
                        marker = "b",
                        sizeBytes = 24,
                    ),
                ),
            ),
        )

        val actual = codec.decode(codec.encode(expected))

        assertEquals(expected, actual)
    }

    @Test
    fun `codec escapes path characters`() {
        val expected = StoredProjectSnapshot(
            createdAt = Instant.parse("2026-07-17T00:00:00Z"),
            snapshot = ProjectSnapshot(
                files = listOf(
                    fingerprint(
                        relativePath = "src/Quoted\"Name.kt",
                        marker = "c",
                        sizeBytes = 1,
                    ),
                ),
            ),
        )

        val json = codec.encode(expected)
        val actual = codec.decode(json)

        assertTrue("\\\"" in json)
        assertEquals(expected, actual)
    }

    @Test
    fun `unsupported schema version fails`() {
        val json = """
            {
              "schemaVersion": 99,
              "createdAt": "2026-07-17T00:00:00Z",
              "files": []
            }
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            codec.decode(json)
        }
    }

    private fun fingerprint(
        relativePath: String,
        marker: String,
        sizeBytes: Long,
    ): SourceFileFingerprint =
        SourceFileFingerprint(
            relativePath = relativePath,
            contentSha256 = marker.repeat(64),
            sizeBytes = sizeBytes,
        )
}

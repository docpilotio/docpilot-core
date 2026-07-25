package io.docpilot.release

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReleaseEvidenceRepositoryTest {
    @Test
    fun `publishes manifest and report without overwriting`() {
        val root = Files.createTempDirectory("release-evidence")
        val target = root.resolve("v0.5.0-rc.1")
        val codec = ReleaseEvidenceCodec()
        val manifest = codec.create(passingInput())
        val repository = ReleaseEvidenceRepository()

        val saved = repository.saveNew(target, manifest)

        assertTrue(Files.isRegularFile(saved))
        assertTrue(Files.isRegularFile(target.resolve(ReleaseEvidenceRepository.REPORT_FILE)))
        assertEquals(manifest, repository.load(saved))
        assertFailsWith<IllegalArgumentException> { repository.saveNew(target, manifest) }
    }
}

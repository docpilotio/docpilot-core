package io.docpilot.release

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReleaseEvidenceCodecTest {
    private val codec = ReleaseEvidenceCodec()

    @Test
    fun `canonical manifest round trips byte identically`() {
        val manifest = codec.create(passingInput())
        val encoded = codec.encode(manifest)
        val decoded = codec.decode(encoded)

        assertEquals(manifest, decoded)
        assertEquals(encoded, codec.encode(decoded))
        assertTrue(encoded.endsWith("\n"))
        assertTrue(manifest.integrity.payloadSha256.isSha256())
        assertEquals(2, manifest.releaseEvidenceFormatVersion)
        assertTrue("mcpMode" !in encoded)
        assertTrue("mcpCommit" !in encoded)
        assertTrue("mcpVersion" !in encoded)
    }

    @Test
    fun `legacy format one manifest is rejected instead of being interpreted as core only`() {
        val encoded = codec.encode(codec.create(passingInput()))
        val legacyVersion = encoded.replaceFirst(
            "\"releaseEvidenceFormatVersion\": 2",
            "\"releaseEvidenceFormatVersion\": 1",
        )

        val failure = assertFailsWith<IllegalArgumentException> { codec.decode(legacyVersion) }
        assertTrue(failure.message.orEmpty().contains("Unsupported Release Evidence Manifest format: 1"))
    }

    @Test
    fun `integrity mismatch rejects the complete manifest`() {
        val encoded = codec.encode(codec.create(passingInput()))
        val corrupted = encoded.replace("\"tests\": 273", "\"tests\": 274")

        assertFailsWith<IllegalArgumentException> { codec.decode(corrupted) }
    }

    @Test
    fun `unknown fields are rejected`() {
        val encoded = codec.encode(codec.create(passingInput()))
        val unknown = encoded.replaceFirst("{\n", "{\n  \"unknown\": true,\n")

        assertFailsWith<IllegalArgumentException> { codec.decode(unknown) }
    }

    @Test
    fun `semantic input ordering does not change canonical bytes`() {
        val input = passingInput()
        val reordered = input.copy(
            executions = input.executions.reversed(),
            artifacts = input.artifacts.reversed(),
            compatibilityChecks = input.compatibilityChecks.reversed(),
        )

        assertEquals(codec.encode(codec.create(input)), codec.encode(codec.create(reordered)))
    }
}

package io.docpilot.core.evolution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EvolutionReportCodecTest {
    @Test
    fun `canonical format round trips and rejects tampering and unknown records`() {
        val report = DefaultDocumentationEvolutionAnalyzer().analyze(EvolutionTestFixtures.request())
        val codec = EvolutionReportCodec()
        val encoded = codec.encode(report)

        assertEquals(report, codec.decode(encoded))
        assertEquals(encoded, codec.encode(codec.decode(encoded)))
        assertFailsWith<IllegalArgumentException> {
            codec.decode(encoded.replace(report.reportSha256, "0".repeat(64)))
        }
        assertFailsWith<IllegalStateException> {
            codec.decode(encoded.replace("REPORT_SHA256", "UNKNOWN"))
        }
    }
}

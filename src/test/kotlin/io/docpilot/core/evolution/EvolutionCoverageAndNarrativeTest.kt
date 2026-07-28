package io.docpilot.core.evolution

import io.docpilot.core.incremental.specification.snapshot.SnapshotIntegrity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EvolutionCoverageAndNarrativeTest {
    @Test
    fun `missing optional relationship evidence produces partial coverage`() {
        val request = EvolutionTestFixtures.request()
        val report = DefaultDocumentationEvolutionAnalyzer().analyze(
            request.copy(beforeRelationshipReport = null, afterRelationshipReport = null),
        )

        assertEquals(EvolutionCoverageState.PARTIAL_MISSING_OPTIONAL_EVIDENCE, report.coverage.state)
        assertTrue(report.coverage.findings.any { it.kind == EvolutionCoverageFindingKind.MISSING_RELATIONSHIP_REPORT })
        assertTrue(EvolutionReportVerifier().verify(report))
    }

    @Test
    fun `incompatible and tampered snapshots fail closed`() {
        val request = EvolutionTestFixtures.request()
        val incompatible = DefaultDocumentationEvolutionAnalyzer().analyze(
            request.copy(
                afterSnapshot = request.afterSnapshot.copy(
                    projectIdentity = request.afterSnapshot.projectIdentity.copy(projectId = "project:other"),
                ),
            ),
        )
        assertEquals(EvolutionCoverageState.BLOCKED_INCOMPATIBLE_FORMAT, incompatible.coverage.state)

        val tampered = DefaultDocumentationEvolutionAnalyzer().analyze(
            request.copy(
                afterSnapshot = request.afterSnapshot.copy(
                    integrity = SnapshotIntegrity(payloadSha256 = "0".repeat(64)),
                ),
            ),
        )
        assertEquals(EvolutionCoverageState.BLOCKED_INTEGRITY_FAILURE, tampered.coverage.state)
    }

    @Test
    fun `optional AI narrative cannot alter authoritative report SHA`() {
        val report = DefaultDocumentationEvolutionAnalyzer().analyze(EvolutionTestFixtures.request())
        val first = EvolutionNarrativeRenderer { "Narrative A for ${it.reportSha256}" }.render(report)
        val second = EvolutionNarrativeRenderer { "Narrative B for ${it.reportSha256}" }.render(report)

        assertNotEquals(first, second)
        assertEquals(report.reportSha256, EvolutionReportCodec().decode(EvolutionReportCodec().encode(report)).reportSha256)
    }
}

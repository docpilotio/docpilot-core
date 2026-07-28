package io.docpilot.core.evolution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentationEvolutionAnalyzerTest {
    @Test
    fun `explains moved entity api property relationship and artifact impact completely`() {
        val report = DefaultDocumentationEvolutionAnalyzer().analyze(EvolutionTestFixtures.request())

        assertEquals(EvolutionCoverageState.COMPLETE, report.coverage.state)
        assertTrue(report.changes.any { it.kind == EvolutionChangeKind.ENTITY_MOVED })
        assertTrue(report.changes.any { it.kind == EvolutionChangeKind.ENTITY_MODIFIED && "name" in it.changedFields })
        assertTrue(report.changes.any { it.kind == EvolutionChangeKind.API_CHANGED })
        assertTrue(report.changes.any { it.kind == EvolutionChangeKind.PROPERTY_CHANGED })
        assertTrue(report.changes.any { it.kind == EvolutionChangeKind.RELATIONSHIP_ADDED })
        assertEquals(2, report.impactedArtifacts.size)
        assertTrue(EvolutionReportVerifier().verify(report))
    }

    @Test
    fun `result is independent of specification catalog inventory and evidence order`() {
        val first = DefaultDocumentationEvolutionAnalyzer().analyze(EvolutionTestFixtures.request())
        val second = DefaultDocumentationEvolutionAnalyzer().analyze(EvolutionTestFixtures.request(reverse = true))

        assertEquals(first, second)
        assertEquals(EvolutionReportRenderer().render(first), EvolutionReportRenderer().render(second))
    }

    @Test
    fun `tampered artifact plan blocks analysis`() {
        val request = EvolutionTestFixtures.request()
        val report = DefaultDocumentationEvolutionAnalyzer().analyze(
            request.copy(artifactPlan = request.artifactPlan.copy(planSha256 = "0".repeat(64))),
        )

        assertEquals(EvolutionCoverageState.BLOCKED_INTEGRITY_FAILURE, report.coverage.state)
        assertTrue(report.changes.isEmpty())
        assertTrue(report.coverage.findings.any { it.kind == EvolutionCoverageFindingKind.INVALID_ARTIFACT_PLAN })
        assertTrue(EvolutionReportVerifier().verify(report))
    }
}

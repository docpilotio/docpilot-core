package io.docpilot.core.evolution

import io.docpilot.core.incremental.execution.DocumentationArtifactPlanVerifier
import io.docpilot.core.specification.RelationshipProjectionVerifier
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvolutionIntegrityBridgeTest {
    @Test
    fun `verifies RFC-0052 plan and RFC-0053 report without changing their hashes`() {
        val request = EvolutionTestFixtures.request()
        assertTrue(
            DocumentationArtifactPlanVerifier().verify(
                request.artifactPlan,
                request.afterSnapshot.specification,
                request.beforeCatalog,
                request.afterCatalog,
                request.existingArtifacts,
            ),
        )
        assertFalse(
            DocumentationArtifactPlanVerifier().verify(
                request.artifactPlan.copy(planSha256 = "0".repeat(64)),
                request.afterSnapshot.specification,
                request.beforeCatalog,
                request.afterCatalog,
                request.existingArtifacts,
            ),
        )
        val relationshipReport = requireNotNull(request.afterRelationshipReport)
        assertTrue(RelationshipProjectionVerifier().verify(relationshipReport))
        assertFalse(RelationshipProjectionVerifier().verify(relationshipReport.copy(reportSha256 = "0".repeat(64))))
    }
}

package io.docpilot.core.evolution

import io.docpilot.core.api.DocumentationArtifactId
import io.docpilot.core.reconciliation.DefaultDocumentationReconciler
import io.docpilot.core.reconciliation.DocumentationOwnership
import io.docpilot.core.reconciliation.DocumentationOwnershipManifest
import io.docpilot.core.reconciliation.DocumentationReconciliationRequest
import io.docpilot.core.reconciliation.ReconciliationApplyResult
import io.docpilot.core.reconciliation.ReconciliationDocumentInput
import io.docpilot.core.reconciliation.ReconciliationDocumentStore
import io.docpilot.core.reconciliation.DocumentationReconciliationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EvolutionReconciliationBindingTest {
    @Test
    fun `binds ownership changes conflicts and retained user decisions`() {
        val base = EvolutionTestFixtures.request()
        val beforeManifest = manifest(DocumentationOwnership.DOCPILOT_OWNED, "before-service")
        val afterManifest = manifest(DocumentationOwnership.USER_OWNED, "before-service")
        val ownershipReport = DefaultDocumentationEvolutionAnalyzer().analyze(
            base.copy(
                beforeOwnershipManifests = listOf(beforeManifest),
                afterOwnershipManifests = listOf(afterManifest),
            ),
        )
        assertTrue(ownershipReport.changes.any { it.kind == EvolutionChangeKind.OWNERSHIP_CHANGED })

        val reconciler = DefaultDocumentationReconciler()
        val conflictPlan = reconciler.preview(
            DocumentationReconciliationRequest(
                EvolutionTestFixtures.PROJECT_ID,
                listOf(
                    ReconciliationDocumentInput(
                        DocumentationArtifactId("component:service"),
                        "docs/service.md",
                        reviewedBase = "before-service",
                        current = "before-service",
                        candidate = "after-service",
                        manifest = null,
                    ),
                ),
            ),
        )
        val conflictReport = DefaultDocumentationEvolutionAnalyzer().analyze(base.copy(reconciliationPlan = conflictPlan))
        assertTrue(conflictReport.changes.any { it.kind == EvolutionChangeKind.RECONCILIATION_CONFLICTED })

        val userPlan = reconciler.preview(
            DocumentationReconciliationRequest(
                EvolutionTestFixtures.PROJECT_ID,
                listOf(
                    ReconciliationDocumentInput(
                        DocumentationArtifactId("component:service"),
                        "docs/service.md",
                        reviewedBase = "before-service",
                        current = "before-service",
                        candidate = "after-service",
                        manifest = afterManifest,
                    ),
                ),
            ),
        )
        val store = MemoryStore(mapOf("docs/service.md" to "before-service"))
        val result = assertIs<ReconciliationApplyResult.Applied>(
            reconciler.apply(userPlan, emptyList(), emptyList(), store),
        ).result
        val userReport = DefaultDocumentationEvolutionAnalyzer().analyze(
            base.copy(
                reconciliationPlan = userPlan,
                reconciliationResult = result,
                beforeOwnershipManifests = listOf(afterManifest),
                afterOwnershipManifests = listOf(afterManifest),
            ),
        )
        assertTrue(userReport.changes.any { it.kind == EvolutionChangeKind.USER_DECISION_APPLIED })
        assertTrue(userReport.changes.any { it.kind == EvolutionChangeKind.ARTIFACT_RETAINED })
        assertEquals(
            EvolutionCanonicalizer.sha256("before-service"),
            userReport.impactedArtifacts.single { it.artifactId == "component:service" }.afterArtifactSha256,
        )
    }

    private fun manifest(ownership: DocumentationOwnership, base: String): DocumentationOwnershipManifest {
        val unsigned = DocumentationOwnershipManifest(
            artifactId = DocumentationArtifactId("component:service"),
            relativePath = "docs/service.md",
            mediaType = "text/markdown",
            ownership = ownership,
            reviewedBaseSha256 = EvolutionCanonicalizer.sha256(base),
            rendererIdentity = "test",
            evidenceRefs = listOf("review:test"),
            manifestSha256 = "",
        )
        val payload = buildString {
            append(unsigned.formatVersion).append('|').append(unsigned.artifactId.value).append('|')
            append(unsigned.relativePath).append('|').append(unsigned.mediaType).append('|')
            append(unsigned.ownership.name).append('|').append(unsigned.reviewedBaseSha256 ?: "").append('|')
            append(unsigned.rendererIdentity).append('|')
            append(unsigned.evidenceRefs.distinct().sorted().joinToString(",")).append('\n')
        }
        return unsigned.copy(manifestSha256 = EvolutionCanonicalizer.sha256(payload))
    }

    private class MemoryStore(initial: Map<String, String>) : ReconciliationDocumentStore {
        private val documents = initial.toMutableMap()
        private var result: DocumentationReconciliationResult? = null
        override fun read(relativePath: String): String? = documents[relativePath]
        override fun applyAtomically(
            expectedCurrentShaByPath: Map<String, String?>,
            documents: Map<String, String>,
            manifests: Map<String, DocumentationOwnershipManifest>,
            result: DocumentationReconciliationResult,
        ): Boolean {
            this.documents.putAll(documents)
            this.result = result
            return true
        }
        override fun findResult(planSha256: String): DocumentationReconciliationResult? =
            result?.takeIf { it.planSha256 == planSha256 }
    }
}

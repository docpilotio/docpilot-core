package io.docpilot.core.reconciliation

import io.docpilot.core.api.DocumentationArtifactId
import io.docpilot.core.incremental.execution.DocumentationArtifactOperation
import io.docpilot.core.incremental.execution.DocumentationArtifactPlan
import io.docpilot.core.incremental.execution.DocumentationArtifactPlanAction
import io.docpilot.core.incremental.specification.ChangeKind
import io.docpilot.core.incremental.specification.IncrementalUpdateTarget
import io.docpilot.core.incremental.specification.ai.AiDocumentationPatchOperation
import io.docpilot.core.incremental.specification.review.DocumentationChangeKind
import io.docpilot.core.incremental.specification.review.DocumentationReviewDecision
import io.docpilot.core.incremental.specification.review.DocumentationReviewDisposition
import io.docpilot.core.incremental.specification.review.DocumentationReviewEntry
import io.docpilot.core.incremental.specification.review.DocumentationReviewProposal
import io.docpilot.core.incremental.specification.review.JsonReviewBundleCodec
import io.docpilot.core.model.ApiSpecification
import io.docpilot.core.model.ComponentSpecification
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.ProjectSpecification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultDocumentationReconcilerTest {
    private val reconciler = DefaultDocumentationReconciler()

    @Test
    fun `preview fails closed for unknown ownership and explains why`() {
        val input = input(base = null, current = "# User\n", candidate = "# Generated\n", manifest = null)

        val plan = reconciler.preview(request(input))

        assertTrue(!plan.applicable)
        assertEquals(ReconciliationConflictKind.UNKNOWN_OWNERSHIP, plan.conflicts.single().kind)
        assertEquals(
            listOf("UNKNOWN_PATH_HAS_NO_OWNERSHIP_EVIDENCE"),
            plan.explanationReport.explanations.single().ruleIds,
        )
        assertEquals(64, plan.planSha256.length)
        assertEquals(64, plan.explanationReport.reportSha256.length)
    }

    @Test
    fun `owned three way update requires decision and applies idempotently`() {
        val base = "# Base\n"
        val current = base
        val candidate = "# Generated v2\n"
        val manifest = manifest(base, DocumentationOwnership.DOCPILOT_OWNED)
        val plan = reconciler.preview(request(input(base, current, candidate, manifest)))
        val operation = plan.operations.single()
        val store = MemoryStore(mapOf(PATH to current))

        assertTrue(plan.applicable)
        assertEquals(ReconciliationOperationKind.UPDATE_OWNED_ARTIFACT, operation.kind)
        assertTrue(operation.requiresDecision)
        assertIs<ReconciliationApplyResult.Pending>(
            reconciler.apply(plan, emptyList(), emptyList(), store),
        )

        val decision = DocumentationReconciliationDecision(
            operation.operationId,
            ReconciliationDecisionDisposition.ACCEPT_GENERATED,
        )
        val applied = assertIs<ReconciliationApplyResult.Applied>(
            reconciler.apply(plan, listOf(decision), emptyList(), store),
        )
        assertEquals(candidate, store.read(PATH))
        assertEquals(applied.result.resultSha256, applied.result.explanationReport.resultSha256)
        assertIs<ReconciliationApplyResult.AlreadyApplied>(
            reconciler.apply(plan, listOf(decision), emptyList(), store),
        )
    }

    @Test
    fun `shared managed reconciliation preserves user bytes outside block`() {
        val base = "# User title\n\n${block("target", "old")}\n\nUser footer\n"
        val current = "# Custom user title\n\n${block("target", "old")}\n\nCustom footer\n"
        val candidate = "# Generated title\n\n${block("target", "new")}\n\nGenerated footer\n"
        val blockBaseSha = ReconciliationIntegrity.sha256("\nold\n")
        val manifest = manifest(base, DocumentationOwnership.SHARED_MANAGED).copy(
            managedBlocks = listOf(
                ManagedBlockOwnership("target", "target", blockBaseSha, blockBaseSha),
            ),
            manifestSha256 = "",
        ).let(ReconciliationIntegrity::signManifest)

        val plan = reconciler.preview(request(input(base, current, candidate, manifest)))
        val operation = plan.operations.single()

        assertEquals(ReconciliationOperationKind.UPDATE_MANAGED_BLOCKS, operation.kind)
        assertTrue(operation.resultContent!!.startsWith("# Custom user title"))
        assertTrue(operation.resultContent.contains("\nnew\n"))
        assertTrue(operation.resultContent.endsWith("Custom footer\n"))
        assertEquals(listOf("UNMANAGED_REGION_BYTE_PRESERVED"), operation.ruleIds)
        val resultBlock = operation.resultManifest!!.managedBlocks.single()
        assertEquals(ReconciliationIntegrity.sha256("\nnew\n"), resultBlock.reviewedBaseContentSha256)
        assertTrue(ReconciliationIntegrity.verifyManifest(operation.resultManifest))
    }

    @Test
    fun `overlapping owned edits become non applicable conflict`() {
        val base = "# Base\n"
        val plan = reconciler.preview(
            request(
                input(
                    base,
                    "# User edit\n",
                    "# Generated edit\n",
                    manifest(base, DocumentationOwnership.DOCPILOT_OWNED),
                ),
            ),
        )

        assertTrue(!plan.applicable)
        assertEquals(
            ReconciliationConflictKind.OVERLAPPING_USER_AND_GENERATED_EDIT,
            plan.conflicts.single().kind,
        )
    }

    @Test
    fun `apply rejects stale current content`() {
        val base = "# Base\n"
        val plan = reconciler.preview(
            request(input(base, base, "# New\n", manifest(base, DocumentationOwnership.DOCPILOT_OWNED))),
        )
        val store = MemoryStore(mapOf(PATH to "# Changed after preview\n"))
        val decision = DocumentationReconciliationDecision(
            plan.operations.single().operationId,
            ReconciliationDecisionDisposition.ACCEPT_GENERATED,
        )

        assertIs<ReconciliationApplyResult.Conflict>(
            reconciler.apply(plan, listOf(decision), emptyList(), store),
        )
    }

    @Test
    fun `apply rejects tampered result manifest and explanation report`() {
        val base = "# Base\n"
        val plan = reconciler.preview(
            request(input(base, base, "# New\n", manifest(base, DocumentationOwnership.DOCPILOT_OWNED))),
        )
        val operation = plan.operations.single()
        val decision = DocumentationReconciliationDecision(
            operation.operationId,
            ReconciliationDecisionDisposition.ACCEPT_GENERATED,
        )
        val tamperedManifest = plan.copy(
            operations = listOf(
                operation.copy(
                    resultManifest = operation.resultManifest!!.copy(rendererIdentity = "tampered"),
                ),
            ),
        )
        assertIs<ReconciliationApplyResult.Conflict>(
            reconciler.apply(tamperedManifest, listOf(decision), emptyList(), MemoryStore(mapOf(PATH to base))),
        )
        val tamperedExplanation = plan.copy(
            explanationReport = plan.explanationReport.copy(reportSha256 = "0".repeat(64)),
        )
        assertIs<ReconciliationApplyResult.Conflict>(
            reconciler.apply(tamperedExplanation, listOf(decision), emptyList(), MemoryStore(mapOf(PATH to base))),
        )
    }

    @Test
    fun `incremental plan excludes unchanged keep and includes drifted keep`() {
        val base = "# Base\n"
        val artifactPlan = DocumentationArtifactPlan(
            actions = listOf(
                DocumentationArtifactPlanAction(
                    DocumentationArtifactId("component:test"),
                    PATH,
                    DocumentationArtifactOperation.KEEP,
                    emptyList(),
                    emptyList(),
                ),
            ),
            orphanedArtifacts = emptyList(),
            planSha256 = "a".repeat(64),
        )
        val unchanged = reconciler.preview(
            request(input(base, base, base, manifest(base, DocumentationOwnership.DOCPILOT_OWNED)))
                .copy(artifactPlan = artifactPlan),
        )
        val drifted = reconciler.preview(
            request(input(base, "# User drift\n", base, manifest(base, DocumentationOwnership.DOCPILOT_OWNED)))
                .copy(artifactPlan = artifactPlan),
        )

        assertTrue(unchanged.operations.isEmpty())
        assertEquals(ReconciliationOperationKind.KEEP_USER_CONTENT, drifted.operations.single().kind)
    }

    @Test
    fun `managed block removal requires complete accepted RFC-0046 review bundle`() {
        val base = "# User\n\n${block("api:removed", "old")}\n"
        val blockSha = ReconciliationIntegrity.sha256("\nold\n")
        val shared = manifest(base, DocumentationOwnership.SHARED_MANAGED).copy(
            managedBlocks = listOf(ManagedBlockOwnership("api:removed", "api:removed", blockSha, blockSha)),
            manifestSha256 = "",
        ).let(ReconciliationIntegrity::signManifest)
        val withoutApproval = reconciler.preview(request(input(base, base, "# User\n\n", shared)))
        val codec = JsonReviewBundleCodec()
        val proposal = DocumentationReviewProposal(
            entries = listOf(
                DocumentationReviewEntry(
                    targetId = "api:removed",
                    parentId = "type:one",
                    target = IncrementalUpdateTarget.API,
                    specificationChangeKind = ChangeKind.REMOVED,
                    documentationChangeKind = DocumentationChangeKind.REMOVE,
                    operation = AiDocumentationPatchOperation.REMOVE,
                    existingMarkdown = "old",
                    proposedMarkdown = "",
                ),
            ),
            reviewedDocumentationSha256 = ReconciliationIntegrity.sha256(base),
        )
        val approved = codec.create(
            removalSpecification(true),
            removalSpecification(false),
            proposal,
            listOf(DocumentationReviewDecision("api:removed", DocumentationReviewDisposition.ACCEPTED)),
        )
        val withApproval = reconciler.preview(
            request(input(base, base, "# User\n\n", shared)).copy(removalReviewBundle = approved),
        )

        assertTrue(!withoutApproval.applicable)
        assertEquals(ReconciliationOperationKind.REMOVE_MANAGED_BLOCKS, withApproval.operations.single().kind)
    }

    private fun request(input: ReconciliationDocumentInput) =
        DocumentationReconciliationRequest("project:test", listOf(input))

    private fun input(
        base: String?,
        current: String?,
        candidate: String,
        manifest: DocumentationOwnershipManifest?,
    ) = ReconciliationDocumentInput(
        DocumentationArtifactId("component:test"),
        PATH,
        reviewedBase = base,
        current = current,
        candidate = candidate,
        manifest = manifest,
    )

    private fun manifest(base: String, ownership: DocumentationOwnership) =
        ReconciliationIntegrity.signManifest(
            DocumentationOwnershipManifest(
                artifactId = DocumentationArtifactId("component:test"),
                relativePath = PATH,
                mediaType = "text/markdown",
                ownership = ownership,
                reviewedBaseSha256 = ReconciliationIntegrity.sha256(base),
                rendererIdentity = "test-renderer",
                evidenceRefs = listOf("receipt:test"),
                manifestSha256 = "",
            ),
        )

    private fun block(id: String, body: String) =
        "<!-- DOCPILOT_AI_START id=$id -->\n$body\n<!-- DOCPILOT_AI_END id=$id -->"

    private fun removalSpecification(withApi: Boolean) = ProjectSpecification(
        schemaVersion = "0.3",
        project = ProjectDescriptor("project:test", "Test"),
        components = listOf(
            ComponentSpecification(
                id = "type:one",
                name = "One",
                moduleId = "module:main",
                kind = "class",
                role = "service",
                apis = if (withApi) listOf(
                    ApiSpecification("api:removed", "removed", "function", "removed(): Unit", "old"),
                ) else emptyList(),
            ),
        ),
    )

    private class MemoryStore(initial: Map<String, String>) : ReconciliationDocumentStore {
        private val documents = initial.toMutableMap()
        private val results = mutableMapOf<String, DocumentationReconciliationResult>()

        override fun read(relativePath: String): String? = documents[relativePath]

        override fun applyAtomically(
            expectedCurrentShaByPath: Map<String, String?>,
            documents: Map<String, String>,
            manifests: Map<String, DocumentationOwnershipManifest>,
            result: DocumentationReconciliationResult,
        ): Boolean {
            if (expectedCurrentShaByPath.any { (path, expected) ->
                    this.documents[path]?.let(ReconciliationIntegrity::sha256) != expected
                }
            ) return false
            this.documents.putAll(documents)
            results[result.planSha256] = result
            return true
        }

        override fun findResult(planSha256: String): DocumentationReconciliationResult? =
            results[planSha256]
    }

    private companion object {
        const val PATH = "docs/test.md"
    }
}

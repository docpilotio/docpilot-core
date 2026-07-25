package io.docpilot.core.incremental.specification.review

import io.docpilot.core.incremental.specification.ChangeKind
import io.docpilot.core.incremental.specification.IncrementalUpdateTarget
import io.docpilot.core.incremental.specification.ai.AiDocumentationPatchOperation
import io.docpilot.core.model.ComponentSpecification
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.ProjectSpecification
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class ReviewLifecycleOperationsTest {
    @TempDir
    lateinit var directory: Path

    private val bundleCodec = JsonReviewBundleCodec()
    private val lifecycleCodec = ReviewLifecycleCodec()

    @Test
    fun `status and offline verification expose Core classified aggregate`() {
        val fixture = fixture()
        val operations = fixture.operations()

        val status = assertIs<LifecycleStatusResult.Available>(
            operations.status(
                LifecycleStatusRequest("project", fixture.bundle.proposalId, fixture.document),
            ),
        ).status
        val verified = assertIs<LifecycleVerificationResult.Verified>(
            operations.verify(
                LifecycleStatusRequest("project", fixture.bundle.proposalId, fixture.document),
            ),
        ).status

        assertEquals(ReviewLifecycleState.ACTIVE, status.state)
        assertEquals(LifecycleDocumentationRelation.MATCHES_INPUT, status.documentationRelation)
        assertEquals(status, verified)
    }

    @Test
    fun `archive dry run performs no writes and confirm applies exact plan`() {
        val fixture = fixture()
        val operations = fixture.operations()
        val request = LifecycleOperationRequest("project", fixture.bundle.proposalId)
        val plan = assertIs<LifecycleOperationPlanResult.Ready>(
            operations.planArchive(request),
        ).plan
        val before = fixture.current()

        assertEquals(before, fixture.current())
        val changed = assertIs<LifecycleOperationResult.Changed>(
            operations.confirmArchive(ConfirmLifecycleOperationRequest(request, plan.planSha256)),
        )

        assertEquals(ReviewLifecycleState.ARCHIVED, changed.aggregate.metadata.state)
        assertEquals(before.generation + 1, changed.aggregate.metadata.generation)
    }

    @Test
    fun `stale plan is rejected after lifecycle generation changes`() {
        val fixture = fixture()
        val operations = fixture.operations()
        val request = LifecycleOperationRequest("project", fixture.bundle.proposalId)
        val plan = assertIs<LifecycleOperationPlanResult.Ready>(operations.planArchive(request)).plan
        val replacement = "review:${"a".repeat(64)}"
        assertIs<ReviewLifecycleResult.Success<ReviewLifecycleAggregate>>(
            ReviewLifecycleService(fixture.lifecycles).supersede(fixture.current(), replacement),
        )

        val result = assertIs<LifecycleOperationResult.Failure>(
            operations.confirmArchive(ConfirmLifecycleOperationRequest(request, plan.planSha256)),
        )

        assertEquals(LifecycleOperationFailure.CONFLICT, result.reason)
    }

    @Test
    fun `recovery dry run is side effect free and confirm rolls result forward`() {
        val fixture = fixture()
        val initial = fixture.current()
        val merged = "merged documentation"
        val resultSha = lifecycleCodec.sha256(merged)
        val receipt = lifecycleCodec.receipt(fixture.bundle, resultSha)
        val transaction = lifecycleCodec.transaction(
            fixture.bundle,
            initial.generation,
            resultSha,
            receipt.receiptId,
            ReviewApplyTransactionPhase.PREPARED,
        )
        assertIs<ReviewLifecycleResult.Success<ReviewLifecycleAggregate>>(
            fixture.lifecycles.beginApply(initial, transaction, receipt),
        )
        fixture.document.value = merged
        val applying = fixture.current()
        val request = LifecycleOperationRequest("project", fixture.bundle.proposalId, fixture.document)

        val plan = assertIs<LifecycleOperationPlanResult.Ready>(
            fixture.operations().planRecovery(request),
        ).plan
        assertEquals(RecoveryDisposition.ROLL_FORWARD_APPLIED, plan.recoveryDisposition)
        assertEquals(applying, fixture.current())

        val changed = assertIs<LifecycleOperationResult.Changed>(
            fixture.operations().confirmRecovery(ConfirmLifecycleOperationRequest(request, plan.planSha256)),
        )
        assertEquals(ReviewLifecycleState.APPLIED, changed.aggregate.metadata.state)
        assertEquals(receipt, changed.aggregate.receipt)
    }

    @Test
    fun `operation plan identity is deterministic and binds replacement proposal`() {
        val fixture = fixture()
        val replacement = "review:${"b".repeat(64)}"
        val request = LifecycleOperationRequest(
            "project",
            fixture.bundle.proposalId,
            replacementProposalId = replacement,
        )
        val first = assertIs<LifecycleOperationPlanResult.Ready>(
            fixture.operations().planSupersede(request),
        ).plan
        val second = assertIs<LifecycleOperationPlanResult.Ready>(
            fixture.operations().planSupersede(request),
        ).plan
        val other = assertIs<LifecycleOperationPlanResult.Ready>(
            fixture.operations().planSupersede(request.copy(replacementProposalId = "review:${"c".repeat(64)}")),
        ).plan

        assertEquals(first, second)
        assertNotEquals(first.planSha256, other.planSha256)
    }

    private fun fixture(): Fixture {
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
            reviewedDocumentationSha256 = lifecycleCodec.sha256("original documentation"),
        )
        val specification = ProjectSpecification(
            schemaVersion = "0.3",
            project = ProjectDescriptor("project", "Project"),
            components = listOf(ComponentSpecification("type:one", "One", "module:main", "class", "service")),
        )
        val undecided = bundleCodec.create(specification, specification, proposal)
        val bundle = bundleCodec.withDecisions(
            undecided,
            listOf(DocumentationReviewDecision("api:removed", DocumentationReviewDisposition.ACCEPTED)),
        )
        val bundles = FileReviewBundleRepository(directory, bundleCodec)
        assertIs<ReviewBundleSaveResult.Saved>(bundles.saveNew(bundle))
        val lifecycles = FileReviewLifecycleRepository(directory, lifecycleCodec)
        assertIs<ReviewLifecycleResult.Success<ReviewLifecycleAggregate>>(lifecycles.initialize(bundle))
        return Fixture(bundle, bundles, lifecycles, MemoryDocumentationResource("original documentation"))
    }

    private data class Fixture(
        val bundle: StoredReviewBundle,
        val bundles: ReviewBundleRepository,
        val lifecycles: ReviewLifecycleRepository,
        val document: MemoryDocumentationResource,
    ) {
        fun operations() = DefaultReviewLifecycleOperations(bundles, lifecycles)
        fun current() = assertIs<ReviewLifecycleResult.Success<ReviewLifecycleAggregate>>(
            lifecycles.load("project", bundle.proposalId),
        ).value.metadata
    }

    private class MemoryDocumentationResource(var value: String) : DocumentationResource {
        override fun read(): String = value
        override fun replace(expectedCurrent: String, replacement: String) {
            require(value == expectedCurrent)
            value = replacement
        }
    }
}

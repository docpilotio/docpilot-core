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
import kotlin.test.assertTrue

class ReviewLifecycleApplyWorkflowTest {
    @TempDir
    lateinit var directory: Path

    private val bundleCodec = JsonReviewBundleCodec()
    private val lifecycleCodec = ReviewLifecycleCodec()

    @Test
    fun `metadata receipt and transaction contracts round trip with integrity`() {
        val bundle = decidedBundle()
        val metadata = lifecycleCodec.createActive(bundle)
        val receipt = lifecycleCodec.receipt(bundle, lifecycleCodec.sha256("result"))
        val transaction = lifecycleCodec.transaction(
            bundle, metadata.generation, receipt.resultDocumentationSha256, receipt.receiptId,
            ReviewApplyTransactionPhase.PREPARED,
        )

        assertEquals(metadata, lifecycleCodec.decodeMetadata(lifecycleCodec.encode(metadata)))
        assertEquals(receipt, lifecycleCodec.decodeReceipt(lifecycleCodec.encode(receipt)))
        assertEquals(transaction, lifecycleCodec.decodeTransaction(lifecycleCodec.encode(transaction)))
    }

    @Test
    fun `apply commits document and receipt as an applied generation and retry is idempotent`() {
        val bundle = decidedBundle()
        val bundles = FileReviewBundleRepository(directory, bundleCodec)
        assertIs<ReviewBundleSaveResult.Saved>(bundles.saveNew(bundle))
        val lifecycles = FileReviewLifecycleRepository(directory, lifecycleCodec)
        val initial = assertIs<ReviewLifecycleResult.Success<ReviewLifecycleAggregate>>(
            lifecycles.initialize(bundle),
        ).value
        val document = MemoryDocumentationResource(existingDocumentation())
        val workflow = ReviewLifecycleApplyWorkflow(bundles, lifecycles, lifecycleCodec)

        val applied = assertIs<LifecycleApplyResult.Applied>(
            workflow.apply("project", bundle.proposalId, bundle.integrity.payloadSha256, initial.metadata.generation, document),
        )
        val stored = assertIs<ReviewLifecycleResult.Success<ReviewLifecycleAggregate>>(
            lifecycles.load("project", bundle.proposalId),
        ).value
        val retried = assertIs<LifecycleApplyResult.AlreadyApplied>(
            workflow.apply(
                "project", bundle.proposalId, bundle.integrity.payloadSha256,
                stored.metadata.generation, document,
            ),
        )

        assertEquals(ReviewLifecycleState.APPLIED, stored.metadata.state)
        assertEquals(applied.receipt, stored.receipt)
        assertEquals(applied.receipt, retried.receipt)
        assertTrue("api:removed" !in document.value)
    }

    @Test
    fun `applied receipt blocks retry after post apply document change`() {
        val bundle = decidedBundle()
        val bundles = FileReviewBundleRepository(directory, bundleCodec)
        bundles.saveNew(bundle)
        val lifecycles = FileReviewLifecycleRepository(directory, lifecycleCodec)
        val initial = assertIs<ReviewLifecycleResult.Success<ReviewLifecycleAggregate>>(lifecycles.initialize(bundle)).value
        val document = MemoryDocumentationResource(existingDocumentation())
        val workflow = ReviewLifecycleApplyWorkflow(bundles, lifecycles, lifecycleCodec)
        assertIs<LifecycleApplyResult.Applied>(
            workflow.apply("project", bundle.proposalId, bundle.integrity.payloadSha256, initial.metadata.generation, document),
        )
        document.value += "\nmanual change"
        val stored = assertIs<ReviewLifecycleResult.Success<ReviewLifecycleAggregate>>(
            lifecycles.load("project", bundle.proposalId),
        ).value

        assertIs<LifecycleApplyResult.Conflict>(
            workflow.apply("project", bundle.proposalId, bundle.integrity.payloadSha256, stored.metadata.generation, document),
        )
    }

    @Test
    fun `recovery rolls forward when document replacement completed before journal update`() {
        val bundle = decidedBundle()
        val bundles = FileReviewBundleRepository(directory, bundleCodec)
        bundles.saveNew(bundle)
        val lifecycles = FileReviewLifecycleRepository(directory, lifecycleCodec)
        val initial = assertIs<ReviewLifecycleResult.Success<ReviewLifecycleAggregate>>(lifecycles.initialize(bundle)).value
        val document = FailingAfterReplaceResource(existingDocumentation())
        val workflow = ReviewLifecycleApplyWorkflow(bundles, lifecycles, lifecycleCodec)

        assertIs<LifecycleApplyResult.RecoveryRequired>(
            workflow.apply("project", bundle.proposalId, bundle.integrity.payloadSha256, initial.metadata.generation, document),
        )
        val recovered = assertIs<LifecycleApplyResult.Applied>(
            workflow.recover("project", bundle.proposalId, document),
        )

        assertEquals(lifecycleCodec.sha256(document.value), recovered.receipt.resultDocumentationSha256)
        assertEquals(
            ReviewLifecycleState.APPLIED,
            assertIs<ReviewLifecycleResult.Success<ReviewLifecycleAggregate>>(
                lifecycles.load("project", bundle.proposalId),
            ).value.metadata.state,
        )
    }

    private fun decidedBundle(): StoredReviewBundle {
        val proposal = DocumentationReviewProposal(
            entries = listOf(
                DocumentationReviewEntry(
                    targetId = "api:removed",
                    parentId = "type:one",
                    target = IncrementalUpdateTarget.API,
                    specificationChangeKind = ChangeKind.REMOVED,
                    documentationChangeKind = DocumentationChangeKind.REMOVE,
                    operation = AiDocumentationPatchOperation.REMOVE,
                    existingMarkdown = "Old generated behavior.",
                    proposedMarkdown = "",
                    evidenceIds = listOf("evidence:removed"),
                ),
            ),
            reviewedDocumentationSha256 = lifecycleCodec.sha256(existingDocumentation()),
        )
        val specification = ProjectSpecification(
            schemaVersion = "0.3",
            project = ProjectDescriptor("project", "Project"),
            components = listOf(ComponentSpecification("type:one", "One", "module:main", "class", "service")),
        )
        val original = bundleCodec.create(specification, specification, proposal)
        return bundleCodec.withDecisions(
            original,
            listOf(DocumentationReviewDecision("api:removed", DocumentationReviewDisposition.ACCEPTED)),
        )
    }

    private fun existingDocumentation() = """
        # Project

        ## AI Incremental Documentation

        <!-- DOCPILOT_AI_START id=api:removed -->
        Old generated behavior.
        <!-- DOCPILOT_AI_END id=api:removed -->
    """.trimIndent()

    private class MemoryDocumentationResource(var value: String) : DocumentationResource {
        override fun read(): String = value
        override fun replace(expectedCurrent: String, replacement: String) {
            require(value == expectedCurrent)
            value = replacement
        }
    }

    private class FailingAfterReplaceResource(var value: String) : DocumentationResource {
        override fun read(): String = value
        override fun replace(expectedCurrent: String, replacement: String) {
            require(value == expectedCurrent)
            value = replacement
            error("simulated crash after replacement")
        }
    }
}

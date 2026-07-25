package io.docpilot.core.reconciliation

import io.docpilot.core.api.DocumentationArtifactId
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileReconciliationDocumentStoreTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `file store persists document manifest and idempotent result across restart`() {
        val base = "# Base\n"
        Files.createDirectories(root.resolve("docs"))
        Files.writeString(root.resolve(PATH), base)
        val (plan, decision) = plan(base)
        val store = FileReconciliationDocumentStore(root)

        val applied = assertIs<ReconciliationApplyResult.Applied>(
            DefaultDocumentationReconciler().apply(plan, listOf(decision), emptyList(), store),
        )
        val restarted = FileReconciliationDocumentStore(root)

        assertEquals("# New\n", restarted.read(PATH))
        assertEquals(plan, restarted.findPlan(plan.planSha256))
        assertNotNull(restarted.readManifest(ID.value))
        assertEquals(applied.result, restarted.findResult(plan.planSha256))
        assertTrue(
            ReconciliationVerifier().verifyOffline(
                applied.result,
                mapOf(PATH to restarted.read(PATH)!!),
                listOf(restarted.readManifest(ID.value)!!),
            ),
        )
    }

    @Test
    fun `prepared journal rolls forward after every visible write phase crash`() {
        ReconciliationTransactionPhase.entries.forEach { crashPhase ->
            val fixture = root.resolve(crashPhase.name.lowercase())
            val base = "# Base\n"
            Files.createDirectories(fixture.resolve("docs"))
            Files.writeString(fixture.resolve(PATH), base)
            val (plan, decision) = plan(base)
            val crashing = FileReconciliationDocumentStore(
                fixture,
                failureInjector = ReconciliationFailureInjector { phase ->
                    if (phase == crashPhase) error("simulated crash")
                },
            )

            assertFailsWith<IllegalStateException> {
                DefaultDocumentationReconciler().apply(plan, listOf(decision), emptyList(), crashing)
            }
            val restarted = FileReconciliationDocumentStore(fixture)
            val recovery = restarted.recover().single()

            assertEquals(ReconciliationRecoveryStatus.RECOVERED, recovery.status)
            assertEquals("# New\n", restarted.read(PATH))
            assertNotNull(restarted.readManifest(ID.value))
            assertNotNull(restarted.findResult(plan.planSha256))
            assertTrue(restarted.recover().isEmpty())
        }
    }

    @Test
    fun `codec rejects tampered manifest and result`() {
        val base = "# Base\n"
        val (plan, decision) = plan(base)
        Files.createDirectories(root.resolve("docs"))
        Files.writeString(root.resolve(PATH), base)
        val store = FileReconciliationDocumentStore(root)
        val result = assertIs<ReconciliationApplyResult.Applied>(
            DefaultDocumentationReconciler().apply(plan, listOf(decision), emptyList(), store),
        ).result
        val codec = ReconciliationCodec()
        val manifest = store.readManifest(ID.value)!!

        assertFailsWith<IllegalArgumentException> {
            codec.decodeManifest(codec.encodeManifest(manifest).replace("sha|", "artifact|"))
        }
        assertFailsWith<IllegalArgumentException> {
            val encoded = codec.encodeResult(result)
            codec.decodeResult(encoded.dropLast(2) + (if (encoded[encoded.lastIndex - 1] == 'A') "B" else "A") + "\n")
        }
    }

    private fun plan(base: String): Pair<DocumentationReconciliationPlan, DocumentationReconciliationDecision> {
        val manifest = ReconciliationIntegrity.signManifest(
            DocumentationOwnershipManifest(
                artifactId = ID,
                relativePath = PATH,
                mediaType = "text/markdown",
                ownership = DocumentationOwnership.DOCPILOT_OWNED,
                reviewedBaseSha256 = ReconciliationIntegrity.sha256(base),
                rendererIdentity = "test",
                evidenceRefs = listOf("review:test"),
                manifestSha256 = "",
            ),
        )
        val plan = DefaultDocumentationReconciler().preview(
            DocumentationReconciliationRequest(
                "project:test",
                listOf(ReconciliationDocumentInput(ID, PATH, reviewedBase = base, current = base,
                    candidate = "# New\n", manifest = manifest)),
            ),
        )
        return plan to DocumentationReconciliationDecision(
            plan.operations.single().operationId,
            ReconciliationDecisionDisposition.ACCEPT_GENERATED,
        )
    }

    private companion object {
        val ID = DocumentationArtifactId("component:test")
        const val PATH = "docs/test.md"
    }
}

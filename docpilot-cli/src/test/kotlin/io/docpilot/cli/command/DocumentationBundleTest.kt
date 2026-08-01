package io.docpilot.cli.command

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DocumentationBundleTest {
    @Test fun `canonical artifact ordering is permutation independent`() {
        val a = artifact("a", "a.md", "a")
        val b = artifact("b", "b.md", "b")
        assertEquals(bundle(listOf(a, b)).manifestSha256, bundle(listOf(b, a)).manifestSha256)
    }

    @Test fun `offline verifier detects changed and missing artifacts`() {
        val root = Files.createTempDirectory("bundle-test")
        root.resolve("a.md").writeText("a")
        val value = bundle(listOf(artifact("a", "a.md", "a")))
        val manifest = root.resolve(DocumentationBundleFormat.MANIFEST_PATH)
        Files.createDirectories(manifest.parent); manifest.writeText(DocumentationBundleCodec.encode(value))
        root.resolve(DocumentationBundleFormat.RECEIPT_PATH).writeText(DocumentationBundleCodec.encodeReceipt(value))
        assertEquals(BundleVerificationStatus.VALID, DocumentationBundleVerifier.verify(root).status)
        root.resolve("a.md").writeText("changed")
        assertEquals(BundleVerificationStatus.TAMPERED, DocumentationBundleVerifier.verify(root).status)
        Files.delete(root.resolve("a.md"))
        assertEquals(BundleVerificationStatus.INCOMPLETE, DocumentationBundleVerifier.verify(root).status)
    }

    @Test fun `time and output root cannot affect semantic identities`() {
        val first = bundle(listOf(artifact("a", "a.md", "a")))
        Thread.sleep(2)
        val second = bundle(listOf(artifact("a", "a.md", "a")))
        assertEquals(first.bundleId, second.bundleId); assertEquals(first.receiptId, second.receiptId)
        assertEquals(first.manifestSha256, second.manifestSha256)
        assertNotEquals(first.bundleId, bundle(listOf(artifact("a", "a.md", "a")), profile = "other@1").bundleId)
    }

    @Test fun `receipt tampering and broken fragments fail closed`() {
        val root = Files.createTempDirectory("bundle-integrity")
        root.resolve("a.md").writeText("# A\n\n[bad](#missing)\n")
        val value = bundle(listOf(artifact("a", "a.md", "# A\n\n[bad](#missing)\n")))
        val manifest = root.resolve(DocumentationBundleFormat.MANIFEST_PATH)
        Files.createDirectories(manifest.parent)
        manifest.writeText(DocumentationBundleCodec.encode(value))
        val receipt = root.resolve(DocumentationBundleFormat.RECEIPT_PATH)
        receipt.writeText(DocumentationBundleCodec.encodeReceipt(value))
        val broken = DocumentationBundleVerifier.verify(root)
        assertEquals(BundleVerificationStatus.INVALID, broken.status)
        assertEquals(1, broken.brokenLinks)
        root.resolve("a.md").writeText("# A\n")
        receipt.writeText(DocumentationBundleCodec.encodeReceipt(value).replace("\"projectId\":\"project\"", "\"projectId\":\"tampered\""))
        assertTrue(DocumentationBundleVerifier.verify(root).status != BundleVerificationStatus.VALID)
    }

    private fun artifact(id: String, path: String, body: String) = BundleArtifact(id, "PROJECT_OVERVIEW", path,
        DocumentationBundleCodec.sha256(body), body.toByteArray().size.toLong(), "CREATE")
    private fun bundle(artifacts: List<BundleArtifact>, profile: String = "kotlin-android@1") = DocumentationBundleCodec.create(
        "project", "spec:1", 3, "0.5", "1".repeat(64), profile, "2".repeat(64), "3".repeat(64), "APPLY", artifacts, "VALID")
}

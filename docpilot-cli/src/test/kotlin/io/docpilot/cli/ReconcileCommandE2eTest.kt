package io.docpilot.cli

import io.docpilot.core.api.DocumentationArtifactId
import io.docpilot.core.reconciliation.DocumentationOwnership
import io.docpilot.core.reconciliation.DocumentationOwnershipManifest
import io.docpilot.core.reconciliation.ManagedBlockOwnership
import io.docpilot.core.reconciliation.ReconciliationCodec
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReconcileCommandE2eTest {
    @Test
    fun `official CLI previews applies preserves user bytes and verifies offline`() {
        val project = Files.createTempDirectory("docpilot-reconcile-e2e")
        val docs = Files.createDirectories(project.resolve("docs"))
        val current = "# Custom user title\n\n${block("target", "old")}\n\nCustom footer\n"
        val base = "# User title\n\n${block("target", "old")}\n\nUser footer\n"
        val candidate = "# Generated title\n\n${block("target", "new")}\n\nGenerated footer\n"
        val document = docs.resolve("design.md")
        val baseFile = project.resolve("base.md")
        val candidateFile = project.resolve("candidate.md")
        val manifestFile = project.resolve("manifest.reconciliation")
        val planFile = project.resolve("plan.reconciliation")
        Files.writeString(document, current)
        Files.writeString(baseFile, base)
        Files.writeString(candidateFile, candidate)
        val blockSha = sha("\nold\n")
        val unsigned = DocumentationOwnershipManifest(
            artifactId = DocumentationArtifactId("artifact:design"),
            relativePath = "docs/design.md",
            mediaType = "text/markdown",
            ownership = DocumentationOwnership.SHARED_MANAGED,
            reviewedBaseSha256 = sha(base),
            managedBlocks = listOf(ManagedBlockOwnership("target", "target", blockSha, blockSha)),
            rendererIdentity = "e2e",
            evidenceRefs = listOf("evidence:e2e"),
            manifestSha256 = "",
        )
        val manifest = sign(unsigned)
        Files.writeString(manifestFile, ReconciliationCodec().encodeManifest(manifest))

        val preview = runCli(
            listOf(
                "reconcile", "preview", "--project", project.toString(), "--project-id", "project:e2e",
                "--artifact-id", "artifact:design", "--path", "docs/design.md", "--base", baseFile.toString(),
                "--candidate", candidateFile.toString(), "--manifest", manifestFile.toString(),
                "--plan", planFile.toString(),
            ),
        )
        assertEquals(0, preview)
        val plan = ReconciliationCodec().decodePlan(Files.readString(planFile))
        assertEquals(
            0,
            runCli(
                listOf(
                    "reconcile", "apply", "--project", project.toString(), "--plan", planFile.toString(),
                    "--decision", "accept-generated",
                ),
            ),
        )

        val result = Files.readString(document)
        assertTrue(result.startsWith("# Custom user title"))
        assertTrue(result.contains("\nnew\n"))
        assertTrue(result.endsWith("Custom footer\n"))
        assertEquals(
            0,
            runCli(
                listOf(
                    "reconcile", "verify", "--project", project.toString(),
                    "--plan-sha256", plan.planSha256,
                ),
            ),
        )
        assertEquals(0, runCli(listOf("reconcile", "recover", "--project", project.toString())))
    }

    private fun block(id: String, value: String): String =
        "<!-- DOCPILOT_AI_START id=$id -->\n$value\n<!-- DOCPILOT_AI_END id=$id -->"

    private fun sha(value: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun sign(value: DocumentationOwnershipManifest): DocumentationOwnershipManifest {
        val payload = buildString {
            append(value.formatVersion).append('|').append(value.artifactId.value).append('|')
            append(value.relativePath).append('|').append(value.mediaType).append('|')
            append(value.ownership.name).append('|').append(value.reviewedBaseSha256 ?: "").append('|')
            append(value.rendererIdentity).append('|')
            append(value.evidenceRefs.distinct().sorted().joinToString(",")).append('\n')
            value.managedBlocks.sortedBy { it.blockId }.forEach {
                append(it.blockId).append('|').append(it.targetId).append('|')
                    .append(it.reviewedBaseContentSha256).append('|')
                    .append(it.lastAppliedContentSha256).append('\n')
            }
        }
        return value.copy(manifestSha256 = sha(payload))
    }
}

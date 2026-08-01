package io.docpilot.cli.command

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

internal object DocumentationBundleFormat {
    const val VERSION = 1
    const val MANIFEST_PATH = ".docpilot/documentation-bundle.json"
    const val RECEIPT_PATH = ".docpilot/documentation-generation-receipt.json"
}

internal data class BundleArtifact(
    val id: String, val type: String, val path: String, val contentSha256: String, val size: Long,
    val action: String, val dependencies: List<String> = emptyList(), val linkCount: Int = 0,
    val unresolvedLinkCount: Int = 0,
)

internal data class BundleData(
    val bundleId: String, val projectId: String, val specificationIdentity: String,
    val snapshotFormatVersion: Int, val dirVersion: String, val snapshotSha256: String,
    val profileId: String, val profileVersion: Int, val profileSha256: String,
    val planSha256: String, val generationMode: String, val artifacts: List<BundleArtifact>,
    val receiptId: String, val receiptSha256: String, val artifactAggregateSha256: String,
    val linkStatus: String, val aggregateSha256: String, val manifestSha256: String = "",
)

internal object DocumentationBundleCodec {
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    fun sha256(value: String) = sha256(value.toByteArray(StandardCharsets.UTF_8))

    fun create(projectId: String, specificationIdentity: String, snapshotFormat: Int, dirVersion: String,
        snapshotSha: String, profile: String, profileSha: String, planSha: String, mode: String,
        artifacts: List<BundleArtifact>, linkStatus: String): BundleData {
        val (profileId, profileVersion) = profile.split('@').let { it[0] to it[1].toInt() }
        val ordered = artifacts.sortedBy { it.id }
        require(ordered.map { it.id }.distinct().size == ordered.size) { "Duplicate Artifact ID." }
        require(ordered.map { it.path.lowercase() }.distinct().size == ordered.size) { "Duplicate or case-colliding Artifact path." }
        val bundleId = "documentation-bundle:" + sha256("bundle|1|$projectId|$profileId|$profileVersion")
        val artifactHash = sha256(ordered.joinToString("\n") { "${it.id}|${it.path}|${it.contentSha256}|${it.size}" })
        val receiptId = "documentation-receipt:" + sha256("receipt|1|$bundleId|$projectId|$profile|$planSha|$mode|$artifactHash|$linkStatus")
        val receiptSha = sha256("$receiptId|$bundleId|$projectId|$profile|$planSha|$mode|$artifactHash|$linkStatus")
        val aggregate = sha256("$bundleId|$projectId|$specificationIdentity|$snapshotSha|$profileSha|$planSha|$artifactHash|$receiptSha|$linkStatus")
        val unsigned = BundleData(bundleId, projectId, specificationIdentity, snapshotFormat, dirVersion, snapshotSha,
            profileId, profileVersion, profileSha, planSha, mode, ordered, receiptId, receiptSha, artifactHash, linkStatus, aggregate)
        return unsigned.copy(manifestSha256 = sha256(payload(unsigned)))
    }

    fun encode(value: BundleData): String = payload(value) + "\n"
    fun payload(v: BundleData): String = buildString {
        append('{'); field("formatVersion", "1", true); field("bundleId", q(v.bundleId)); field("projectId", q(v.projectId))
        field("specificationIdentity", q(v.specificationIdentity)); field("snapshotFormatVersion", v.snapshotFormatVersion.toString(), true)
        field("dirVersion", q(v.dirVersion)); field("snapshotSha256", q(v.snapshotSha256)); field("profileId", q(v.profileId))
        field("profileVersion", v.profileVersion.toString(), true); field("profileSha256", q(v.profileSha256)); field("artifactPlanSha256", q(v.planSha256))
        field("generationMode", q(v.generationMode)); field("artifacts", v.artifacts.joinToString(",", "[", "]") { a ->
            "{\"id\":${q(a.id)},\"type\":${q(a.type)},\"mediaType\":\"text/markdown\",\"path\":${q(a.path)},\"ownership\":\"DOCPILOT_OWNED\",\"contentSha256\":${q(a.contentSha256)},\"size\":${a.size},\"action\":${q(a.action)},\"dependencies\":[${a.dependencies.sorted().joinToString(",") { q(it) }}],\"linkCount\":${a.linkCount},\"unresolvedLinkCount\":${a.unresolvedLinkCount}}"
        }, true); field("receiptId", q(v.receiptId)); field("receiptSha256", q(v.receiptSha256)); field("artifactAggregateSha256", q(v.artifactAggregateSha256))
        field("linkStatus", q(v.linkStatus)); field("aggregateSha256", q(v.aggregateSha256)); field("manifestSha256", q(v.manifestSha256)); append('}')
    }
    private fun StringBuilder.field(name: String, value: String, raw: Boolean = false) { if (length > 1) append(','); append(q(name)).append(':'); append(if (raw) value else value) }
    private fun q(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}

internal enum class BundleVerificationStatus { VALID, INVALID, INCOMPLETE, UNSUPPORTED, TAMPERED }
internal data class BundleVerification(val status: BundleVerificationStatus, val diagnostics: List<String>, val missing: Int = 0, val changed: Int = 0, val brokenLinks: Int = 0) {
    val exitCode get() = when (status) { BundleVerificationStatus.VALID -> 0; BundleVerificationStatus.UNSUPPORTED -> 4; BundleVerificationStatus.INCOMPLETE -> 5; BundleVerificationStatus.TAMPERED -> 6; BundleVerificationStatus.INVALID -> 7 }
}

/** Offline, fail-closed verifier. It intentionally needs no source project or registry. */
internal object DocumentationBundleVerifier {
    fun verify(input: Path): BundleVerification {
        val manifest = if (Files.isDirectory(input)) input.resolve(DocumentationBundleFormat.MANIFEST_PATH) else input
        if (!Files.isRegularFile(manifest)) return BundleVerification(BundleVerificationStatus.INCOMPLETE, listOf("Manifest not found: $manifest"), missing = 1)
        val text = Files.readString(manifest, StandardCharsets.UTF_8)
        if (!text.endsWith("\n") || text.contains("\\\\")) return BundleVerification(BundleVerificationStatus.INVALID, listOf("Manifest is non-canonical or contains a Windows path."))
        fun str(name: String) = Regex("\\\"$name\\\":\\\"([^\\\"]*)\\\"").find(text)?.groupValues?.get(1)
        val version = Regex("\"formatVersion\":(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()
            ?: return BundleVerification(BundleVerificationStatus.INVALID, listOf("Malformed Manifest."))
        if (version != 1) return BundleVerification(BundleVerificationStatus.UNSUPPORTED, listOf("Unsupported Manifest format: $version"))
        val expected = str("manifestSha256") ?: return BundleVerification(BundleVerificationStatus.INVALID, listOf("Missing manifestSha256."))
        val canonicalWithoutNewline = text.dropLast(1)
        val unsigned = canonicalWithoutNewline.replace(Regex("\"manifestSha256\":\"[0-9a-f]{64}\""), "\"manifestSha256\":\"\"")
        if (DocumentationBundleCodec.sha256(unsigned) != expected) return BundleVerification(BundleVerificationStatus.TAMPERED, listOf("Manifest integrity mismatch."))
        val root = manifest.parent.parent
        val artifactRegex = Regex("\\{\"id\":\"([^\"]+)\",\"type\":\"([^\"]+)\",\"mediaType\":\"text/markdown\",\"path\":\"([^\"]+)\",\"ownership\":\"DOCPILOT_OWNED\",\"contentSha256\":\"([0-9a-f]{64})\",\"size\":(\\d+)")
        var missing = 0; var changed = 0; val diagnostics = mutableListOf<String>(); val ids = mutableSetOf<String>(); val paths = mutableSetOf<String>()
        artifactRegex.findAll(text).forEach { m ->
            val id = m.groupValues[1]; val relative = m.groupValues[3]; val hash = m.groupValues[4]; val size = m.groupValues[5].toLong()
            if (!ids.add(id) || !paths.add(relative.lowercase()) || relative.startsWith('/') || relative.contains("..") || Regex("^[A-Za-z]:").containsMatchIn(relative)) diagnostics += "Unsafe or duplicate Artifact: $id"
            val path = root.resolve(relative).normalize()
            if (!path.startsWith(root) || !Files.isRegularFile(path)) { missing++; diagnostics += "MISSING: $relative" }
            else { val bytes = Files.readAllBytes(path); if (bytes.size.toLong() != size || DocumentationBundleCodec.sha256(bytes) != hash) { changed++; diagnostics += "TAMPERED: $relative" } }
        }
        return when { diagnostics.any { it.startsWith("Unsafe") } -> BundleVerification(BundleVerificationStatus.INVALID, diagnostics, missing, changed)
            changed > 0 -> BundleVerification(BundleVerificationStatus.TAMPERED, diagnostics, missing, changed)
            missing > 0 -> BundleVerification(BundleVerificationStatus.INCOMPLETE, diagnostics, missing, changed)
            else -> BundleVerification(BundleVerificationStatus.VALID, emptyList()) }
    }
}

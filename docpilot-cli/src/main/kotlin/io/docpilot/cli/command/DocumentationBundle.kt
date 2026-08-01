package io.docpilot.cli.command

import io.docpilot.core.documentation.enrichment.DocumentationEnrichmentRecord
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
    val enrichments: List<DocumentationEnrichmentRecord> = emptyList(),
    val enrichmentReceiptSha256: String = DocumentationBundleCodec.sha256(""),
)

internal object DocumentationBundleCodec {
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    fun sha256(value: String) = sha256(value.toByteArray(StandardCharsets.UTF_8))

    fun create(projectId: String, specificationIdentity: String, snapshotFormat: Int, dirVersion: String,
        snapshotSha: String, profile: String, profileSha: String, planSha: String, mode: String,
        artifacts: List<BundleArtifact>, linkStatus: String, enrichments: List<DocumentationEnrichmentRecord> = emptyList()): BundleData {
        val (profileId, profileVersion) = profile.split('@').let { it[0] to it[1].toInt() }
        val ordered = artifacts.sortedBy { it.id }
        require(ordered.map { it.id }.distinct().size == ordered.size) { "Duplicate Artifact ID." }
        require(ordered.map { it.path.lowercase() }.distinct().size == ordered.size) { "Duplicate or case-colliding Artifact path." }
        val bundleId = "documentation-bundle:" + sha256("bundle|1|$projectId|$profileId|$profileVersion")
        val artifactHash = sha256(ordered.joinToString("\n") { "${it.id}|${it.path}|${it.contentSha256}|${it.size}" })
        val receiptId = "documentation-receipt:" + sha256("receipt|1|$bundleId|$projectId|$profile|$planSha|$mode|$artifactHash|$linkStatus")
        val receiptSha = sha256("$receiptId|$bundleId|$projectId|$profile|$planSha|$mode|$artifactHash|$linkStatus")
        val orderedEnrichments = enrichments.sortedBy { it.enrichmentStableId }
        val enrichmentHash = sha256(orderedEnrichments.joinToString("\n") { enrichmentPayload(it) })
        val aggregate = sha256("$bundleId|$projectId|$specificationIdentity|$snapshotSha|$profileSha|$planSha|$artifactHash|$receiptSha|$linkStatus|$enrichmentHash")
        val unsigned = BundleData(bundleId, projectId, specificationIdentity, snapshotFormat, dirVersion, snapshotSha,
            profileId, profileVersion, profileSha, planSha, mode, ordered, receiptId, receiptSha, artifactHash, linkStatus,
            aggregate, enrichments = orderedEnrichments, enrichmentReceiptSha256 = enrichmentHash)
        return unsigned.copy(manifestSha256 = sha256(payload(unsigned)))
    }

    fun encode(value: BundleData): String = payload(value) + "\n"
    fun encodeReceipt(bundle: BundleData): String =
        "{\"formatVersion\":1,\"receiptId\":\"${bundle.receiptId}\",\"bundleId\":\"${bundle.bundleId}\",\"projectId\":\"${bundle.projectId}\",\"profileId\":\"${bundle.profileId}\",\"profileVersion\":${bundle.profileVersion},\"artifactPlanSha256\":\"${bundle.planSha256}\",\"generationMode\":\"${bundle.generationMode}\",\"manifestSha256\":\"${bundle.manifestSha256}\",\"artifactAggregateSha256\":\"${bundle.artifactAggregateSha256}\",\"linkStatus\":\"${bundle.linkStatus}\",\"enrichmentReceiptSha256\":\"${bundle.enrichmentReceiptSha256}\",\"receiptSha256\":\"${bundle.receiptSha256}\"}\n"
    fun artifacts(encoded: String): List<BundleArtifact> {
        val pattern = Regex("\\{\\\"id\\\":\\\"([^\\\"]+)\\\",\\\"type\\\":\\\"([^\\\"]+)\\\",\\\"mediaType\\\":\\\"text/markdown\\\",\\\"path\\\":\\\"([^\\\"]+)\\\",\\\"ownership\\\":\\\"DOCPILOT_OWNED\\\",\\\"contentSha256\\\":\\\"([0-9a-f]{64})\\\",\\\"size\\\":(\\d+),\\\"action\\\":\\\"([^\\\"]+)\\\",\\\"dependencies\\\":\\[([^]]*)],\\\"linkCount\\\":(\\d+),\\\"unresolvedLinkCount\\\":(\\d+)")
        return pattern.findAll(encoded).map { m -> BundleArtifact(m.groupValues[1], m.groupValues[2], m.groupValues[3],
            m.groupValues[4], m.groupValues[5].toLong(), m.groupValues[6],
            Regex("\\\"([^\\\"]+)\\\"").findAll(m.groupValues[7]).map { it.groupValues[1] }.toList(),
            m.groupValues[8].toInt(), m.groupValues[9].toInt()) }.toList()
    }
    fun payload(v: BundleData): String = buildString {
        append('{'); field("formatVersion", "1", true); field("bundleId", q(v.bundleId)); field("projectId", q(v.projectId))
        field("specificationIdentity", q(v.specificationIdentity)); field("snapshotFormatVersion", v.snapshotFormatVersion.toString(), true)
        field("dirVersion", q(v.dirVersion)); field("snapshotSha256", q(v.snapshotSha256)); field("profileId", q(v.profileId))
        field("profileVersion", v.profileVersion.toString(), true); field("profileSha256", q(v.profileSha256)); field("artifactPlanSha256", q(v.planSha256))
        field("generationMode", q(v.generationMode)); field("artifacts", v.artifacts.joinToString(",", "[", "]") { a ->
            "{\"id\":${q(a.id)},\"type\":${q(a.type)},\"mediaType\":\"text/markdown\",\"path\":${q(a.path)},\"ownership\":\"DOCPILOT_OWNED\",\"contentSha256\":${q(a.contentSha256)},\"size\":${a.size},\"action\":${q(a.action)},\"dependencies\":[${a.dependencies.sorted().joinToString(",") { q(it) }}],\"linkCount\":${a.linkCount},\"unresolvedLinkCount\":${a.unresolvedLinkCount}}"
        }, true); field("receiptId", q(v.receiptId)); field("receiptSha256", q(v.receiptSha256)); field("artifactAggregateSha256", q(v.artifactAggregateSha256))
        field("linkStatus", q(v.linkStatus)); field("enrichments", v.enrichments.joinToString(",", "[", "]") { enrichmentJson(it) }, true)
        field("enrichmentReceiptSha256", q(v.enrichmentReceiptSha256)); field("aggregateSha256", q(v.aggregateSha256)); field("manifestSha256", q(v.manifestSha256)); append('}')
    }
    private fun StringBuilder.field(name: String, value: String, raw: Boolean = false) { if (length > 1) append(','); append(q(name)).append(':'); append(if (raw) value else value) }
    private fun enrichmentPayload(r: DocumentationEnrichmentRecord): String = listOf(r.enrichmentStableId, r.providerId.orEmpty(),
        r.model.orEmpty(), r.canonicalInputIdentity, r.promptTemplateIdentity, r.targetArtifactId, r.targetSectionId,
        r.narrativeSha256.orEmpty(), r.status.name, r.diagnostic.orEmpty()).joinToString("|")
    private fun enrichmentJson(r: DocumentationEnrichmentRecord): String =
        "{\"formatVersion\":${r.formatVersion},\"enrichmentStableId\":${q(r.enrichmentStableId)},\"providerId\":${r.providerId?.let(::q) ?: "null"},\"model\":${r.model?.let(::q) ?: "null"},\"canonicalInputIdentity\":${q(r.canonicalInputIdentity)},\"promptTemplateIdentity\":${q(r.promptTemplateIdentity)},\"promptTemplateVersion\":${r.promptTemplateVersion},\"targetArtifactId\":${q(r.targetArtifactId)},\"targetSectionId\":${q(r.targetSectionId)},\"sourceModelStableIds\":[${r.sourceModelStableIds.joinToString(",") { q(it) }}],\"evidenceRefs\":[${r.evidenceRefs.joinToString(",") { q(it) }}],\"unresolvedRefs\":[${r.unresolvedRefs.joinToString(",") { q(it) }}],\"narrativeSha256\":${r.narrativeSha256?.let(::q) ?: "null"},\"status\":${q(r.status.name)},\"providerInvoked\":${r.providerInvoked},\"cached\":${r.cached},\"diagnostic\":${r.diagnostic?.let(::q) ?: "null"}}"
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
        if (!text.endsWith("\n") || Regex("[A-Za-z]:\\\\\\\\").containsMatchIn(text)) return BundleVerification(BundleVerificationStatus.INVALID, listOf("Manifest is non-canonical or contains a Windows path."))
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
        val receipt = root.resolve(DocumentationBundleFormat.RECEIPT_PATH)
        if (!Files.isRegularFile(receipt)) { missing++; diagnostics += "MISSING: ${DocumentationBundleFormat.RECEIPT_PATH}" }
        else {
            val receiptText = Files.readString(receipt, StandardCharsets.UTF_8)
            fun field(name: String) = Regex("\\\"$name\\\":\\\"([^\\\"]*)\\\"").find(receiptText)?.groupValues?.get(1)
            val receiptVersion = Regex("\\\"formatVersion\\\":(\\d+)").find(receiptText)?.groupValues?.get(1)?.toIntOrNull()
            val profileVersion = Regex("\\\"profileVersion\\\":(\\d+)").find(receiptText)?.groupValues?.get(1)
            val profile = "${field("profileId")}@$profileVersion"
            val recomputed = DocumentationBundleCodec.sha256("${field("receiptId")}|${field("bundleId")}|${field("projectId")}|$profile|${field("artifactPlanSha256")}|${field("generationMode")}|${field("artifactAggregateSha256")}|${field("linkStatus")}")
            if (receiptVersion != 1 || field("receiptId") != str("receiptId") || field("bundleId") != str("bundleId") ||
                field("manifestSha256") != expected || field("receiptSha256") != str("receiptSha256") ||
                field("enrichmentReceiptSha256") != str("enrichmentReceiptSha256") || recomputed != field("receiptSha256")) {
                changed++; diagnostics += "TAMPERED: ${DocumentationBundleFormat.RECEIPT_PATH}"
            }
        }
        val indexed = artifactRegex.findAll(text).map { it.groupValues[3].lowercase() }.toSet()
        if (Files.isDirectory(root.resolve("docs"))) Files.walk(root.resolve("docs")).use { paths -> paths
            .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".md", true) }
            .map { root.relativize(it).toString().replace('\\', '/').lowercase() }
            .filter { it !in indexed }.forEach { diagnostics += "UNEXPECTED_MANAGED_FILE: $it" }
        }
        var brokenLinks = 0
        DocumentationBundleCodec.artifacts(text).forEach { artifact ->
            val source = root.resolve(artifact.path)
            if (Files.isRegularFile(source)) MarkdownResourceVerifier.verify(root, source).forEach {
                brokenLinks++; diagnostics += "BROKEN_LINK: ${artifact.path}: $it"
            }
        }
        return when { diagnostics.any { it.startsWith("Unsafe") } -> BundleVerification(BundleVerificationStatus.INVALID, diagnostics, missing, changed)
            diagnostics.any { it.startsWith("UNEXPECTED_MANAGED_FILE") } -> BundleVerification(BundleVerificationStatus.INVALID, diagnostics, missing, changed, brokenLinks)
            brokenLinks > 0 -> BundleVerification(BundleVerificationStatus.INVALID, diagnostics, missing, changed, brokenLinks)
            changed > 0 -> BundleVerification(BundleVerificationStatus.TAMPERED, diagnostics, missing, changed)
            missing > 0 -> BundleVerification(BundleVerificationStatus.INCOMPLETE, diagnostics, missing, changed)
            else -> BundleVerification(BundleVerificationStatus.VALID, emptyList()) }
    }
}

private object MarkdownResourceVerifier {
    fun verify(root: Path, source: Path): List<String> {
        val text = Files.readString(source, StandardCharsets.UTF_8)
        val visible = text.lineSequence().runningFold(false to "") { state, line ->
            val fence = if (Regex("^\\s*(```|~~~)").containsMatchIn(line)) !state.first else state.first
            fence to if (state.first || Regex("^\\s*(```|~~~)").containsMatchIn(line)) "" else line
        }.drop(1).joinToString("\n") { it.second }
        val failures = mutableListOf<String>()
        Regex("!?\\[[^]]*]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)").findAll(visible).forEach { match ->
            val destination = match.groupValues[1].removePrefix("<").removeSuffix(">")
            if (destination.startsWith("http://") || destination.startsWith("https://") || destination.startsWith("mailto:")) return@forEach
            val parts = destination.split('#', limit = 2)
            val target = if (parts[0].isEmpty()) source else source.parent.resolve(parts[0]).normalize()
            if (!target.startsWith(root) || !Files.isRegularFile(target)) failures += destination
            else if (parts.size == 2 && parts[1].isNotEmpty() && parts[1] !in headings(target)) failures += destination
        }
        return failures
    }

    private fun headings(path: Path): Set<String> = Files.readAllLines(path, StandardCharsets.UTF_8).mapNotNull { line ->
        Regex("^#{1,6}\\s+(.+?)\\s*#*\\s*$").matchEntire(line)?.groupValues?.get(1)?.lowercase()
            ?.replace(Regex("[^\\p{L}\\p{N} _-]"), "")?.trim()?.replace(Regex("[ _]+"), "-")
    }.toSet()
}

package io.docpilot.core.documentation.synthesis

import io.docpilot.core.api.AiProvider
import io.docpilot.core.documentation.enrichment.DocumentationEnrichmentStatus
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.ai.AiGenerationResult
import io.docpilot.core.model.ai.AiMessage
import io.docpilot.core.model.ai.AiMessageRole
import io.docpilot.core.model.ai.AiModelId
import io.docpilot.core.model.ai.AiRequest
import io.docpilot.core.specification.claim.ClaimEvidenceBinder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

public enum class DocumentationTier { CANONICAL, ADVISORY }

public data class SynthesisSource(
    public val artifactId: String,
    public val sourceKind: String,
    public val sourceModelStableIds: List<String> = emptyList(),
    public val evidenceRefs: List<String> = emptyList(),
    public val unresolvedRefs: List<String> = emptyList(),
) {
    init {
        require(artifactId.isNotBlank()) { "Synthesis source artifact id must not be blank." }
        require(sourceKind.isNotBlank()) { "Synthesis source kind must not be blank." }
    }
}

public data class SynthesisRequest(
    public val documentType: String,
    public val sources: List<SynthesisSource>,
    public val canonicalNarrative: String,
    public val providerId: String,
    public val model: String,
) {
    init {
        require(documentType.isNotBlank()) { "Synthesis document type must not be blank." }
        require(sources.size >= 2) { "Cross-artifact synthesis requires at least two sources." }
        require(sources.map { it.artifactId }.distinct().size == sources.size) { "Synthesis sources must have unique artifact ids." }
        require(canonicalNarrative.isNotBlank()) { "Synthesis canonical narrative must not be blank." }
    }
}

public data class SynthesisRecord(
    public val formatVersion: Int = 1,
    public val synthesisStableId: String,
    public val tier: DocumentationTier = DocumentationTier.ADVISORY,
    public val providerId: String?,
    public val model: String?,
    public val canonicalInputIdentity: String,
    public val promptTemplateIdentity: String,
    public val promptTemplateVersion: Int,
    public val documentType: String,
    public val sourceArtifactIds: List<String>,
    public val evidenceRefs: List<String>,
    public val unresolvedRefs: List<String>,
    public val contentSha256: String?,
    public val status: DocumentationEnrichmentStatus,
    public val providerInvoked: Boolean,
    public val cached: Boolean,
    public val diagnostic: String? = null,
)

public data class SynthesisResult(public val content: String?, public val record: SynthesisRecord)

public object SynthesisPrompt {
    public const val TEMPLATE_ID: String = "docpilot.documentation.cross-artifact-synthesis"
    public const val TEMPLATE_VERSION: Int = 1
    public val identity: String = "$TEMPLATE_ID@$TEMPLATE_VERSION"

    public fun canonicalInput(request: SynthesisRequest): String = buildString {
        appendLine("documentType=${request.documentType}")
        request.sources.sortedBy { it.artifactId }.forEach { source ->
            appendLine("source.artifactId=${source.artifactId}")
            appendLine("source.kind=${source.sourceKind}")
            appendLine("source.sourceModelStableIds=${source.sourceModelStableIds.sorted().joinToString(",")}")
            appendLine("source.evidenceRefs=${source.evidenceRefs.sorted().joinToString(",")}")
            appendLine("source.unresolvedRefs=${source.unresolvedRefs.sorted().joinToString(",")}")
        }
        append("canonicalNarrative=${request.canonicalNarrative.trim()}")
    }

    public fun render(request: SynthesisRequest): String = """
        Synthesize an Advisory-tier documentation draft of type '${request.documentType}' using only the
        canonical facts supplied below, drawn from multiple sources. Do not fabricate facts beyond what is
        supplied. Do not claim an unresolved item is resolved. This output is Advisory, not Canonical: it
        may be restructured as full Markdown (headings, lists) but must not assert Stable IDs, Evidence, or
        entities that are not present in the supplied canonical input.

        ${canonicalInput(request)}
    """.trimIndent()
}

/**
 * Bundles multiple canonical sources (Contracts/Features/Evidence) into a single AI request, producing
 * Advisory-tier documentation. Unlike `DocumentationEnrichmentEngine` (RFC-0070, one artifact+section per
 * request, patched into an existing Canonical document body), this produces a standalone document and is
 * not wired into any CLI command or the RFC-0069 Bundle/Manifest — see RFC-0079's "Out of scope".
 */
public class SynthesisEngine(
    private val provider: AiProvider,
    private val cache: MutableMap<String, SynthesisResult> = mutableMapOf(),
) {
    public fun synthesize(specification: ProjectSpecification, request: SynthesisRequest): SynthesisResult {
        require(request.providerId == provider.descriptor.id.value) { "Selected provider does not match the provider instance." }
        request.sources.forEach { source ->
            ClaimEvidenceBinder.resolveRefs(
                specification, source.evidenceRefs.toSet(), source.unresolvedRefs.toSet(),
                "Synthesis source ${source.artifactId}",
            )
        }

        val canonicalIdentity = sha256(SynthesisPrompt.canonicalInput(request))
        val identity = sha256(
            listOf(
                request.providerId, request.model, request.documentType, canonicalIdentity,
                SynthesisPrompt.identity, request.sources.map { it.artifactId }.sorted().joinToString(","),
            ).joinToString("|"),
        )
        cache[identity]?.let { cached ->
            return cached.copy(record = cached.record.copy(providerInvoked = false, cached = true))
        }

        val base = record(request, identity, canonicalIdentity)
        val generated = provider.generate(
            AiRequest(
                modelId = AiModelId(request.model),
                messages = listOf(
                    AiMessage(AiMessageRole.SYSTEM, "You synthesize bounded Advisory documentation from multiple canonical sources."),
                    AiMessage(AiMessageRole.USER, SynthesisPrompt.render(request)),
                ),
                temperature = 0.0,
                maxOutputTokens = 4000,
            ),
        )
        val result = when (generated) {
            is AiGenerationResult.Failure -> SynthesisResult(
                null,
                base.copy(status = DocumentationEnrichmentStatus.FALLBACK, diagnostic = sanitize(generated.error.message)),
            )
            is AiGenerationResult.Success -> {
                val content = generated.response.content.trim()
                val rejection = validate(content)
                if (rejection != null) {
                    SynthesisResult(null, base.copy(status = DocumentationEnrichmentStatus.REJECTED, diagnostic = rejection))
                } else {
                    SynthesisResult(content, base.copy(status = DocumentationEnrichmentStatus.APPLIED, contentSha256 = sha256(content)))
                }
            }
        }
        cache[identity] = result
        return result
    }

    private fun record(request: SynthesisRequest, identity: String, canonical: String) = SynthesisRecord(
        synthesisStableId = "documentation-synthesis:$identity",
        providerId = request.providerId,
        model = request.model,
        canonicalInputIdentity = canonical,
        promptTemplateIdentity = SynthesisPrompt.identity,
        promptTemplateVersion = SynthesisPrompt.TEMPLATE_VERSION,
        documentType = request.documentType,
        sourceArtifactIds = request.sources.map { it.artifactId }.sorted(),
        evidenceRefs = request.sources.flatMap { it.evidenceRefs }.distinct().sorted(),
        unresolvedRefs = request.sources.flatMap { it.unresolvedRefs }.distinct().sorted(),
        contentSha256 = null,
        status = DocumentationEnrichmentStatus.FAILED,
        providerInvoked = true,
        cached = false,
    )

    private fun validate(content: String): String? {
        if (content.isBlank()) return "Provider returned empty Advisory content."
        if (content.length > 20_000) return "Provider content exceeded 20000 characters."
        if (Regex("(?i)([a-z]:\\\\|/(?:users|home|etc)/|api[_-]?key|bearer\\s+[a-z0-9._-]+)").containsMatchIn(content)) {
            return "Path or secret-like content is not allowed."
        }
        return null
    }

    private fun sanitize(value: String): String = value
        .replace(Regex("(?i)(api[_-]?key|authorization|bearer)\\s*[:=]?\\s*[^\\s,;]+"), "$1=[REDACTED]")
        .replace(Regex("(?i)sk-[a-z0-9_-]+"), "[REDACTED]").take(500)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

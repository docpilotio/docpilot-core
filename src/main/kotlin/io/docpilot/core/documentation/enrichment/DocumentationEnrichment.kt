package io.docpilot.core.documentation.enrichment

import io.docpilot.core.api.AiProvider
import io.docpilot.core.model.ai.AiGenerationResult
import io.docpilot.core.model.ai.AiMessage
import io.docpilot.core.model.ai.AiMessageRole
import io.docpilot.core.model.ai.AiModelId
import io.docpilot.core.model.ai.AiRequest
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

public enum class DocumentationEnrichmentStatus { APPLIED, FAILED, FALLBACK, SKIPPED, NOT_APPLIED, STALE, REJECTED }

public data class DocumentationEnrichmentTarget(
    val artifactId: String,
    val sectionId: String,
    val canonicalDisplayName: String,
    val documentType: String,
    val sourceModelStableIds: List<String> = emptyList(),
    val evidenceRefs: List<String> = emptyList(),
    val unresolvedRefs: List<String> = emptyList(),
)

public data class DocumentationEnrichmentRequest(
    val target: DocumentationEnrichmentTarget,
    val canonicalNarrative: String,
    val providerId: String,
    val model: String,
)

public data class DocumentationEnrichmentRecord(
    val formatVersion: Int = 1,
    val enrichmentStableId: String,
    val providerId: String?,
    val model: String?,
    val canonicalInputIdentity: String,
    val promptTemplateIdentity: String,
    val promptTemplateVersion: Int,
    val targetArtifactId: String,
    val targetSectionId: String,
    val sourceModelStableIds: List<String>,
    val evidenceRefs: List<String>,
    val unresolvedRefs: List<String>,
    val narrativeSha256: String?,
    val status: DocumentationEnrichmentStatus,
    val providerInvoked: Boolean,
    val cached: Boolean,
    val diagnostic: String? = null,
)

public data class DocumentationEnrichmentResult(
    val narrative: String?,
    val record: DocumentationEnrichmentRecord,
)

public object DocumentationEnrichmentPrompt {
    public const val TEMPLATE_ID: String = "docpilot.documentation.section-narrative"
    public const val TEMPLATE_VERSION: Int = 1
    public val identity: String = "$TEMPLATE_ID@$TEMPLATE_VERSION"

    public fun canonicalInput(request: DocumentationEnrichmentRequest): String = buildString {
        appendLine("artifactId=${request.target.artifactId}")
        appendLine("sectionId=${request.target.sectionId}")
        appendLine("displayName=${request.target.canonicalDisplayName}")
        appendLine("documentType=${request.target.documentType}")
        appendLine("sourceModelStableIds=${request.target.sourceModelStableIds.sorted().joinToString(",")}")
        appendLine("evidenceRefs=${request.target.evidenceRefs.sorted().joinToString(",")}")
        appendLine("unresolvedRefs=${request.target.unresolvedRefs.sorted().joinToString(",")}")
        append("canonicalNarrative=${request.canonicalNarrative.trim()}")
    }

    public fun render(request: DocumentationEnrichmentRequest): String = """
        Enrich only the requested documentation narrative using only the supplied canonical facts.
        Do not change or restate stable IDs, Evidence, unresolved items, entities, relationships, or ownership.
        Do not claim an unresolved item is resolved. Return narrative plain text only; no heading, link, list, JSON, or code fence.

        ${canonicalInput(request)}
    """.trimIndent()
}

public class DocumentationEnrichmentEngine(
    private val provider: AiProvider,
    private val cache: MutableMap<String, DocumentationEnrichmentResult> = mutableMapOf(),
) {
    public fun enrich(request: DocumentationEnrichmentRequest): DocumentationEnrichmentResult {
        require(request.providerId == provider.descriptor.id.value) { "Selected provider does not match the provider instance." }
        require(request.target.sectionId in allowedSections(request.target.documentType)) {
            "Section '${request.target.sectionId}' is not enrichment-enabled for ${request.target.documentType}."
        }
        val canonicalIdentity = sha256(DocumentationEnrichmentPrompt.canonicalInput(request))
        val identity = sha256(listOf(request.providerId, request.model, canonicalIdentity,
            DocumentationEnrichmentPrompt.identity, request.target.artifactId, request.target.sectionId).joinToString("|"))
        cache[identity]?.let { cached ->
            return cached.copy(record = cached.record.copy(providerInvoked = false, cached = true))
        }
        val base = record(request, identity, canonicalIdentity)
        val generated = provider.generate(AiRequest(
            modelId = AiModelId(request.model),
            messages = listOf(AiMessage(AiMessageRole.SYSTEM, "You write bounded documentation narrative."),
                AiMessage(AiMessageRole.USER, DocumentationEnrichmentPrompt.render(request))),
            temperature = 0.0,
            maxOutputTokens = 500,
            metadata = mapOf("artifactId" to request.target.artifactId, "sectionId" to request.target.sectionId),
        ))
        val result = when (generated) {
            is AiGenerationResult.Failure -> DocumentationEnrichmentResult(null, base.copy(
                status = DocumentationEnrichmentStatus.FALLBACK,
                diagnostic = sanitize(generated.error.message),
            ))
            is AiGenerationResult.Success -> {
                val narrative = generated.response.content.trim()
                val rejection = validate(narrative, request)
                if (rejection != null) DocumentationEnrichmentResult(null, base.copy(
                    status = DocumentationEnrichmentStatus.REJECTED, diagnostic = rejection,
                )) else DocumentationEnrichmentResult(narrative, base.copy(
                    status = DocumentationEnrichmentStatus.APPLIED, narrativeSha256 = sha256(narrative),
                ))
            }
        }
        cache[identity] = result
        return result
    }

    private fun record(request: DocumentationEnrichmentRequest, identity: String, canonical: String) =
        DocumentationEnrichmentRecord(1, "documentation-enrichment:$identity", request.providerId, request.model,
            canonical, DocumentationEnrichmentPrompt.identity, DocumentationEnrichmentPrompt.TEMPLATE_VERSION,
            request.target.artifactId, request.target.sectionId, request.target.sourceModelStableIds.sorted(),
            request.target.evidenceRefs.sorted(), request.target.unresolvedRefs.sorted(), null,
            DocumentationEnrichmentStatus.FAILED, providerInvoked = true, cached = false)

    private fun validate(narrative: String, request: DocumentationEnrichmentRequest): String? {
        if (narrative.isBlank()) return "Provider returned an empty narrative."
        if (narrative.length > 4_000) return "Provider narrative exceeded 4000 characters."
        if (Regex("(?m)^\\s{0,3}(#|[-*+] |\\d+[.)] |```|~~~|>)").containsMatchIn(narrative)) return "Markdown structure is not allowed."
        if (Regex("!?\\[[^]]+]\\([^)]+\\)").containsMatchIn(narrative)) return "Links and resources are not allowed."
        if (Regex("(?i)(stable[ -]?id|evidence|unresolved)\\s*:").containsMatchIn(narrative)) return "Canonical fields cannot be redefined."
        if (Regex("(?i)([a-z]:\\\\|/(?:users|home|etc)/|api[_-]?key|bearer\\s+[a-z0-9._-]+)").containsMatchIn(narrative)) return "Path or secret-like content is not allowed."
        val protected = request.target.sourceModelStableIds + request.target.evidenceRefs + request.target.unresolvedRefs
        if (protected.any { it.isNotBlank() && narrative.contains(it) }) return "Canonical references cannot be restated."
        return null
    }

    private fun allowedSections(type: String): Set<String> = when (type) {
        "PROJECT_OVERVIEW" -> setOf("architecture-description")
        "FEATURE_CATALOG", "FEATURE_SPECIFICATION" -> setOf("feature-summary", "feature-description")
        "SCENARIO_DETAIL" -> setOf("scenario-flow")
        "CONTRACT_DETAIL" -> setOf("contract-description")
        else -> emptySet()
    }

    private fun sanitize(value: String): String = value
        .replace(Regex("(?i)(api[_-]?key|authorization|bearer)\\s*[:=]?\\s*[^\\s,;]+"), "$1=[REDACTED]")
        .replace(Regex("(?i)sk-[a-z0-9_-]+"), "[REDACTED]").take(500)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

public object DocumentationEnrichmentSections {
    public const val START: String = "<!-- docpilot:enrichment:start -->"
    public const val END: String = "<!-- docpilot:enrichment:end -->"

    public fun apply(canonical: String, narrative: String): String {
        require(!canonical.contains(START) && !canonical.contains(END)) { "Canonical document already contains enrichment markers." }
        val block = "$START\n\n## AI Enrichment\n\n$narrative\n\n$END\n"
        val insertion = Regex("(?m)^## (Evidence|Unresolved)\\s*$").find(canonical)?.range?.first ?: canonical.length
        val prefix = canonical.substring(0, insertion).trimEnd()
        val suffix = canonical.substring(insertion).trimStart()
        return if (suffix.isEmpty()) "$prefix\n\n$block" else "$prefix\n\n$block\n$suffix"
    }
}

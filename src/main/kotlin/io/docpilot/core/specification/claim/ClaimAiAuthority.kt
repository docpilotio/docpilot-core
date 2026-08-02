package io.docpilot.core.specification.claim

/**
 * AI-content boundary check for non-deterministic Claim origins, mirroring
 * `io.docpilot.core.documentation.enrichment.DocumentationEnrichmentEngine.validate()`: a nullable
 * rejection reason, non-throwing, so callers can inspect it standalone. [ClaimFactory] turns a
 * non-null result into a `require()` failure.
 */
public object ClaimAiAuthority {
    public fun reject(origin: ClaimOrigin, assertion: String, protectedStableIds: Set<String>): String? {
        if (origin == ClaimOrigin.DETERMINISTIC) return null
        if (protectedStableIds.any { it.isNotBlank() && assertion.contains(it) }) {
            return "Claim assertion cannot restate a canonical Stable ID or Evidence/Contract reference."
        }
        if (Regex("(?i)(stable[ -]?id|evidence|unresolved)\\s*:").containsMatchIn(assertion)) {
            return "Claim assertion cannot redefine canonical fields."
        }
        return null
    }
}

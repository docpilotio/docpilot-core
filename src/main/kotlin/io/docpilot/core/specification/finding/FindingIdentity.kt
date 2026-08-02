package io.docpilot.core.specification.finding

import io.docpilot.core.specification.claim.ClaimHashing

/**
 * Deterministic Stable ID scheme for [Finding], derived only from `(subjectStableId, category,
 * semanticKey)` — never from `severity` or `summary` — so an AI can re-judge severity or refine
 * wording without identity churn, mirroring `io.docpilot.core.specification.claim.ClaimIdentity`.
 */
public object FindingIdentity {
    public fun of(subjectStableId: String, category: String, semanticKey: String): String {
        val canonical = ClaimHashing.canonicalize(listOf(subjectStableId, category, semanticKey))
        return "finding:${ClaimHashing.sha256(canonical).take(32)}"
    }
}

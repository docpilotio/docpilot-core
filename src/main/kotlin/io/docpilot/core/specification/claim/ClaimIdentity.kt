package io.docpilot.core.specification.claim

/**
 * Deterministic Stable ID scheme for [Claim], mirroring [io.docpilot.core.specification.ContractIdentity].
 *
 * Identity is derived from the Claim's subject and semantic key only, never from [Claim.assertion] text —
 * a Claim's identity must survive narrative re-wording, the same way a Contract's `id` does not change
 * when its `displayName` changes. Drift in the underlying facts is what [ClaimTraceability] (staleness)
 * detects instead.
 */
public object ClaimIdentity {
    public fun of(subject: ClaimSubject, semanticKey: String): String {
        val canonical = ClaimHashing.canonicalize(listOf(subject.kind.name, subject.stableId, semanticKey))
        return "claim:${ClaimHashing.sha256(canonical).take(32)}"
    }
}

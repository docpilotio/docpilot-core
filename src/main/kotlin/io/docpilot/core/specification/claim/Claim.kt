package io.docpilot.core.specification.claim

@JvmInline
public value class ClaimId(public val value: String) {
    init {
        require(value.isNotBlank()) { "Claim id must not be blank." }
    }
}

public enum class ClaimSubjectKind { ENTITY, SECTION, DOCUMENT }

public data class ClaimSubject(
    public val kind: ClaimSubjectKind,
    public val stableId: String,
)

/**
 * DETERMINISTIC: produced by DocPilot core from DIR/Contract data, no human review required.
 * AI_NARRATIVE: RFC-0070 bounded narrative, may only bind to a Document Section.
 * AI_PATCH_PROPOSAL: RFC-0070 designated patch proposal, may bind to a Section or Document, never an Entity.
 */
public enum class ClaimOrigin { DETERMINISTIC, AI_NARRATIVE, AI_PATCH_PROPOSAL }

public data class Claim(
    public val formatVersion: Int = 1,
    public val id: ClaimId,
    public val subject: ClaimSubject,
    public val semanticKey: String,
    public val assertion: String,
    public val evidenceRefs: Set<String>,
    public val unresolvedRefs: Set<String> = emptySet(),
    public val origin: ClaimOrigin,
    public val assertionSha256: String,
    public val boundFactsSha256: String,
)

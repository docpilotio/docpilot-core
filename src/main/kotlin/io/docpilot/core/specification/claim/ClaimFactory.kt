package io.docpilot.core.specification.claim

import io.docpilot.core.model.ProjectSpecification

/**
 * The single fail-closed construction entry point for [Claim]. This is where "AI cannot create
 * canonical entities" and "AI cannot mutate Stable IDs" are enforced structurally, not just
 * content-checked: only [deterministic] may target [ClaimSubjectKind.ENTITY].
 */
public object ClaimFactory {
    public fun deterministic(
        specification: ProjectSpecification,
        subject: ClaimSubject,
        semanticKey: String,
        assertion: String,
        evidenceRefs: Set<String>,
        unresolvedRefs: Set<String> = emptySet(),
    ): Claim = build(specification, subject, semanticKey, assertion, evidenceRefs, unresolvedRefs, ClaimOrigin.DETERMINISTIC)

    public fun aiNarrative(
        specification: ProjectSpecification,
        subject: ClaimSubject,
        semanticKey: String,
        assertion: String,
        evidenceRefs: Set<String>,
        unresolvedRefs: Set<String> = emptySet(),
    ): Claim {
        require(subject.kind == ClaimSubjectKind.SECTION) { "AI narrative Claims may only bind to a Document Section." }
        return build(specification, subject, semanticKey, assertion, evidenceRefs, unresolvedRefs, ClaimOrigin.AI_NARRATIVE)
    }

    public fun aiPatchProposal(
        specification: ProjectSpecification,
        subject: ClaimSubject,
        semanticKey: String,
        assertion: String,
        evidenceRefs: Set<String>,
        unresolvedRefs: Set<String> = emptySet(),
    ): Claim {
        require(subject.kind != ClaimSubjectKind.ENTITY) { "AI patch-proposal Claims cannot bind directly to a canonical entity." }
        return build(specification, subject, semanticKey, assertion, evidenceRefs, unresolvedRefs, ClaimOrigin.AI_PATCH_PROPOSAL)
    }

    private fun build(
        specification: ProjectSpecification,
        subject: ClaimSubject,
        semanticKey: String,
        assertion: String,
        evidenceRefs: Set<String>,
        unresolvedRefs: Set<String>,
        origin: ClaimOrigin,
    ): Claim {
        require(subject.stableId.isNotBlank()) { "Claim subject stable ID must not be blank." }
        require(semanticKey.isNotBlank()) { "Claim semantic key must not be blank." }
        require(assertion.isNotBlank()) { "Claim assertion must not be blank." }
        ClaimEvidenceBinder.resolveRefs(specification, evidenceRefs, unresolvedRefs, "Claim")

        val protected = evidenceRefs + unresolvedRefs + subject.stableId
        val rejection = ClaimAiAuthority.reject(origin, assertion, protected)
        require(rejection == null) { rejection.orEmpty() }

        return Claim(
            id = ClaimId(ClaimIdentity.of(subject, semanticKey)),
            subject = subject,
            semanticKey = semanticKey,
            assertion = assertion,
            evidenceRefs = evidenceRefs,
            unresolvedRefs = unresolvedRefs,
            origin = origin,
            assertionSha256 = ClaimHashing.sha256(assertion),
            boundFactsSha256 = ClaimTraceability.boundFactsHash(specification, evidenceRefs),
        )
    }
}

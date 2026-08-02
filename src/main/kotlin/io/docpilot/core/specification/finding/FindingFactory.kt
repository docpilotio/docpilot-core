package io.docpilot.core.specification.finding

import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.specification.claim.ClaimAiAuthority
import io.docpilot.core.specification.claim.ClaimEvidenceBinder
import io.docpilot.core.specification.claim.ClaimOrigin

/**
 * The single fail-closed construction entry point for [Finding]. Reuses RFC-0072's Claim-Evidence
 * binding rather than reimplementing it: [ClaimEvidenceBinder.resolveRefs] validates `evidenceRefs`
 * against real Evidence/Contract, and [ClaimAiAuthority.reject] guards against an AI-authored
 * `summary` restating a canonical reference verbatim — the same mechanism that protects
 * `Claim.assertion`.
 */
public object FindingFactory {
    public fun create(
        specification: ProjectSpecification,
        subjectStableId: String,
        semanticKey: String,
        category: String,
        severity: FindingSeverity,
        summary: String,
        evidenceRefs: Set<String>,
        unresolvedRefs: Set<String> = emptySet(),
    ): Finding {
        require(subjectStableId.isNotBlank()) { "Finding subject stable ID must not be blank." }
        require(semanticKey.isNotBlank()) { "Finding semantic key must not be blank." }
        require(category.isNotBlank()) { "Finding category must not be blank." }
        require(summary.isNotBlank()) { "Finding summary must not be blank." }

        ClaimEvidenceBinder.resolveRefs(specification, evidenceRefs, unresolvedRefs, "Finding")

        val protected = evidenceRefs + unresolvedRefs + subjectStableId
        val rejection = ClaimAiAuthority.reject(ClaimOrigin.AI_NARRATIVE, summary, protected)
        require(rejection == null) { rejection.orEmpty() }

        return Finding(
            id = FindingId(FindingIdentity.of(subjectStableId, category, semanticKey)),
            subjectStableId = subjectStableId,
            semanticKey = semanticKey,
            category = category,
            severity = severity,
            summary = summary,
            evidenceRefs = evidenceRefs,
            unresolvedRefs = unresolvedRefs,
        )
    }
}

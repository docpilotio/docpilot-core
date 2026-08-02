package io.docpilot.core.specification.claim

import io.docpilot.core.model.EvidenceConfidence
import io.docpilot.core.model.ProjectSpecification

/**
 * Fail-closed resolution of Claim `evidenceRefs`/`unresolvedRefs` against a [ProjectSpecification]'s
 * canonical DIR Evidence and Contracts. This is the reusable binding point RFC-0078's Finding model
 * calls directly for its own evidenceRefs validation.
 *
 * Mirrors the referential-integrity idiom used by `ProjectSpecificationValidator`'s private DIR 0.5
 * `validateRefs` closure (throws via `require()`, no silent drop) rather than the nullable-rejection
 * style used for AI content moderation (see [ClaimAiAuthority]) — this is a structural/graph-integrity
 * check, not a content-moderation concern.
 */
public object ClaimEvidenceBinder {
    public fun unresolvedTargets(specification: ProjectSpecification, refs: Set<String>): Set<String> {
        val evidenceIds = specification.evidence.mapTo(mutableSetOf()) { it.id }
        val contractIds = specification.contracts.mapTo(mutableSetOf()) { it.id }
        return refs - evidenceIds - contractIds
    }

    public fun resolveRefs(
        specification: ProjectSpecification,
        evidenceRefs: Set<String>,
        unresolvedRefs: Set<String>,
        label: String,
    ) {
        require(evidenceRefs.isNotEmpty()) { "Every $label must reference Evidence or a Contract." }
        val missing = unresolvedTargets(specification, evidenceRefs)
        require(missing.isEmpty()) { "Every $label reference must resolve to existing Evidence or a Contract: $missing" }
        val evidenceById = specification.evidence.associateBy { it.id }
        require(evidenceRefs.any { evidenceById[it]?.confidence != EvidenceConfidence.LOW }) {
            "$label cannot rely only on low-confidence Evidence."
        }
        val unresolvedIds = specification.unresolved.mapTo(mutableSetOf()) { it.id }
        require(unresolvedRefs.all(unresolvedIds::contains)) { "Every $label unresolved reference must exist." }
    }
}

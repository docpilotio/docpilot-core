package io.docpilot.core.specification.claim

import io.docpilot.core.model.ContractSpecification
import io.docpilot.core.model.Evidence
import io.docpilot.core.model.ProjectSpecification

public enum class ClaimTraceabilityIssueReason { ORPHAN, STALE, BROKEN }

public data class ClaimTraceabilityIssue(
    public val claimId: ClaimId,
    public val reason: ClaimTraceabilityIssueReason,
    public val detail: String,
)

/**
 * Computes the content-identity hash a [Claim] binds against at construction time, and re-validates
 * Claims against a (possibly newer) [ProjectSpecification] to detect orphan, stale, and broken
 * traceability. This is a deterministic, callable checker — it is not wired into any CLI command.
 */
public object ClaimTraceability {
    public fun boundFactsHash(specification: ProjectSpecification, evidenceRefs: Set<String>): String {
        val evidenceById = specification.evidence.associateBy { it.id }
        val contractsById = specification.contracts.associateBy { it.id }
        val canonical = ClaimHashing.canonicalize(
            evidenceRefs.sorted().map { ref -> canonicalFact(ref, evidenceById[ref], contractsById[ref]) },
        )
        return ClaimHashing.sha256(canonical)
    }

    private fun canonicalFact(ref: String, evidence: Evidence?, contract: ContractSpecification?): String = when {
        evidence != null -> ClaimHashing.canonicalize(
            listOf(
                "EVIDENCE", evidence.id, evidence.type, evidence.file.orEmpty(), evidence.symbol.orEmpty(),
                evidence.lineStart?.toString().orEmpty(), evidence.lineEnd?.toString().orEmpty(),
                evidence.summary, evidence.confidence.name,
            ),
        )
        contract != null -> ClaimHashing.canonicalize(
            listOf("CONTRACT", contract.id, contract.semanticKey, contract.displayName, contract.kind.name, contract.role.name),
        )
        else -> ClaimHashing.canonicalize(listOf("MISSING", ref))
    }
}

public object ClaimTraceabilityChecker {
    public fun check(
        specification: ProjectSpecification,
        claims: List<Claim>,
        knownSectionStableIds: Set<String>? = null,
        knownDocumentArtifactIds: Set<String>? = null,
    ): List<ClaimTraceabilityIssue> {
        val entityIds = buildSet {
            add(specification.project.id)
            addAll(specification.modules.map { it.id })
            addAll(specification.packages.map { it.id })
            addAll(specification.components.map { it.id })
            addAll(specification.features.map { it.id })
            addAll(specification.entryPoints.map { it.id })
            addAll(specification.contracts.map { it.id })
        }
        val unresolvedIds = specification.unresolved.mapTo(mutableSetOf()) { it.id }

        return claims.mapNotNull { claim ->
            val orphan = when (claim.subject.kind) {
                ClaimSubjectKind.ENTITY -> claim.subject.stableId !in entityIds
                ClaimSubjectKind.SECTION -> knownSectionStableIds?.let { claim.subject.stableId !in it } ?: false
                ClaimSubjectKind.DOCUMENT -> knownDocumentArtifactIds?.let { claim.subject.stableId !in it } ?: false
            }
            if (orphan) {
                return@mapNotNull ClaimTraceabilityIssue(
                    claim.id, ClaimTraceabilityIssueReason.ORPHAN,
                    "Claim subject '${claim.subject.stableId}' no longer exists.",
                )
            }

            val missingEvidenceRefs = ClaimEvidenceBinder.unresolvedTargets(specification, claim.evidenceRefs)
            val missingUnresolvedRefs = claim.unresolvedRefs.filterNot(unresolvedIds::contains)
            if (missingEvidenceRefs.isNotEmpty() || missingUnresolvedRefs.isNotEmpty()) {
                return@mapNotNull ClaimTraceabilityIssue(
                    claim.id, ClaimTraceabilityIssueReason.BROKEN,
                    "Claim references no longer resolve: evidence=$missingEvidenceRefs, unresolved=$missingUnresolvedRefs.",
                )
            }

            val currentBoundFactsHash = ClaimTraceability.boundFactsHash(specification, claim.evidenceRefs)
            if (currentBoundFactsHash != claim.boundFactsSha256) {
                return@mapNotNull ClaimTraceabilityIssue(
                    claim.id, ClaimTraceabilityIssueReason.STALE,
                    "Claim's bound Evidence/Contract facts have changed since it was recorded.",
                )
            }

            null
        }
    }
}

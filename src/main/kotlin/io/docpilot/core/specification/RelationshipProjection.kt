package io.docpilot.core.specification

import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.RelationshipSpecification
import java.security.MessageDigest

public enum class SemanticRelationshipKind {
    DEPENDS_ON,
    EXTENDS,
    IMPLEMENTS,
    CALLS,
    IMPORTS,
}

public object RelationshipIdentity {
    public fun of(kind: String, sourceId: String, targetId: String): String {
        require(kind in SemanticRelationshipKind.entries.map { it.name }) {
            "Unsupported semantic relationship kind: $kind"
        }
        require(sourceId.isNotBlank() && targetId.isNotBlank()) {
            "Relationship endpoints must not be blank."
        }
        return "relationship:${frame(kind)}:${frame(sourceId)}:${frame(targetId)}"
    }

    private fun frame(value: String): String = "${value.toByteArray(Charsets.UTF_8).size}:$value"
}

public enum class RelationshipOverflowBehavior { TRUNCATE_WITH_REPORT, FAIL_CLOSED }

public data class RelationshipProjectionPolicy(
    public val formatVersion: Int = 1,
    public val policyId: String = "docpilot-default-v1",
    public val maxCallsPerSource: Int = 128,
    public val maxCallsPerProject: Int = 50_000,
    public val maxImportsPerSourcePackage: Int = 512,
    public val maxImportsPerProject: Int = 20_000,
    public val overflowBehavior: RelationshipOverflowBehavior =
        RelationshipOverflowBehavior.TRUNCATE_WITH_REPORT,
) {
    init {
        require(formatVersion == 1) { "Unsupported relationship projection policy format." }
        require(policyId.isNotBlank()) { "Relationship projection policy id must not be blank." }
        require(maxCallsPerSource > 0 && maxCallsPerProject > 0) { "CALLS limits must be positive." }
        require(maxImportsPerSourcePackage > 0 && maxImportsPerProject > 0) {
            "IMPORTS limits must be positive."
        }
    }
}

public data class RelationshipOverflowScope(
    public val kind: String,
    public val sourceId: String?,
    public val logicalCount: Int,
    public val emittedCount: Int,
)

public data class RelationshipProjectionReport(
    public val formatVersion: Int = 1,
    public val policyId: String,
    public val policySha256: String,
    public val logicalCountByKind: Map<String, Int>,
    public val emittedCountByKind: Map<String, Int>,
    public val omittedCountByKind: Map<String, Int>,
    public val aggregatedOccurrenceCountByKind: Map<String, Int>,
    public val overflowScopes: List<RelationshipOverflowScope>,
    public val omittedIdentitySha256ByKind: Map<String, String>,
    public val reportSha256: String,
)

public data class SpecificationBuildResult(
    public val specification: ProjectSpecification,
    public val relationshipProjectionReport: RelationshipProjectionReport,
)

internal data class RelationshipProjectionResult(
    val relationships: List<RelationshipSpecification>,
    val report: RelationshipProjectionReport,
)

internal object RelationshipProjector {
    fun project(
        candidates: List<RelationshipSpecification>,
        policy: RelationshipProjectionPolicy,
    ): RelationshipProjectionResult {
        val canonicalCandidates = candidates.map { relationship ->
            require(relationship.evidenceRefs.isNotEmpty()) {
                "Every semantic relationship requires Evidence: ${relationship.type}"
            }
            relationship.copy(
                id = RelationshipIdentity.of(
                    relationship.type,
                    relationship.sourceId,
                    relationship.targetId,
                ),
                evidenceRefs = relationship.evidenceRefs.toSortedSet(),
            )
        }
        val occurrencesByKind = canonicalCandidates.groupingBy { it.type }.eachCount()
        val aggregated = canonicalCandidates.groupBy { it.id }.values.map { sameIdentity ->
            val first = sameIdentity.first()
            require(sameIdentity.all {
                it.type == first.type && it.sourceId == first.sourceId && it.targetId == first.targetId
            }) { "Conflicting relationship observations for ${first.id}" }
            first.copy(evidenceRefs = sameIdentity.flatMap { it.evidenceRefs }.toSortedSet())
        }.sortedWith(order)

        val retained = mutableListOf<RelationshipSpecification>()
        val omitted = mutableListOf<RelationshipSpecification>()
        val overflow = mutableListOf<RelationshipOverflowScope>()
        SemanticRelationshipKind.entries.forEach { kind ->
            val values = aggregated.filter { it.type == kind.name }
            if (kind !in setOf(SemanticRelationshipKind.CALLS, SemanticRelationshipKind.IMPORTS)) {
                retained += values
            } else {
                val perSourceLimit = if (kind == SemanticRelationshipKind.CALLS) {
                    policy.maxCallsPerSource
                } else {
                    policy.maxImportsPerSourcePackage
                }
                val afterSource = values.groupBy { it.sourceId }.toSortedMap().flatMap { (source, group) ->
                    val sorted = group.sortedWith(order)
                    if (sorted.size > perSourceLimit) {
                        overflow += RelationshipOverflowScope(kind.name, source, sorted.size, perSourceLimit)
                        omitted += sorted.drop(perSourceLimit)
                    }
                    sorted.take(perSourceLimit)
                }.distinctBy { it.id }.sortedWith(order)
                val projectLimit = if (kind == SemanticRelationshipKind.CALLS) {
                    policy.maxCallsPerProject
                } else {
                    policy.maxImportsPerProject
                }
                if (afterSource.size > projectLimit) {
                    overflow += RelationshipOverflowScope(kind.name, null, afterSource.size, projectLimit)
                    omitted += afterSource.drop(projectLimit)
                }
                retained += afterSource.take(projectLimit)
            }
        }
        if (omitted.isNotEmpty() && policy.overflowBehavior == RelationshipOverflowBehavior.FAIL_CLOSED) {
            error("Relationship projection threshold exceeded: ${omitted.size} relationships omitted.")
        }
        val logicalByKind = aggregated.groupingBy { it.type }.eachCount().toSortedMap()
        val emittedByKind = retained.groupingBy { it.type }.eachCount().toSortedMap()
        val omittedDistinct = omitted.distinctBy { it.id }.sortedWith(order)
        val omittedByKind = omittedDistinct.groupingBy { it.type }.eachCount().toSortedMap()
        val aggregatedOccurrences = occurrencesByKind.mapValues { (kind, count) ->
            count - (logicalByKind[kind] ?: 0)
        }.filterValues { it > 0 }.toSortedMap()
        val policySha = digest(
            listOf(
                policy.formatVersion,
                policy.policyId,
                policy.maxCallsPerSource,
                policy.maxCallsPerProject,
                policy.maxImportsPerSourcePackage,
                policy.maxImportsPerProject,
                policy.overflowBehavior.name,
            ).joinToString("|"),
        )
        val omittedDigests = omittedDistinct.groupBy { it.type }.toSortedMap().mapValues { (_, values) ->
            digest(values.joinToString("\n") { it.id })
        }
        val reportBody = buildString {
            append(policy.policyId).append('|').append(policySha).append('\n')
            append(logicalByKind).append('\n').append(emittedByKind).append('\n')
            append(omittedByKind).append('\n').append(aggregatedOccurrences).append('\n')
            overflow.sortedWith(compareBy({ it.kind }, { it.sourceId ?: "" })).forEach { append(it).append('\n') }
            append(omittedDigests)
        }
        return RelationshipProjectionResult(
            relationships = retained.sortedWith(order),
            report = RelationshipProjectionReport(
                policyId = policy.policyId,
                policySha256 = policySha,
                logicalCountByKind = logicalByKind,
                emittedCountByKind = emittedByKind,
                omittedCountByKind = omittedByKind,
                aggregatedOccurrenceCountByKind = aggregatedOccurrences,
                overflowScopes = overflow.distinct().sortedWith(compareBy({ it.kind }, { it.sourceId ?: "" })),
                omittedIdentitySha256ByKind = omittedDigests,
                reportSha256 = digest(reportBody),
            ),
        )
    }

    private val order = compareBy<RelationshipSpecification>(
        { it.sourceId },
        { it.type },
        { endpointRank(it.targetId) },
        { it.targetId },
        { it.id },
    )

    private fun endpointRank(id: String): Int = when {
        id.startsWith(RelationshipEndpointSemantics.EXTERNAL_PREFIX) -> 1
        id.startsWith(RelationshipEndpointSemantics.UNRESOLVED_PREFIX) -> 2
        else -> 0
    }

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

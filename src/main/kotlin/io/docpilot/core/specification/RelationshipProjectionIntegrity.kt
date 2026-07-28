package io.docpilot.core.specification

import java.security.MessageDigest

/** Canonical integrity rules for RFC-0053 relationship projection Evidence. */
public object RelationshipProjectionIntegrity {
    public fun policySha256(policy: RelationshipProjectionPolicy): String = sha256(
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

    public fun reportSha256(report: RelationshipProjectionReport): String = sha256(reportPayload(report))

    public fun reportPayload(report: RelationshipProjectionReport): String = buildString {
        append(report.policyId).append('|').append(report.policySha256).append('\n')
        append(report.logicalCountByKind.toSortedMap()).append('\n')
        append(report.emittedCountByKind.toSortedMap()).append('\n')
        append(report.omittedCountByKind.toSortedMap()).append('\n')
        append(report.aggregatedOccurrenceCountByKind.toSortedMap()).append('\n')
        report.overflowScopes.sortedWith(compareBy({ it.kind }, { it.sourceId ?: "" })).forEach {
            append(it).append('\n')
        }
        append(report.omittedIdentitySha256ByKind.toSortedMap())
    }

    public fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

public class RelationshipProjectionVerifier {
    public fun verify(
        report: RelationshipProjectionReport,
        policy: RelationshipProjectionPolicy? = null,
    ): Boolean = runCatching {
        require(report.formatVersion == 1)
        require(report.policyId.isNotBlank())
        require(report.policySha256.matches(SHA))
        if (policy != null) {
            require(report.policyId == policy.policyId)
            require(report.policySha256 == RelationshipProjectionIntegrity.policySha256(policy))
        }
        val supportedKinds = SemanticRelationshipKind.entries.mapTo(hashSetOf()) { it.name }
        val maps = listOf(
            report.logicalCountByKind,
            report.emittedCountByKind,
            report.omittedCountByKind,
            report.aggregatedOccurrenceCountByKind,
        )
        require(maps.all { map -> map.keys.all(supportedKinds::contains) && map.values.all { it >= 0 } })
        supportedKinds.forEach { kind ->
            val logical = report.logicalCountByKind[kind] ?: 0
            val emitted = report.emittedCountByKind[kind] ?: 0
            val omitted = report.omittedCountByKind[kind] ?: 0
            require(logical == emitted + omitted)
        }
        require(report.overflowScopes == report.overflowScopes.distinct().sortedWith(compareBy({ it.kind }, { it.sourceId ?: "" })))
        require(report.overflowScopes.all {
            it.kind in supportedKinds && it.logicalCount >= it.emittedCount && it.emittedCount >= 0 &&
                (it.sourceId == null || it.sourceId.isNotBlank())
        })
        require(report.omittedIdentitySha256ByKind.keys.all(supportedKinds::contains))
        require(report.omittedIdentitySha256ByKind.values.all { it.matches(SHA) })
        require(report.reportSha256 == RelationshipProjectionIntegrity.reportSha256(report))
    }.isSuccess

    private companion object {
        val SHA = Regex("[0-9a-f]{64}")
    }
}

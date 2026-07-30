package io.docpilot.core.specification.discovery

import java.security.MessageDigest

public object FeatureDiscoveryIntegrity {
    public fun semanticHash(result: FeatureDiscoveryResult): String =
        hash(canonicalPayload(result))

    public fun requireValid(result: FeatureDiscoveryResult) {
        require(result.semanticHash == semanticHash(result)) {
            "Feature Discovery semantic integrity verification failed."
        }
    }

    internal fun canonicalPayload(result: FeatureDiscoveryResult): String = buildString {
        append("policy=").append(result.policyVersion).append('\n')
        result.entryPoints.sortedBy { it.id }.forEach {
            append("entry|").append(it.id).append('|').append(it.kind).append('|')
                .append(it.ownerComponentId).append('|').append(it.apiId.orEmpty()).append('|')
                .append(it.evidenceRefs.sorted().joinToString(",")).append('|')
                .append(it.unresolvedRefs.sorted().joinToString(",")).append('\n')
        }
        result.features.sortedBy { it.id }.forEach {
            append("feature|").append(it.id).append('|').append(it.ownerComponentId).append('|')
                .append(it.participantComponentIds.sorted().joinToString(",")).append('|')
                .append(it.entryPointIds.sorted().joinToString(",")).append('|')
                .append(it.scenarioIds.sorted().joinToString(",")).append('|')
                .append(it.evidenceRefs.sorted().joinToString(",")).append('|')
                .append(it.unresolvedRefs.sorted().joinToString(",")).append('\n')
        }
        result.scenarios.sortedBy { it.id }.forEach { scenario ->
            append("scenario|").append(scenario.id).append('|').append(scenario.featureId).append('|')
                .append(scenario.entryPointId.orEmpty()).append('|')
                .append(scenario.evidenceRefs.sorted().joinToString(",")).append('\n')
            scenario.steps.sortedWith(compareBy({ it.order }, { it.id })).forEach {
                append("step|").append(it.id).append('|').append(it.order).append('|')
                    .append(it.action).append('|').append(it.ownerComponentId).append('|')
                    .append(it.targetComponentId.orEmpty()).append('|').append(it.apiId.orEmpty()).append('|')
                    .append(it.evidenceRefs.sorted().joinToString(",")).append('\n')
            }
        }
        result.unresolved.sortedBy { it.id }.forEach {
            append("unresolved|").append(it.id).append('|').append(it.subject).append('|')
                .append(it.question).append('|').append(it.requiredAction.orEmpty()).append('\n')
        }
    }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

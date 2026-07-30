package io.docpilot.core.specification.discovery

import io.docpilot.core.model.EntryPointSpecification
import io.docpilot.core.model.FeatureSpecification
import io.docpilot.core.model.ScenarioSpecification
import io.docpilot.core.model.UnresolvedItem

public data class FeatureDiscoveryPolicy(
    public val version: String = "1",
    public val maximumTraversalDepth: Int = 4,
    public val maximumParticipantsPerFeature: Int = 32,
) {
    init {
        require(version.isNotBlank()) { "Discovery policy version must not be blank." }
        require(maximumTraversalDepth > 0) { "Maximum traversal depth must be positive." }
        require(maximumParticipantsPerFeature > 0) { "Maximum participant count must be positive." }
    }
}

public data class FeatureDiscoveryResult(
    public val policyVersion: String,
    public val features: List<FeatureSpecification>,
    public val entryPoints: List<EntryPointSpecification>,
    public val scenarios: List<ScenarioSpecification>,
    public val unresolved: List<UnresolvedItem>,
    public val semanticHash: String,
)

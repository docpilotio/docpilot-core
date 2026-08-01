package io.docpilot.core.incremental.specification

import io.docpilot.core.model.ApiSpecification
import io.docpilot.core.model.ComponentSpecification
import io.docpilot.core.model.PackageSpecification
import io.docpilot.core.model.PropertySpecification
import io.docpilot.core.model.RelationshipSpecification
import io.docpilot.core.model.FeatureSpecification
import io.docpilot.core.model.EntryPointSpecification
import io.docpilot.core.model.ScenarioSpecification
import io.docpilot.core.model.ScenarioStepSpecification
import io.docpilot.core.model.Evidence

/** A deterministic change entry identified by the entity's stable DIR id. */
public data class SpecificationChange<T>(
    public val id: String,
    public val parentId: String? = null,
    public val kind: ChangeKind,
    public val previous: T? = null,
    public val current: T? = null,
) {
    init {
        require(id.isNotBlank()) { "Change id must not be blank." }
        when (kind) {
            ChangeKind.ADDED -> require(previous == null && current != null) {
                "Added change must contain only the current value."
            }
            ChangeKind.REMOVED -> require(previous != null && current == null) {
                "Removed change must contain only the previous value."
            }
            ChangeKind.MODIFIED -> require(previous != null && current != null) {
                "Modified change must contain previous and current values."
            }
        }
    }
}

/** DIR-level diff used to plan selective documentation updates. */
public data class SpecificationDiff(
    public val packageChanges: List<SpecificationChange<PackageSpecification>> = emptyList(),
    public val typeChanges: List<SpecificationChange<ComponentSpecification>> = emptyList(),
    public val apiChanges: List<SpecificationChange<ApiSpecification>> = emptyList(),
    public val propertyChanges: List<SpecificationChange<PropertySpecification>> = emptyList(),
    public val relationshipChanges: List<SpecificationChange<RelationshipSpecification>> = emptyList(),
    public val featureChanges: List<SpecificationChange<FeatureSpecification>> = emptyList(),
    public val entryPointChanges: List<SpecificationChange<EntryPointSpecification>> = emptyList(),
    public val scenarioChanges: List<SpecificationChange<ScenarioSpecification>> = emptyList(),
    public val scenarioStepChanges: List<SpecificationChange<ScenarioStepSpecification>> = emptyList(),
    public val evidenceChanges: List<SpecificationChange<Evidence>> = emptyList(),
) {
    public val hasChanges: Boolean
        get() = packageChanges.isNotEmpty() ||
            typeChanges.isNotEmpty() ||
            apiChanges.isNotEmpty() ||
            propertyChanges.isNotEmpty() ||
            relationshipChanges.isNotEmpty() ||
            featureChanges.isNotEmpty() ||
            entryPointChanges.isNotEmpty() ||
            scenarioChanges.isNotEmpty() ||
            scenarioStepChanges.isNotEmpty() ||
            evidenceChanges.isNotEmpty()

    public companion object {
        public val EMPTY: SpecificationDiff = SpecificationDiff()
    }
}

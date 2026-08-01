package io.docpilot.core.incremental.specification

/** Granularity at which a documentation update was detected. */
public enum class IncrementalUpdateTarget {
    PACKAGE,
    TYPE,
    API,
    PROPERTY,
    RELATIONSHIP,
    FEATURE,
    ENTRY_POINT,
    SCENARIO,
    SCENARIO_STEP,
    EVIDENCE,
}

public data class IncrementalUpdateAction(
    public val target: IncrementalUpdateTarget,
    public val id: String,
    public val parentId: String? = null,
    public val changeKind: ChangeKind,
)

/** Deterministic plan consumed by a future selective rendering/output integration. */
public data class IncrementalUpdatePlan(
    public val actions: List<IncrementalUpdateAction> = emptyList(),
    public val changedPackageIds: List<String> = emptyList(),
    public val changedTypeIds: List<String> = emptyList(),
) {
    public val requiresUpdate: Boolean
        get() = actions.isNotEmpty()

    public companion object {
        public val EMPTY: IncrementalUpdatePlan = IncrementalUpdatePlan()
    }
}

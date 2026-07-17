package io.docpilot.core.incremental.specification

import io.docpilot.core.model.ComponentSpecification
import io.docpilot.core.model.ProjectSpecification

/** Converts a specification diff into renderer-agnostic update targets. */
public class DefaultIncrementalSpecificationPlanner : IncrementalSpecificationPlanner {
    override fun plan(
        diff: SpecificationDiff,
        previous: ProjectSpecification,
        current: ProjectSpecification,
    ): IncrementalUpdatePlan {
        if (!diff.hasChanges) return IncrementalUpdatePlan.EMPTY

        val typeToPackage = buildTypeToPackage(previous, current)
        val actions = buildList {
            addAll(diff.packageChanges.map { it.toAction(IncrementalUpdateTarget.PACKAGE) })
            addAll(diff.typeChanges.map { it.toAction(IncrementalUpdateTarget.TYPE) })
            addAll(diff.apiChanges.map { it.toAction(IncrementalUpdateTarget.API) })
            addAll(diff.propertyChanges.map { it.toAction(IncrementalUpdateTarget.PROPERTY) })
        }.sortedWith(compareBy({ it.target.ordinal }, { it.parentId ?: "" }, { it.id }, { it.changeKind.ordinal }))

        val changedTypeIds = buildSet {
            diff.typeChanges.mapTo(this) { it.id }
            diff.apiChanges.mapNotNullTo(this) { it.parentId }
            diff.propertyChanges.mapNotNullTo(this) { it.parentId }
        }.sorted()

        val changedPackageIds = buildSet {
            diff.packageChanges.mapTo(this) { it.id }
            changedTypeIds.mapNotNullTo(this) { typeToPackage[it] }
        }.sorted()

        return IncrementalUpdatePlan(actions, changedPackageIds, changedTypeIds)
    }

    private fun buildTypeToPackage(
        previous: ProjectSpecification,
        current: ProjectSpecification,
    ): Map<String, String> = (previous.components + current.components)
        .associateBy(ComponentSpecification::id)
        .mapValues { (_, type) -> type.packageId ?: type.moduleId }

    private fun <T> SpecificationChange<T>.toAction(target: IncrementalUpdateTarget): IncrementalUpdateAction =
        IncrementalUpdateAction(target, id, parentId, kind)
}

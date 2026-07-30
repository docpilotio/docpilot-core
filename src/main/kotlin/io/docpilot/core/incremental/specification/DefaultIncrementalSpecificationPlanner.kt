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

        val previousTypeToPackage = buildTypeToPackage(previous)
        val currentTypeToPackage = buildTypeToPackage(current)
        val previousApiToType = buildApiToType(previous)
        val currentApiToType = buildApiToType(current)
        val previousPropertyToType = buildPropertyToType(previous)
        val currentPropertyToType = buildPropertyToType(current)
        val previousEndpointToType = buildEndpointToType(previous)
        val currentEndpointToType = buildEndpointToType(current)
        val previousEndpointToPackage = buildEndpointToPackage(previous)
        val currentEndpointToPackage = buildEndpointToPackage(current)

        val actions = buildList {
            addAll(diff.packageChanges.map { it.toAction(IncrementalUpdateTarget.PACKAGE) })
            addAll(diff.typeChanges.map { it.toAction(IncrementalUpdateTarget.TYPE) })
            addAll(diff.apiChanges.map { it.toAction(IncrementalUpdateTarget.API) })
            addAll(diff.propertyChanges.map { it.toAction(IncrementalUpdateTarget.PROPERTY) })
            addAll(diff.relationshipChanges.map { it.toAction(IncrementalUpdateTarget.RELATIONSHIP) })
            addAll(diff.featureChanges.map { it.toAction(IncrementalUpdateTarget.FEATURE) })
            addAll(diff.entryPointChanges.map { it.toAction(IncrementalUpdateTarget.ENTRY_POINT) })
            addAll(diff.scenarioChanges.map { it.toAction(IncrementalUpdateTarget.SCENARIO) })
            addAll(diff.scenarioStepChanges.map { it.toAction(IncrementalUpdateTarget.SCENARIO_STEP) })
        }.sortedWith(compareBy({ it.target.ordinal }, { it.parentId ?: "" }, { it.id }, { it.changeKind.ordinal }))

        val changedTypeIds = buildSet {
            diff.typeChanges.mapTo(this) { it.id }
            diff.apiChanges.forEach { change ->
                previousApiToType[change.id]?.let(::add)
                currentApiToType[change.id]?.let(::add)
            }
            diff.propertyChanges.forEach { change ->
                previousPropertyToType[change.id]?.let(::add)
                currentPropertyToType[change.id]?.let(::add)
            }
            diff.relationshipChanges.forEach { change ->
                change.previous?.let { relationship ->
                    previousEndpointToType[relationship.sourceId]?.let(::add)
                    previousEndpointToType[relationship.targetId]?.let(::add)
                }
                change.current?.let { relationship ->
                    currentEndpointToType[relationship.sourceId]?.let(::add)
                    currentEndpointToType[relationship.targetId]?.let(::add)
                }
            }
        }.sorted()

        val changedPackageIds = buildSet {
            diff.packageChanges.mapTo(this) { it.id }
            changedTypeIds.forEach { typeId ->
                previousTypeToPackage[typeId]?.let(::add)
                currentTypeToPackage[typeId]?.let(::add)
            }
            diff.relationshipChanges.forEach { change ->
                change.previous?.let { relationship ->
                    previousEndpointToPackage[relationship.sourceId]?.let(::add)
                    previousEndpointToPackage[relationship.targetId]?.let(::add)
                }
                change.current?.let { relationship ->
                    currentEndpointToPackage[relationship.sourceId]?.let(::add)
                    currentEndpointToPackage[relationship.targetId]?.let(::add)
                }
            }
        }.sorted()

        return IncrementalUpdatePlan(actions, changedPackageIds, changedTypeIds)
    }

    private fun buildTypeToPackage(specification: ProjectSpecification): Map<String, String> =
        specification.components.associate { type -> type.id to (type.packageId ?: type.moduleId) }

    private fun buildApiToType(specification: ProjectSpecification): Map<String, String> = buildMap {
        specification.components.forEach { type ->
            type.apis.forEach { api -> put(api.id, type.id) }
        }
    }

    private fun buildPropertyToType(specification: ProjectSpecification): Map<String, String> = buildMap {
        specification.components.forEach { type ->
            type.properties.forEach { property -> put(property.id, type.id) }
        }
    }

    private fun buildEndpointToType(specification: ProjectSpecification): Map<String, String> = buildMap {
        specification.components.forEach { type ->
            put(type.id, type.id)
            type.apis.forEach { api -> put(api.id, type.id) }
            type.properties.forEach { property -> put(property.id, type.id) }
        }
    }

    private fun buildEndpointToPackage(specification: ProjectSpecification): Map<String, String> = buildMap {
        specification.packages.forEach { packageSpecification ->
            put(packageSpecification.id, packageSpecification.id)
        }
        specification.components.forEach { type ->
            val packageId = type.packageId ?: type.moduleId
            put(type.id, packageId)
            type.apis.forEach { api -> put(api.id, packageId) }
            type.properties.forEach { property -> put(property.id, packageId) }
        }
    }

    private fun <T> SpecificationChange<T>.toAction(target: IncrementalUpdateTarget): IncrementalUpdateAction =
        IncrementalUpdateAction(target, id, parentId, kind)
}

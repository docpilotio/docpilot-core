package io.docpilot.core.incremental.specification

import io.docpilot.core.model.ApiSpecification
import io.docpilot.core.model.ComponentSpecification
import io.docpilot.core.model.PackageSpecification
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.PropertySpecification

/** Stable-id based, deterministic ProjectSpecification differ. */
public class DefaultSpecificationDiffer : SpecificationDiffer {
    override fun diff(previous: ProjectSpecification, current: ProjectSpecification): SpecificationDiff {
        val previousTypes = indexByStableId(previous.components, ComponentSpecification::id, "previous types")
        val currentTypes = indexByStableId(current.components, ComponentSpecification::id, "current types")

        return SpecificationDiff(
            packageChanges = compare(
                previous = indexByStableId(previous.packages, PackageSpecification::id, "previous packages"),
                current = indexByStableId(current.packages, PackageSpecification::id, "current packages"),
            ),
            typeChanges = compare(
                previous = previousTypes,
                current = currentTypes,
                comparable = { it.copy(apis = emptyList(), properties = emptyList()) },
                parentId = { it.packageId ?: it.moduleId },
            ),
            apiChanges = compareOwned(
                previous = flattenApis(previousTypes),
                current = flattenApis(currentTypes),
            ),
            propertyChanges = compareOwned(
                previous = flattenProperties(previousTypes),
                current = flattenProperties(currentTypes),
            ),
        )
    }

    private fun flattenApis(types: Map<String, ComponentSpecification>): Map<String, Owned<ApiSpecification>> =
        buildOwnedIndex("APIs") {
            types.values.forEach { type ->
                type.apis.forEach { api -> add(api.id, Owned(type.id, api)) }
            }
        }

    private fun flattenProperties(types: Map<String, ComponentSpecification>): Map<String, Owned<PropertySpecification>> =
        buildOwnedIndex("properties") {
            types.values.forEach { type ->
                type.properties.forEach { property -> add(property.id, Owned(type.id, property)) }
            }
        }

    private fun <T> compareOwned(
        previous: Map<String, Owned<T>>,
        current: Map<String, Owned<T>>,
    ): List<SpecificationChange<T>> = allIds(previous, current).mapNotNull { id ->
        val old = previous[id]
        val new = current[id]
        when {
            old == null && new != null -> SpecificationChange(id, new.ownerId, ChangeKind.ADDED, current = new.value)
            old != null && new == null -> SpecificationChange(id, old.ownerId, ChangeKind.REMOVED, previous = old.value)
            old != null && new != null && old != new -> SpecificationChange(id, new.ownerId, ChangeKind.MODIFIED, old.value, new.value)
            else -> null
        }
    }

    private fun <T> compare(
        previous: Map<String, T>,
        current: Map<String, T>,
        comparable: (T) -> Any? = { it },
        parentId: (T) -> String? = { null },
    ): List<SpecificationChange<T>> = allIds(previous, current).mapNotNull { id ->
        val old = previous[id]
        val new = current[id]
        when {
            old == null && new != null -> SpecificationChange(id, parentId(new), ChangeKind.ADDED, current = new)
            old != null && new == null -> SpecificationChange(id, parentId(old), ChangeKind.REMOVED, previous = old)
            old != null && new != null && comparable(old) != comparable(new) ->
                SpecificationChange(id, parentId(new), ChangeKind.MODIFIED, old, new)
            else -> null
        }
    }

    private fun <T> indexByStableId(
        values: List<T>,
        id: (T) -> String,
        label: String,
    ): Map<String, T> {
        val result = linkedMapOf<String, T>()
        values.forEach { value ->
            val stableId = id(value)
            require(stableId.isNotBlank()) { "Stable id in $label must not be blank." }
            require(result.put(stableId, value) == null) { "Duplicate stable id '$stableId' in $label." }
        }
        return result
    }

    private fun <T> buildOwnedIndex(label: String, block: OwnedIndexBuilder<T>.() -> Unit): Map<String, Owned<T>> =
        OwnedIndexBuilder<T>(label).apply(block).build()

    private fun <T> allIds(previous: Map<String, T>, current: Map<String, T>): List<String> =
        (previous.keys + current.keys).distinct().sorted()

    private data class Owned<T>(val ownerId: String, val value: T)

    private class OwnedIndexBuilder<T>(private val label: String) {
        private val values = linkedMapOf<String, Owned<T>>()

        fun add(id: String, value: Owned<T>) {
            require(id.isNotBlank()) { "Stable id in $label must not be blank." }
            require(values.put(id, value) == null) { "Duplicate stable id '$id' in $label." }
        }

        fun build(): Map<String, Owned<T>> = values
    }
}

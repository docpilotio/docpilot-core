package io.docpilot.core.specification

import io.docpilot.core.model.ProjectSpecification

internal object ProjectSpecificationValidator {
    fun validate(specification: ProjectSpecification) {
        fun requireValidIds(label: String, ids: List<String>) {
            require(ids.all(String::isNotBlank)) { "$label IDs must not be blank." }
            require(ids.distinct().size == ids.size) { "$label IDs must be unique." }
        }

        requireValidIds("Module", specification.modules.map { it.id })
        requireValidIds("Package", specification.packages.map { it.id })
        requireValidIds("Component", specification.components.map { it.id })
        requireValidIds("Relationship", specification.relationships.map { it.id })
        requireValidIds("Evidence", specification.evidence.map { it.id })
        requireValidIds("Unresolved", specification.unresolved.map { it.id })
        requireValidIds("API", specification.components.flatMap { it.apis }.map { it.id })
        requireValidIds("Property", specification.components.flatMap { it.properties }.map { it.id })

        val moduleIds = specification.modules.mapTo(mutableSetOf()) { it.id }
        require(specification.packages.all { it.moduleId in moduleIds }) { "Every package must reference an existing module." }
        require(specification.components.all { it.moduleId in moduleIds }) { "Every component must reference an existing module." }

        val packagesById = specification.packages.associateBy { it.id }
        require(specification.components.all { component ->
            val packageId = component.packageId ?: return@all true
            val packageSpecification = packagesById[packageId] ?: return@all false
            packageSpecification.moduleId == component.moduleId
        }) { "Every component package must exist and belong to the component module." }

        require(specification.relationships.all { it.sourceId.isNotBlank() && it.targetId.isNotBlank() }) {
            "Relationship endpoints must not be blank."
        }

        val evidenceIds = specification.evidence.mapTo(mutableSetOf()) { it.id }
        val refs = buildList {
            addAll(specification.modules.flatMap { it.evidenceRefs })
            addAll(specification.packages.flatMap { it.evidenceRefs })
            addAll(specification.components.flatMap { it.evidenceRefs })
            addAll(specification.components.flatMap { c -> c.apis.flatMap { it.evidenceRefs } })
            addAll(specification.components.flatMap { c -> c.properties.flatMap { it.evidenceRefs } })
            addAll(specification.relationships.flatMap { it.evidenceRefs })
        }
        require(refs.all { it in evidenceIds }) { "Every DIR evidence reference must exist." }
        require(specification.evidence.all { it.lineStart == null || it.lineEnd == null || it.lineStart <= it.lineEnd }) {
            "Evidence lineStart must not be after lineEnd."
        }
    }
}

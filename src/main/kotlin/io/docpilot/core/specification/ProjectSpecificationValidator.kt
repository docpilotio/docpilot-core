package io.docpilot.core.specification

import io.docpilot.core.model.ProjectSpecification

internal object ProjectSpecificationValidator {
    fun validate(specification: ProjectSpecification) {
        fun requireUnique(label: String, ids: List<String>) {
            require(ids.distinct().size == ids.size) { "$label IDs must be unique." }
        }
        requireUnique("Module", specification.modules.map { it.id })
        requireUnique("Package", specification.packages.map { it.id })
        requireUnique("Component", specification.components.map { it.id })
        requireUnique("Relationship", specification.relationships.map { it.id })
        requireUnique("Evidence", specification.evidence.map { it.id })
        requireUnique("Unresolved", specification.unresolved.map { it.id })
        requireUnique("API", specification.components.flatMap { it.apis }.map { it.id })
        requireUnique("Property", specification.components.flatMap { it.properties }.map { it.id })

        val moduleIds = specification.modules.mapTo(mutableSetOf()) { it.id }
        require(specification.packages.all { it.moduleId in moduleIds }) { "Every package must reference an existing module." }
        require(specification.components.all { it.moduleId in moduleIds }) { "Every component must reference an existing module." }

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

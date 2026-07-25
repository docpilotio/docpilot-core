package io.docpilot.core.specification

import io.docpilot.core.model.ProjectSpecification

public object ProjectSpecificationValidator {
    public fun validate(specification: ProjectSpecification) {
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

        val internalEndpointIds = buildSet {
            addAll(moduleIds)
            addAll(packagesById.keys)
            specification.components.forEach { component ->
                add(component.id)
                addAll(component.apis.map { it.id })
                addAll(component.properties.map { it.id })
            }
        }
        require(specification.relationships.all {
            it.type in SemanticRelationshipKind.entries.map(SemanticRelationshipKind::name)
        }) { "Every DIR relationship type must belong to the semantic relationship allowlist." }
        require(specification.relationships.all {
            it.id == RelationshipIdentity.of(it.type, it.sourceId, it.targetId)
        }) { "Every relationship ID must match its canonical semantic identity." }
        require(specification.relationships.all { it.evidenceRefs.isNotEmpty() }) {
            "Every semantic relationship must reference Evidence."
        }
        require(specification.relationships.all { relationship ->
            val sourceKind = RelationshipEndpointSemantics.kindOf(relationship.sourceId, internalEndpointIds)
            sourceKind == RelationshipEndpointKind.INTERNAL &&
                listOf(relationship.sourceId, relationship.targetId).all { endpointId ->
                endpointId.isNotBlank() && when (
                    RelationshipEndpointSemantics.kindOf(endpointId, internalEndpointIds)
                ) {
                    RelationshipEndpointKind.INTERNAL -> true
                    RelationshipEndpointKind.EXTERNAL ->
                        endpointId.length > RelationshipEndpointSemantics.EXTERNAL_PREFIX.length
                    RelationshipEndpointKind.UNRESOLVED -> unresolvedEndpointHasEvidence(endpointId, specification)
                }
            }
        }) {
            "Every relationship endpoint must be internal, external, or explicitly unresolved."
        }
        require(specification.relationships.none { it.sourceId == it.targetId && it.type != "CALLS" }) {
            "Structural self-relationships are not allowed."
        }

        val expectedDependencyIds = specification.relationships
            .filter { it.type == "DEPENDS_ON" }
            .filterNot { it.targetId.startsWith(RelationshipEndpointSemantics.UNRESOLVED_PREFIX) }
            .groupBy { it.sourceId }
            .mapValues { (_, values) -> values.mapTo(sortedSetOf()) { it.targetId } }
        require(specification.components.all { component ->
            component.dependencyIds == expectedDependencyIds[component.id].orEmpty()
        }) {
            "Component dependencyIds must equal direct outgoing DEPENDS_ON relationship targets."
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

    private fun unresolvedEndpointHasEvidence(endpointId: String, specification: ProjectSpecification): Boolean {
        if (!endpointId.startsWith(RelationshipEndpointSemantics.UNRESOLVED_PREFIX)) return false
        val reference = endpointId.removePrefix(RelationshipEndpointSemantics.UNRESOLVED_PREFIX)
        if (reference.isBlank()) return false
        val separator = reference.lastIndexOf(':')
        val relationshipId = if (separator > 0) reference.substring(0, separator) else reference
        val direction = if (separator > 0) reference.substring(separator + 1) else ""
        if (direction !in setOf("source", "target")) return false
        return specification.unresolved.any { item -> item.id == relationshipId }
    }
}

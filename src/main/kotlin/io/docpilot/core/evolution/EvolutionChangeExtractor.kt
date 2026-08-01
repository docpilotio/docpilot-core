package io.docpilot.core.evolution

import io.docpilot.core.model.ProjectSpecification

internal class EvolutionChangeExtractor {
    fun extract(before: ProjectSpecification, after: ProjectSpecification): List<DocumentationEvolutionChange> {
        val beforeRecords = records(before)
        val afterRecords = records(after)
        return (beforeRecords.keys + afterRecords.keys).distinct().sorted().flatMap { key ->
            val old = beforeRecords[key]
            val new = afterRecords[key]
            when {
                old == null && new != null -> listOf(change(new, addedKind(new.kind), null, new.hash, null, new.parentId, listOf("lifecycle:added")))
                old != null && new == null -> listOf(change(old, removedKind(old.kind), old.hash, null, old.parentId, null, listOf("lifecycle:removed")))
                old != null && new != null && old.hash != new.hash -> modifiedChanges(old, new)
                else -> emptyList()
            }
        }.sortedBy { it.changeId }
    }

    private fun modifiedChanges(old: SubjectRecord, new: SubjectRecord): List<DocumentationEvolutionChange> {
        val changedFields = changedFields(old, new)
        val result = mutableListOf<DocumentationEvolutionChange>()
        if (old.kind in entityKinds && (old.parentId != new.parentId || "path" in changedFields)) {
            result += change(
                new,
                EvolutionChangeKind.ENTITY_MOVED,
                old.hash,
                new.hash,
                old.parentId,
                new.parentId,
                changedFields.filter { it in moveFields },
                old.evidenceRefs,
            )
        }
        val nonMoveFields = changedFields.filterNot { it in moveFields }
        if (nonMoveFields.isNotEmpty() || result.isEmpty()) {
            result += change(
                new,
                modifiedKind(new.kind),
                old.hash,
                new.hash,
                old.parentId,
                new.parentId,
                if (nonMoveFields.isEmpty()) changedFields else nonMoveFields,
                old.evidenceRefs,
            )
        }
        return result
    }

    private fun change(
        record: SubjectRecord,
        kind: EvolutionChangeKind,
        beforeSha: String?,
        afterSha: String?,
        previousParentId: String?,
        currentParentId: String?,
        changedFields: List<String>,
        additionalEvidence: List<String> = emptyList(),
    ): DocumentationEvolutionChange {
        val fields = changedFields.distinct().sorted()
        val evidence = (record.evidenceRefs + additionalEvidence).distinct().sorted()
        val id = EvolutionCanonicalizer.stableChangeId(
            record.id,
            record.kind,
            kind,
            beforeSha,
            afterSha,
            previousParentId,
            currentParentId,
            fields,
        )
        return DocumentationEvolutionChange(
            changeId = id,
            subjectId = record.id,
            subjectKind = record.kind,
            kind = kind,
            beforeSha256 = beforeSha,
            afterSha256 = afterSha,
            previousParentId = previousParentId,
            currentParentId = currentParentId,
            changedFields = fields,
            evidenceRefs = evidence,
            causalPredecessorIds = evidence,
            confidenceClass = EvolutionConfidenceClass.OBSERVED,
            coverageState = EvolutionCoverageState.COMPLETE,
        )
    }

    private fun records(specification: ProjectSpecification): Map<String, SubjectRecord> {
        val values = mutableListOf<SubjectRecord>()
        values += subject(
            EvolutionSubjectKind.PROJECT,
            specification.project.id,
            null,
            EvolutionCanonicalizer.project(specification.project),
            emptyList(),
            mapOf(
                "name" to specification.project.name,
                "description" to specification.project.description.orEmpty(),
                "platforms" to specification.project.platforms.sorted().joinToString(","),
                "languages" to specification.project.languages.sorted().joinToString(","),
                "buildSystems" to specification.project.buildSystems.sorted().joinToString(","),
            ),
        )
        specification.modules.forEach { module ->
            values += subject(
                EvolutionSubjectKind.MODULE,
                module.id,
                specification.project.id,
                EvolutionCanonicalizer.module(module),
                module.evidenceRefs.toList(),
                mapOf(
                    "name" to module.name,
                    "path" to module.path.orEmpty(),
                    "description" to module.description.orEmpty(),
                    "sourceSets" to module.sourceSets.sorted().joinToString(","),
                ),
            )
        }
        specification.packages.forEach { pkg ->
            values += subject(
                EvolutionSubjectKind.PACKAGE,
                pkg.id,
                pkg.moduleId,
                EvolutionCanonicalizer.packageSpecification(pkg),
                pkg.evidenceRefs.toList(),
                mapOf(
                    "name" to pkg.name,
                    "qualifiedName" to pkg.qualifiedName,
                    "moduleId" to pkg.moduleId,
                    "description" to pkg.description.orEmpty(),
                ),
            )
        }
        specification.components.forEach { component ->
            values += subject(
                EvolutionSubjectKind.COMPONENT,
                component.id,
                component.packageId ?: component.moduleId,
                EvolutionCanonicalizer.component(component),
                component.evidenceRefs.toList(),
                mapOf(
                    "name" to component.name,
                    "moduleId" to component.moduleId,
                    "packageId" to component.packageId.orEmpty(),
                    "qualifiedName" to component.qualifiedName.orEmpty(),
                    "kind" to component.kind,
                    "role" to component.role,
                    "visibility" to component.visibility.orEmpty(),
                    "modifiers" to component.modifiers.sorted().joinToString(","),
                    "annotations" to component.annotations.joinToString(","),
                    "typeParameters" to component.typeParameters.joinToString(","),
                    "superTypes" to component.superTypes.joinToString(","),
                    "responsibilities" to component.responsibilities.joinToString("\u001f"),
                    "dependencyIds" to component.dependencyIds.sorted().joinToString(","),
                ),
            )
            component.apis.forEach { api ->
                values += subject(
                    EvolutionSubjectKind.API,
                    api.id,
                    component.id,
                    EvolutionCanonicalizer.api(api),
                    api.evidenceRefs.toList(),
                    mapOf(
                        "owner" to component.id,
                        "name" to api.name,
                        "kind" to api.kind,
                        "signature" to api.signature.orEmpty(),
                        "visibility" to api.visibility.orEmpty(),
                        "receiverType" to api.receiverType.orEmpty(),
                        "returnType" to api.returnType.orEmpty(),
                        "parameters" to api.parameters.joinToString("\u001f") { "${it.name}:${it.type}:${it.hasDefaultValue}" },
                        "modifiers" to api.modifiers.sorted().joinToString(","),
                        "annotations" to api.annotations.joinToString(","),
                        "purpose" to api.purpose.orEmpty(),
                    ),
                )
            }
            component.properties.forEach { property ->
                values += subject(
                    EvolutionSubjectKind.PROPERTY,
                    property.id,
                    component.id,
                    EvolutionCanonicalizer.property(property),
                    property.evidenceRefs.toList(),
                    mapOf(
                        "owner" to component.id,
                        "name" to property.name,
                        "type" to property.type.orEmpty(),
                        "visibility" to property.visibility.orEmpty(),
                        "mutable" to property.mutable?.toString().orEmpty(),
                        "hasInitializer" to property.hasInitializer?.toString().orEmpty(),
                        "modifiers" to property.modifiers.sorted().joinToString(","),
                        "annotations" to property.annotations.joinToString(","),
                        "purpose" to property.purpose.orEmpty(),
                    ),
                )
            }
        }
        specification.relationships.forEach { relationship ->
            values += subject(
                EvolutionSubjectKind.RELATIONSHIP,
                relationship.id,
                relationship.sourceId,
                EvolutionCanonicalizer.relationship(relationship),
                relationship.evidenceRefs.toList(),
                mapOf(
                    "type" to relationship.type,
                    "sourceId" to relationship.sourceId,
                    "targetId" to relationship.targetId,
                    "description" to relationship.description.orEmpty(),
                ),
            )
        }
        specification.contracts.forEach { contract ->
            values += subject(
                EvolutionSubjectKind.CONTRACT,
                contract.id,
                contract.owner.stableId,
                EvolutionCanonicalizer.contract(contract),
                contract.evidenceRefs.toList(),
                mapOf(
                    "displayName" to contract.displayName,
                    "semanticKey" to contract.semanticKey,
                    "kind" to contract.kind.name,
                    "role" to contract.role.name,
                    "owner" to contract.owner.stableId,
                    "sourceBindings" to contract.sourceEntityStableIds.sorted().joinToString(","),
                    "inputs" to contract.inputs.joinToString(",") { it.id },
                    "outputs" to contract.outputs.joinToString(",") { it.id },
                    "members" to contract.members.joinToString(",") { it.id },
                    "relationships" to contract.relationships.joinToString(",") { it.id },
                    "evidence" to contract.evidenceRefs.sorted().joinToString(","),
                    "unresolved" to contract.unresolvedRefs.sorted().joinToString(","),
                ),
            )
        }
        require(values.map { "${it.kind.name}:${it.id}" }.distinct().size == values.size) {
            "Duplicate Evolution subject identity."
        }
        return values.associateBy { "${it.kind.name}:${it.id}" }
    }

    private fun subject(
        kind: EvolutionSubjectKind,
        id: String,
        parentId: String?,
        canonical: String,
        evidenceRefs: List<String>,
        fields: Map<String, String>,
    ) = SubjectRecord(
        kind,
        id,
        parentId,
        EvolutionCanonicalizer.sha256(canonical),
        evidenceRefs.distinct().sorted(),
        fields.toSortedMap(),
    )

    private fun changedFields(old: SubjectRecord, new: SubjectRecord): List<String> =
        (old.fields.keys + new.fields.keys).distinct().sorted().filter { old.fields[it] != new.fields[it] }

    private fun addedKind(kind: EvolutionSubjectKind): EvolutionChangeKind = when (kind) {
        EvolutionSubjectKind.API -> EvolutionChangeKind.API_CHANGED
        EvolutionSubjectKind.PROPERTY -> EvolutionChangeKind.PROPERTY_CHANGED
        EvolutionSubjectKind.RELATIONSHIP -> EvolutionChangeKind.RELATIONSHIP_ADDED
        EvolutionSubjectKind.CONTRACT -> EvolutionChangeKind.CONTRACT_CHANGED
        else -> EvolutionChangeKind.ENTITY_ADDED
    }

    private fun removedKind(kind: EvolutionSubjectKind): EvolutionChangeKind = when (kind) {
        EvolutionSubjectKind.API -> EvolutionChangeKind.API_CHANGED
        EvolutionSubjectKind.PROPERTY -> EvolutionChangeKind.PROPERTY_CHANGED
        EvolutionSubjectKind.RELATIONSHIP -> EvolutionChangeKind.RELATIONSHIP_REMOVED
        EvolutionSubjectKind.CONTRACT -> EvolutionChangeKind.CONTRACT_CHANGED
        else -> EvolutionChangeKind.ENTITY_REMOVED
    }

    private fun modifiedKind(kind: EvolutionSubjectKind): EvolutionChangeKind = when (kind) {
        EvolutionSubjectKind.API -> EvolutionChangeKind.API_CHANGED
        EvolutionSubjectKind.PROPERTY -> EvolutionChangeKind.PROPERTY_CHANGED
        EvolutionSubjectKind.RELATIONSHIP -> EvolutionChangeKind.RELATIONSHIP_MODIFIED
        EvolutionSubjectKind.CONTRACT -> EvolutionChangeKind.CONTRACT_CHANGED
        else -> EvolutionChangeKind.ENTITY_MODIFIED
    }

    private data class SubjectRecord(
        val kind: EvolutionSubjectKind,
        val id: String,
        val parentId: String?,
        val hash: String,
        val evidenceRefs: List<String>,
        val fields: Map<String, String>,
    )

    private companion object {
        val entityKinds = setOf(
            EvolutionSubjectKind.PROJECT,
            EvolutionSubjectKind.MODULE,
            EvolutionSubjectKind.PACKAGE,
            EvolutionSubjectKind.COMPONENT,
            EvolutionSubjectKind.CONTRACT,
        )
        val moveFields = setOf("path", "moduleId", "packageId", "qualifiedName")
    }
}

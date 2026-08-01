package io.docpilot.core.specification

import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.EntryPointKind
import io.docpilot.core.model.ScenarioStepKind
import io.docpilot.core.model.*

public object ProjectSpecificationValidator {
    public fun validate(specification: ProjectSpecification) {
        require(specification.schemaVersion in setOf("0.2", "0.3", "0.4", "0.5")) {
            "Unsupported DIR schema version: ${specification.schemaVersion}"
        }
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
        if (specification.schemaVersion in setOf("0.4", "0.5")) validateDir04(specification, ::requireValidIds)
        if (specification.schemaVersion == "0.5") validateDir05(specification, ::requireValidIds)

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

    private fun validateDir05(
        specification: ProjectSpecification,
        requireValidIds: (String, List<String>) -> Unit,
    ) {
        requireValidIds("Contract", specification.contracts.map { it.id })
        requireValidIds("Contract nested entity", specification.contracts.flatMap { contract ->
            contract.inputs.map { it.id } + contract.outputs.map { it.id } +
                contract.members.map { it.id } + contract.relationships.map { it.id }
        })
        require(specification.contracts.map { it.id } == specification.contracts.map { it.id }.sorted()) {
            "Contracts must use canonical stable-ID ordering."
        }
        val evidence = specification.evidence.associateBy { it.id }
        val unresolvedIds = specification.unresolved.mapTo(mutableSetOf()) { it.id }
        val componentIds = specification.components.mapTo(mutableSetOf()) { it.id }
        val apiIds = specification.components.flatMap { it.apis }.mapTo(mutableSetOf()) { it.id }
        val entityIds = buildSet {
            add(specification.project.id)
            addAll(specification.modules.map { it.id }); addAll(specification.packages.map { it.id })
            addAll(componentIds); addAll(apiIds); addAll(specification.features.map { it.id })
            addAll(specification.entryPoints.map { it.id }); addAll(specification.contracts.map { it.id })
        }
        val allowedKinds = mapOf(
            ContractRole.PUBLIC_API to ContractKind.API,
            ContractRole.REPOSITORY_API to ContractKind.API,
            ContractRole.DATA_MODEL to ContractKind.DATA,
            ContractRole.DTO to ContractKind.DATA,
            ContractRole.EVENT to ContractKind.MESSAGE,
            ContractRole.CALLBACK to ContractKind.MESSAGE,
            ContractRole.NAVIGATION_ARGUMENT to ContractKind.NAVIGATION,
            ContractRole.PERSISTENCE_SCHEMA to ContractKind.PERSISTENCE,
            ContractRole.EXTERNAL_SERVICE_BOUNDARY to ContractKind.EXTERNAL,
        )
        val allowedOwners = mapOf(
            ContractRole.PUBLIC_API to setOf(ContractOwnerKind.PROJECT, ContractOwnerKind.MODULE, ContractOwnerKind.PACKAGE, ContractOwnerKind.COMPONENT, ContractOwnerKind.API),
            ContractRole.REPOSITORY_API to setOf(ContractOwnerKind.MODULE, ContractOwnerKind.PACKAGE, ContractOwnerKind.COMPONENT, ContractOwnerKind.API),
            ContractRole.DATA_MODEL to setOf(ContractOwnerKind.MODULE, ContractOwnerKind.PACKAGE, ContractOwnerKind.COMPONENT),
            ContractRole.DTO to setOf(ContractOwnerKind.MODULE, ContractOwnerKind.PACKAGE, ContractOwnerKind.COMPONENT),
            ContractRole.EVENT to setOf(ContractOwnerKind.MODULE, ContractOwnerKind.PACKAGE, ContractOwnerKind.COMPONENT, ContractOwnerKind.API),
            ContractRole.CALLBACK to setOf(ContractOwnerKind.COMPONENT, ContractOwnerKind.API),
            ContractRole.NAVIGATION_ARGUMENT to setOf(ContractOwnerKind.COMPONENT, ContractOwnerKind.API, ContractOwnerKind.FEATURE, ContractOwnerKind.ENTRY_POINT),
            ContractRole.PERSISTENCE_SCHEMA to setOf(ContractOwnerKind.MODULE, ContractOwnerKind.PACKAGE, ContractOwnerKind.COMPONENT),
            ContractRole.EXTERNAL_SERVICE_BOUNDARY to setOf(ContractOwnerKind.PROJECT, ContractOwnerKind.MODULE, ContractOwnerKind.COMPONENT, ContractOwnerKind.API, ContractOwnerKind.EXTERNAL),
        )
        fun validateRefs(evidenceRefs: Set<String>, unresolvedRefs: Set<String>, label: String) {
            require(evidenceRefs.isNotEmpty()) { "Every DIR 0.5 $label must reference Evidence." }
            require(evidenceRefs.all(evidence::containsKey)) { "Every DIR 0.5 $label Evidence reference must exist." }
            require(evidenceRefs.any { evidence.getValue(it).confidence != EvidenceConfidence.LOW }) {
                "DIR 0.5 $label cannot rely only on low-confidence Evidence."
            }
            require(unresolvedRefs.all(unresolvedIds::contains)) { "Every DIR 0.5 $label unresolved reference must exist." }
        }
        fun validateType(type: ContractTypeReference) {
            require(type.displayName.isNotBlank()) { "Contract type display name must not be blank." }
            require(type.kind != ContractTypeKind.UNRESOLVED || type.unresolvedRefs.isNotEmpty()) {
                "Unresolved Contract types must explicitly reference UnresolvedItem."
            }
            if (type.kind in setOf(ContractTypeKind.PROJECT, ContractTypeKind.EXTERNAL)) {
                require(!type.stableId.isNullOrBlank()) { "Named Contract types require a stable ID." }
            }
            if (type.kind == ContractTypeKind.PROJECT) require(type.stableId in entityIds) {
                "Project Contract types must bind to an existing canonical entity."
            }
            if (type.evidenceRefs.isNotEmpty()) validateRefs(type.evidenceRefs, type.unresolvedRefs, "type")
            else require(type.unresolvedRefs.all(unresolvedIds::contains)) { "Contract type unresolved reference must exist." }
            type.arguments.forEach(::validateType)
        }
        specification.contracts.forEach { contract ->
            require(contract.semanticKey.isNotBlank()) { "Contract semantic key must not be blank." }
            require(contract.id == ContractIdentity.of(contract.kind, contract.role, contract.owner, contract.semanticKey)) {
                "Contract ID must match its canonical semantic identity."
            }
            require(allowedKinds[contract.role] == contract.kind) { "Contract kind and role are incompatible." }
            require(contract.owner.kind in allowedOwners.getValue(contract.role)) { "Contract role and owner kind are incompatible." }
            require(contract.displayName.isNotBlank()) { "Contract display name must not be blank." }
            require(contract.sourceEntityStableIds.isNotEmpty()) { "Every Contract requires a source entity binding." }
            require(contract.sourceEntityStableIds.all { it in entityIds || it.startsWith("external:") }) {
                "Every Contract source binding must exist or be explicitly external."
            }
            require(contract.owner.stableId in entityIds || contract.owner.kind == ContractOwnerKind.EXTERNAL) {
                "Every Contract owner must exist or be explicitly external."
            }
            require(contract.owner.kind != ContractOwnerKind.EXTERNAL || contract.owner.stableId.startsWith("external:")) {
                "External Contract owners require an external: stable ID."
            }
            validateRefs(contract.evidenceRefs, contract.unresolvedRefs, "Contract")
            fun validateValues(values: List<ContractValue>, direction: ContractDirection) {
                require(values.map { it.id } == values.sortedWith(compareBy<ContractValue>({ it.semanticOrder ?: Int.MAX_VALUE }, { it.id })).map { it.id }) {
                    "Contract values must use semantic-order then stable-ID ordering."
                }
                values.forEach { value ->
                    require(value.id == ContractIdentity.nested(contract.id, direction.name.lowercase(), value.semanticKey)) {
                        "Contract value ID must match its canonical semantic identity."
                    }
                    require(value.semanticKey.isNotBlank() && value.name.isNotBlank()) { "Contract values require semantic key and name." }
                    require(value.direction == direction || value.direction == ContractDirection.INOUT) { "Contract value direction is incompatible with its collection." }
                    require(value.sourceEntityStableIds.all(entityIds::contains)) { "Contract value source binding must exist." }
                    validateRefs(value.evidenceRefs, value.unresolvedRefs, "value")
                    validateType(value.type)
                }
            }
            validateValues(contract.inputs, ContractDirection.INPUT)
            validateValues(contract.outputs, ContractDirection.OUTPUT)
            require(contract.members.map { it.id } == contract.members.sortedWith(compareBy<ContractMember>({ it.semanticOrder ?: Int.MAX_VALUE }, { it.id })).map { it.id }) {
                "Contract members must use semantic-order then stable-ID ordering."
            }
            contract.members.forEach { member ->
                require(member.id == ContractIdentity.nested(contract.id, "member", member.semanticKey)) {
                    "Contract member ID must match its canonical semantic identity."
                }
                require(member.semanticKey.isNotBlank() && member.name.isNotBlank()) { "Contract members require semantic key and name." }
                require(member.sourceEntityStableIds.all(entityIds::contains)) { "Contract member source binding must exist." }
                validateRefs(member.evidenceRefs, member.unresolvedRefs, "member")
                validateType(member.type)
            }
            require(contract.relationships.map { it.id } == contract.relationships.map { it.id }.sorted()) {
                "Contract relationships must use stable-ID ordering."
            }
            contract.relationships.forEach { relationship ->
                require(relationship.id == ContractIdentity.relationship(contract.id, relationship.kind, relationship.targetStableId)) {
                    "Contract relationship ID must match its canonical semantic identity."
                }
                require(relationship.targetStableId in entityIds || relationship.unresolvedRefs.isNotEmpty()) {
                    "Contract relationship target must exist or be unresolved."
                }
                require(relationship.targetStableId != contract.id) { "Contract self-relationships are prohibited." }
                validateRefs(relationship.evidenceRefs, relationship.unresolvedRefs, "relationship")
            }
            when (contract.role) {
                ContractRole.PUBLIC_API, ContractRole.REPOSITORY_API, ContractRole.EXTERNAL_SERVICE_BOUNDARY ->
                    require(contract.inputs.isNotEmpty() || contract.outputs.isNotEmpty()) { "API boundary Contracts require input or output." }
                ContractRole.DATA_MODEL, ContractRole.DTO, ContractRole.EVENT, ContractRole.NAVIGATION_ARGUMENT,
                ContractRole.PERSISTENCE_SCHEMA -> require(contract.members.isNotEmpty()) { "Data-shaped Contracts require members." }
                ContractRole.CALLBACK -> require(contract.inputs.isNotEmpty()) { "Callback Contracts require delivered inputs." }
            }
        }
        val contractIds = specification.contracts.mapTo(mutableSetOf()) { it.id }
        val graph = specification.contracts.associate { contract ->
            contract.id to contract.relationships.map { it.targetStableId }.filter(contractIds::contains)
        }
        fun visitsCycle(id: String, visiting: MutableSet<String>, visited: MutableSet<String>): Boolean {
            if (!visiting.add(id)) return true
            if (id in visited) { visiting.remove(id); return false }
            val cycle = graph[id].orEmpty().any { visitsCycle(it, visiting, visited) }
            visiting.remove(id); visited.add(id)
            return cycle
        }
        val visited = mutableSetOf<String>()
        require(graph.keys.sorted().none { visitsCycle(it, mutableSetOf(), visited) }) {
            "Contract relationship cycles are prohibited."
        }
    }

    private fun validateDir04(
        specification: ProjectSpecification,
        requireValidIds: (String, List<String>) -> Unit,
    ) {
        requireValidIds("Feature", specification.features.map { it.id })
        requireValidIds("Entry point", specification.entryPoints.map { it.id })
        requireValidIds("Scenario", specification.scenarios.map { it.id })
        requireValidIds("Scenario step", specification.scenarios.flatMap { it.steps }.map { it.id })
        fun <T> requireCanonical(label: String, values: List<T>, id: (T) -> String) {
            require(values.map(id) == values.map(id).sorted()) { "$label must use canonical stable-ID ordering." }
        }
        requireCanonical("Features", specification.features) { it.id }
        requireCanonical("Entry points", specification.entryPoints) { it.id }
        requireCanonical("Scenarios", specification.scenarios) { it.id }

        val componentIds = specification.components.mapTo(mutableSetOf()) { it.id }
        val apiIds = specification.components.flatMap { it.apis }.mapTo(mutableSetOf()) { it.id }
        val evidenceIds = specification.evidence.mapTo(mutableSetOf()) { it.id }
        val unresolvedIds = specification.unresolved.mapTo(mutableSetOf()) { it.id }
        val entryPointIds = specification.entryPoints.mapTo(mutableSetOf()) { it.id }
        val scenarioIds = specification.scenarios.mapTo(mutableSetOf()) { it.id }
        val featureIds = specification.features.mapTo(mutableSetOf()) { it.id }
        fun validateRefs(evidence: Set<String>, unresolved: Set<String>, label: String) {
            require(evidence.isNotEmpty()) { "Every DIR 0.4 $label must reference Evidence." }
            require(evidence.all { it in evidenceIds }) { "Every DIR 0.4 $label Evidence reference must exist." }
            require(unresolved.all { it in unresolvedIds }) { "Every DIR 0.4 $label UnresolvedItem reference must exist." }
        }
        specification.entryPoints.forEach {
            require(it.kind in EntryPointKind.entries.map(EntryPointKind::name)) {
                "Every Entry Point kind must belong to the DIR 0.4 allowlist."
            }
            require(it.ownerComponentId in componentIds) { "Every Entry Point owner must reference a Component." }
            require(it.apiId == null || it.apiId in apiIds) { "Every Entry Point API must exist." }
            validateRefs(it.evidenceRefs, it.unresolvedRefs, "Entry Point")
        }
        specification.features.forEach {
            require(it.ownerComponentId in componentIds) { "Every Feature owner must reference a Component." }
            require(it.participantComponentIds.all(componentIds::contains)) { "Every Feature participant must reference a Component." }
            require(it.entryPointIds.distinct().size == it.entryPointIds.size && it.entryPointIds.all(entryPointIds::contains)) {
                "Every Feature Entry Point reference must be unique and exist."
            }
            require(it.scenarioIds.distinct().size == it.scenarioIds.size && it.scenarioIds.all(scenarioIds::contains)) {
                "Every Feature Scenario reference must be unique and exist."
            }
            validateRefs(it.evidenceRefs, it.unresolvedRefs, "Feature")
        }
        specification.scenarios.forEach { scenario ->
            require(scenario.featureId in featureIds) { "Every Scenario Feature must exist." }
            require(scenario.entryPointId == null || scenario.entryPointId in entryPointIds) { "Every Scenario Entry Point must exist." }
            require(scenario.steps.isNotEmpty()) { "Every Scenario must contain at least one ordered step." }
            require(scenario.steps.map { it.order }.distinct().size == scenario.steps.size) {
                "Scenario step order must be unique."
            }
            require(scenario.steps == scenario.steps.sortedWith(compareBy({ it.order }, { it.id }))) {
                "Scenario steps must use canonical order plus stable-ID ordering."
            }
            validateRefs(scenario.evidenceRefs, scenario.unresolvedRefs, "Scenario")
            scenario.steps.forEach { step ->
                require(step.action in ScenarioStepKind.entries.map(ScenarioStepKind::name)) {
                    "Every Scenario Step kind must belong to the DIR 0.4 allowlist."
                }
                require(step.ownerComponentId in componentIds) { "Every Scenario Step owner must reference a Component." }
                require(step.targetComponentId == null || step.targetComponentId in componentIds) {
                    "Every Scenario Step target must reference a Component."
                }
                require(step.apiId == null || step.apiId in apiIds) { "Every Scenario Step API must exist." }
                validateRefs(step.evidenceRefs, step.unresolvedRefs, "Scenario Step")
            }
        }
        require(specification.features.all { feature ->
            specification.scenarios.filter { it.featureId == feature.id }.map { it.id } == feature.scenarioIds
        }) { "Feature scenarioIds must equal its canonical Scenario ownership list." }
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

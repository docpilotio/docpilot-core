package io.docpilot.core.specification.extraction

import io.docpilot.core.model.*
import io.docpilot.core.model.source.*
import io.docpilot.core.specification.ContractIdentity
import java.security.MessageDigest

public data class ContractExtractionResult(
    val contracts: List<ContractSpecification>,
    val unresolved: List<UnresolvedItem>,
)

/** Evidence-bounded RFC-0066 extraction. Rules deliberately accept qualified identities only. */
public class DeterministicContractExtractionEngine {
    public fun extract(sourceIndex: SourceIndex?, specification: ProjectSpecification): ContractExtractionResult {
        if (sourceIndex == null) return ContractExtractionResult(emptyList(), emptyList())
        val context = Context(sourceIndex, specification)
        val candidates = buildList {
            sourceIndex.files.sortedBy { it.relativePath }.forEach { file ->
                file.symbols.sortedBy { it.id }.forEach { symbol ->
                    addAll(context.extract(file, symbol))
                }
                addAll(context.navigation(file))
            }
        }
        return ContractExtractionResult(
            contracts = candidates.groupBy { it.id }.map { (_, duplicates) -> merge(duplicates) }.sortedBy { it.id },
            unresolved = context.unresolved.values.sortedBy { it.id },
        )
    }

    private fun merge(values: List<ContractSpecification>): ContractSpecification {
        val first = values.first()
        require(values.all { it.kind == first.kind && it.role == first.role && it.owner == first.owner }) {
            "Conflicting observations for Contract ${first.id}."
        }
        return first.copy(
            sourceEntityStableIds = values.flatMapTo(sortedSetOf()) { it.sourceEntityStableIds },
            inputs = values.flatMap { it.inputs }.distinctBy { it.id }.sortedWith(compareBy({ it.semanticOrder ?: Int.MAX_VALUE }, { it.id })),
            outputs = values.flatMap { it.outputs }.distinctBy { it.id }.sortedWith(compareBy({ it.semanticOrder ?: Int.MAX_VALUE }, { it.id })),
            members = values.flatMap { it.members }.distinctBy { it.id }.sortedWith(compareBy({ it.semanticOrder ?: Int.MAX_VALUE }, { it.id })),
            relationships = values.flatMap { it.relationships }.distinctBy { it.id }.sortedBy { it.id },
            evidenceRefs = values.flatMapTo(sortedSetOf()) { it.evidenceRefs },
            unresolvedRefs = values.flatMapTo(sortedSetOf()) { it.unresolvedRefs },
        )
    }

    private class Context(sourceIndex: SourceIndex, private val spec: ProjectSpecification) {
        val unresolved = linkedMapOf<String, UnresolvedItem>()
        private val components = spec.components.associateBy { it.qualifiedName }
        private val allComponentsBySimpleName = spec.components.groupBy { it.name }
        private val importsByFile: Map<String, Map<String, String>> = sourceIndex.files.associate { file ->
            file.relativePath to file.imports.filterNot { it.wildcard }.associate {
                (it.alias ?: it.qualifiedName.substringAfterLast('.')) to it.qualifiedName
            }
        }

        fun extract(file: SourceFile, symbol: SourceSymbol): List<ContractSpecification> {
            val component = symbol.qualifiedName?.let(components::get) ?: return emptyList()
            val annotations = symbol.annotations.mapNotNull { qualifiedAnnotation(file, it) }.toSet()
            return buildList {
                // Kotlin declarations are public when visibility is omitted; the scanner preserves that as DEFAULT.
                component.apis.filter { it.visibility in setOf("DEFAULT", "PUBLIC", "PROTECTED") }.forEach { api ->
                    apiContract(component, api, ContractRole.PUBLIC_API)?.let(::add)
                    callbackContracts(component, api).forEach(::add)
                }
                roleFor(annotations)?.let { role ->
                    when (role) {
                        ContractRole.REPOSITORY_API -> shapedApiComponentContract(component, role)
                        else -> shapedContract(component, role)
                    }?.let(::add)
                }
                if (annotations.any { it in RETROFIT_METHODS } || component.apis.any { api ->
                        api.annotations.mapNotNull { qualifiedAnnotation(file, it) }.any(RETROFIT_METHODS::contains)
                    }) shapedApiComponentContract(component, ContractRole.EXTERNAL_SERVICE_BOUNDARY)?.let(::add)
                symbol.children.filter { it.kind.isComponent() }.forEach { addAll(extract(file, it)) }
            }
        }

        fun navigation(file: SourceFile): List<ContractSpecification> =
            (file.composeNavigation.routeArguments + file.composeNavigation.registrations.flatMap { it.arguments })
                .filter { it.sourceKind != ComposeNavigationArgumentSourceKind.UNKNOWN }
                .distinctBy { it.id }
                .mapNotNull { argument ->
                    val ownerId = argument.ownerRegistrationId?.let { registrationId ->
                        spec.entryPoints.firstOrNull { it.id.contains(registrationId) }?.id
                    } ?: spec.entryPoints.firstOrNull { entry -> argument.ownerRouteId?.let(entry.id::contains) == true }?.id
                    val owner = ownerId?.let { ContractOwner(ContractOwnerKind.ENTRY_POINT, it) } ?: return@mapNotNull null
                    val evidence = evidenceAt(argument.location, "COMPOSE_NAVIGATION_ARGUMENT")
                    if (evidence.isEmpty()) return@mapNotNull null
                    val semanticKey = "navigation-argument:${argument.id}"
                    val id = ContractIdentity.of(ContractKind.NAVIGATION, ContractRole.NAVIGATION_ARGUMENT, owner, semanticKey)
                    val type = type(argument.declaredType ?: "String", file, evidence, "navigation:${argument.id}")
                    ContractSpecification(
                        id, semanticKey, argument.name, ContractKind.NAVIGATION, ContractRole.NAVIGATION_ARGUMENT,
                        owner, setOf(owner.stableId), members = listOf(member(id, argument.name, argument.name, type, owner.stableId, evidence, 0)),
                        evidenceRefs = evidence,
                    )
                }

        private fun apiContract(component: ComponentSpecification, api: ApiSpecification, role: ContractRole): ContractSpecification? {
            if (api.evidenceRefs.isEmpty()) return null
            val owner = ContractOwner(ContractOwnerKind.COMPONENT, component.id)
            val key = "api:${api.id}"
            val id = ContractIdentity.of(ContractKind.API, role, owner, key)
            val inputs = api.parameters.mapIndexed { index, parameter ->
                value(id, parameter.name, parameter.name, type(parameter.type, null, api.evidenceRefs, "$key:${parameter.name}"),
                    ContractDirection.INPUT, api.id, api.evidenceRefs, index)
            }
            val outputs = api.returnType?.takeUnless { it == "Unit" || it == "kotlin.Unit" }?.let {
                listOf(value(id, "return", "return", type(it, null, api.evidenceRefs, "$key:return"),
                    ContractDirection.OUTPUT, api.id, api.evidenceRefs, 0))
            }.orEmpty()
            if (inputs.isEmpty() && outputs.isEmpty()) return null
            return ContractSpecification(id, key, api.name, ContractKind.API, role, owner, setOf(api.id),
                inputs = inputs, outputs = outputs, evidenceRefs = api.evidenceRefs)
        }

        private fun callbackContracts(component: ComponentSpecification, api: ApiSpecification): List<ContractSpecification> =
            api.parameters.mapIndexedNotNull { index, parameter ->
                val raw = parameter.type ?: return@mapIndexedNotNull null
                val arrow = topLevelArrow(raw) ?: return@mapIndexedNotNull null
                if (api.evidenceRefs.isEmpty()) return@mapIndexedNotNull null
                val owner = ContractOwner(ContractOwnerKind.API, api.id)
                val key = "callback:${api.id}:${parameter.name}"
                val id = ContractIdentity.of(ContractKind.MESSAGE, ContractRole.CALLBACK, owner, key)
                val delivered = arrow.first.removePrefix("(").removeSuffix(")").split(',').map(String::trim)
                    .filter(String::isNotBlank).mapIndexed { deliveredIndex, rawType ->
                        value(id, "arg:$deliveredIndex", "arg$deliveredIndex", type(rawType, null, api.evidenceRefs, "$key:$deliveredIndex"),
                            ContractDirection.INPUT, api.id, api.evidenceRefs, deliveredIndex)
                    }
                if (delivered.isEmpty()) return@mapIndexedNotNull null
                ContractSpecification(id, key, parameter.name, ContractKind.MESSAGE, ContractRole.CALLBACK, owner,
                    setOf(api.id), inputs = delivered, evidenceRefs = api.evidenceRefs)
            }

        private fun shapedContract(component: ComponentSpecification, role: ContractRole): ContractSpecification? {
            if (component.evidenceRefs.isEmpty()) return null
            val owner = ContractOwner(ContractOwnerKind.COMPONENT, component.id)
            val key = "type:${component.qualifiedName ?: component.id}:${role.name}"
            val kind = KIND_BY_ROLE.getValue(role)
            val id = ContractIdentity.of(kind, role, owner, key)
            val members = component.properties.mapIndexedNotNull { index, property ->
                val evidence = property.evidenceRefs.ifEmpty { component.evidenceRefs }
                property.type?.let { member(id, property.name, property.name, type(it, null, evidence, "$key:${property.name}"), component.id, evidence, index) }
            }
            if (members.isEmpty()) return null
            return ContractSpecification(id, key, component.name, kind, role, owner, setOf(component.id), members = members,
                evidenceRefs = component.evidenceRefs)
        }

        private fun shapedApiComponentContract(component: ComponentSpecification, role: ContractRole): ContractSpecification? {
            val contracts = component.apis.mapNotNull { apiContract(component, it, role) }
            if (contracts.isEmpty()) return null
            val owner = ContractOwner(ContractOwnerKind.COMPONENT, component.id)
            val key = "boundary:${component.qualifiedName ?: component.id}"
            val id = ContractIdentity.of(KIND_BY_ROLE.getValue(role), role, owner, key)
            return ContractSpecification(id, key, component.name, KIND_BY_ROLE.getValue(role), role, owner, setOf(component.id),
                inputs = contracts.flatMap { it.inputs }.map { value ->
                    val nestedKey = "${value.sourceEntityStableIds.sorted().joinToString(",")}:${value.semanticKey}"
                    value.copy(id = ContractIdentity.nested(id, "input", nestedKey), semanticKey = nestedKey)
                }.distinctBy { it.id }.sortedWith(compareBy({ it.semanticOrder ?: Int.MAX_VALUE }, { it.id })),
                outputs = contracts.flatMap { it.outputs }.map { value ->
                    val nestedKey = "${value.sourceEntityStableIds.sorted().joinToString(",")}:${value.semanticKey}"
                    value.copy(id = ContractIdentity.nested(id, "output", nestedKey), semanticKey = nestedKey)
                }.distinctBy { it.id }.sortedWith(compareBy({ it.semanticOrder ?: Int.MAX_VALUE }, { it.id })),
                evidenceRefs = contracts.flatMapTo(sortedSetOf()) { it.evidenceRefs })
        }

        private fun roleFor(annotations: Set<String>): ContractRole? {
            val matches = RULES.filter { rule -> annotations.any(rule.annotations::contains) }.map { it.role }.distinct()
            return matches.singleOrNull()
        }

        private fun qualifiedAnnotation(file: SourceFile, raw: String): String? {
            val clean = raw.substringBefore('(').removePrefix("@").trim()
            if ('.' in clean) return clean
            val imports: Map<String, String> = importsByFile[file.relativePath].orEmpty()
            return imports[clean]
        }

        private fun type(raw: String?, file: SourceFile?, evidence: Set<String>, subject: String): ContractTypeReference {
            if (raw.isNullOrBlank()) return unresolvedType("<unknown>", evidence, subject)
            val text = raw.trim()
            val nullable = text.endsWith('?')
            val base = text.removeSuffix("?").trim()
            val genericStart = base.indexOf('<')
            if (genericStart > 0 && base.endsWith('>')) {
                val outer = base.substring(0, genericStart).trim()
                val arguments = splitTypes(base.substring(genericStart + 1, base.length - 1)).map { type(it, file, evidence, subject) }
                val collection = outer.substringAfterLast('.') in setOf("List", "Set", "Collection", "Iterable", "Array", "Map")
                return ContractTypeReference(if (collection) ContractTypeKind.COLLECTION else ContractTypeKind.GENERIC,
                    displayName = outer, nullable = nullable, arguments = arguments, evidenceRefs = evidence)
            }
            if (base in PRIMITIVES || base.substringAfterLast('.') in PRIMITIVES) return ContractTypeReference(
                ContractTypeKind.PRIMITIVE, displayName = base, nullable = nullable, evidenceRefs = evidence)
            val qualified = when {
                '.' in base -> base
                file != null -> importsByFile[file.relativePath].orEmpty()[base]
                else -> null
            }
            val candidates: List<ComponentSpecification> = if (qualified != null) {
                listOfNotNull(components[qualified])
            } else {
                allComponentsBySimpleName[base].orEmpty()
            }
            return when (candidates.size) {
                1 -> ContractTypeReference(ContractTypeKind.PROJECT, candidates.single().id, base, nullable, evidenceRefs = evidence)
                0 -> ContractTypeReference(ContractTypeKind.EXTERNAL, "external:type:${qualified ?: base}", base, nullable, evidenceRefs = evidence)
                else -> unresolvedType(base, evidence, subject)
            }
        }

        private fun unresolvedType(name: String, evidence: Set<String>, subject: String): ContractTypeReference {
            val id = "unresolved:contract-type:${digest("$subject|$name")}" 
            unresolved.putIfAbsent(id, UnresolvedItem(id, subject, "Resolve ambiguous Contract type $name.", "AMBIGUOUS_TYPE"))
            return ContractTypeReference(ContractTypeKind.UNRESOLVED, displayName = name, evidenceRefs = evidence, unresolvedRefs = setOf(id))
        }

        private fun evidenceAt(location: SourceLocation, type: String): Set<String> = spec.evidence.filter {
            it.file == location.relativePath && it.type == type &&
                (it.lineStart == null || it.lineStart == location.lineStart)
        }.mapTo(sortedSetOf()) { it.id }

        private fun value(contractId: String, key: String, name: String, type: ContractTypeReference,
            direction: ContractDirection, source: String, evidence: Set<String>, order: Int) = ContractValue(
            ContractIdentity.nested(contractId, direction.name.lowercase(), key), key, name, type, direction,
            sourceEntityStableIds = setOf(source), evidenceRefs = evidence, unresolvedRefs = type.unresolvedRefs, semanticOrder = order)

        private fun member(contractId: String, key: String, name: String, type: ContractTypeReference,
            source: String, evidence: Set<String>, order: Int) = ContractMember(
            ContractIdentity.nested(contractId, "member", key), key, name, type,
            sourceEntityStableIds = setOf(source), evidenceRefs = evidence, unresolvedRefs = type.unresolvedRefs, semanticOrder = order)
    }

    public data class Rule(val role: ContractRole, val annotations: Set<String>)

    public companion object {
        public val RULES: List<Rule> = listOf(
            Rule(ContractRole.REPOSITORY_API, setOf("org.springframework.stereotype.Repository", "io.docpilot.contract.Repository")),
            Rule(ContractRole.DATA_MODEL, setOf("io.docpilot.contract.DataModel")),
            Rule(ContractRole.DTO, setOf("kotlinx.serialization.Serializable", "com.fasterxml.jackson.annotation.JsonIgnoreProperties", "io.docpilot.contract.Dto")),
            Rule(ContractRole.EVENT, setOf("org.springframework.context.event.EventListener", "io.docpilot.contract.Event")),
            Rule(ContractRole.PERSISTENCE_SCHEMA, setOf("androidx.room.Entity", "io.docpilot.contract.PersistenceSchema")),
        )
        private val KIND_BY_ROLE = mapOf(
            ContractRole.PUBLIC_API to ContractKind.API, ContractRole.REPOSITORY_API to ContractKind.API,
            ContractRole.DATA_MODEL to ContractKind.DATA, ContractRole.DTO to ContractKind.DATA,
            ContractRole.EVENT to ContractKind.MESSAGE, ContractRole.CALLBACK to ContractKind.MESSAGE,
            ContractRole.NAVIGATION_ARGUMENT to ContractKind.NAVIGATION, ContractRole.PERSISTENCE_SCHEMA to ContractKind.PERSISTENCE,
            ContractRole.EXTERNAL_SERVICE_BOUNDARY to ContractKind.EXTERNAL,
        )
        private val RETROFIT_METHODS = setOf("retrofit2.http.GET", "retrofit2.http.POST", "retrofit2.http.PUT", "retrofit2.http.DELETE", "retrofit2.http.PATCH", "retrofit2.http.HEAD", "retrofit2.http.OPTIONS", "retrofit2.http.HTTP")
        private val PRIMITIVES = setOf("Boolean", "Byte", "Short", "Int", "Long", "Float", "Double", "Char", "String", "Unit", "Any", "Nothing")
        private fun SourceSymbolKind.isComponent() = this in setOf(SourceSymbolKind.CLASS, SourceSymbolKind.INTERFACE, SourceSymbolKind.OBJECT, SourceSymbolKind.ENUM_CLASS, SourceSymbolKind.ANNOTATION_CLASS, SourceSymbolKind.TYPE_ALIAS)
        private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)
        private fun splitTypes(value: String): List<String> { var depth = 0; var start = 0; val result = mutableListOf<String>(); value.forEachIndexed { index, c -> when(c) { '<','(' -> depth++; '>',')' -> depth--; ',' -> if (depth == 0) { result += value.substring(start, index).trim(); start = index + 1 } } }; result += value.substring(start).trim(); return result }
        private fun topLevelArrow(value: String): Pair<String, String>? { var depth = 0; for (i in 0 until value.length - 1) { when(value[i]) { '(','<' -> depth++; ')','>' -> depth--; '-' -> if (value[i + 1] == '>' && depth == 0) return value.substring(0, i).trim() to value.substring(i + 2).trim() } }; return null }
    }
}

package io.docpilot.core.model

public enum class ContractKind { API, DATA, MESSAGE, NAVIGATION, PERSISTENCE, EXTERNAL }

public enum class ContractRole {
    PUBLIC_API, REPOSITORY_API, DATA_MODEL, DTO, EVENT, CALLBACK,
    NAVIGATION_ARGUMENT, PERSISTENCE_SCHEMA, EXTERNAL_SERVICE_BOUNDARY,
}

public enum class ContractOwnerKind { PROJECT, MODULE, PACKAGE, COMPONENT, API, FEATURE, ENTRY_POINT, EXTERNAL }
public enum class ContractTypeKind { PRIMITIVE, PROJECT, EXTERNAL, GENERIC, COLLECTION, UNRESOLVED }
public enum class ContractDirection { INPUT, OUTPUT, INOUT }
public enum class ContractCardinality { ONE, OPTIONAL, MANY, NON_EMPTY_MANY }
public enum class ContractRelationshipKind {
    CALLS_API, USES_INPUT, PRODUCES_OUTPUT, USES_PAYLOAD, DELIVERS,
    NAVIGATES_TO, PERSISTS_AS, REQUESTS, RESPONDS_WITH, REFERENCES,
}

public data class ContractOwner(public val kind: ContractOwnerKind, public val stableId: String)

public data class ContractTypeReference(
    public val kind: ContractTypeKind,
    public val stableId: String? = null,
    public val displayName: String,
    public val nullable: Boolean = false,
    public val arguments: List<ContractTypeReference> = emptyList(),
    public val evidenceRefs: Set<String> = emptySet(),
    public val unresolvedRefs: Set<String> = emptySet(),
)

public data class ContractValue(
    public val id: String,
    public val semanticKey: String,
    public val name: String,
    public val type: ContractTypeReference,
    public val direction: ContractDirection,
    public val cardinality: ContractCardinality = ContractCardinality.ONE,
    public val sourceEntityStableIds: Set<String> = emptySet(),
    public val evidenceRefs: Set<String> = emptySet(),
    public val unresolvedRefs: Set<String> = emptySet(),
    public val semanticOrder: Int? = null,
)

public data class ContractMember(
    public val id: String,
    public val semanticKey: String,
    public val name: String,
    public val type: ContractTypeReference,
    public val cardinality: ContractCardinality = ContractCardinality.ONE,
    public val sourceEntityStableIds: Set<String> = emptySet(),
    public val evidenceRefs: Set<String> = emptySet(),
    public val unresolvedRefs: Set<String> = emptySet(),
    public val semanticOrder: Int? = null,
)

public data class ContractRelationship(
    public val id: String,
    public val kind: ContractRelationshipKind,
    public val targetStableId: String,
    public val evidenceRefs: Set<String> = emptySet(),
    public val unresolvedRefs: Set<String> = emptySet(),
)

public data class ContractSpecification(
    public val id: String,
    public val semanticKey: String,
    public val displayName: String,
    public val kind: ContractKind,
    public val role: ContractRole,
    public val owner: ContractOwner,
    public val sourceEntityStableIds: Set<String>,
    public val inputs: List<ContractValue> = emptyList(),
    public val outputs: List<ContractValue> = emptyList(),
    public val members: List<ContractMember> = emptyList(),
    public val relationships: List<ContractRelationship> = emptyList(),
    public val evidenceRefs: Set<String>,
    public val unresolvedRefs: Set<String> = emptySet(),
)

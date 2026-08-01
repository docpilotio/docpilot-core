package io.docpilot.core.specification

import io.docpilot.core.model.*
import java.security.MessageDigest

public object ContractCanonicalizer {
    public fun semanticHash(contract: ContractSpecification): String = sha256(canonical(contract))

    public fun canonical(contract: ContractSpecification): String = record(
        contract.id, contract.semanticKey, contract.displayName, contract.kind.name, contract.role.name,
        contract.owner.kind.name, contract.owner.stableId, contract.sourceEntityStableIds.sorted().joinToString(","),
        contract.inputs.sortedWith(valueOrder).joinToString("\u001f", transform = ::value),
        contract.outputs.sortedWith(valueOrder).joinToString("\u001f", transform = ::value),
        contract.members.sortedWith(memberOrder).joinToString("\u001f", transform = ::member),
        contract.relationships.sortedBy { it.id }.joinToString("\u001f") { relationship -> record(
            relationship.id, relationship.kind.name, relationship.targetStableId,
            relationship.evidenceRefs.sorted().joinToString(","), relationship.unresolvedRefs.sorted().joinToString(","),
        ) },
        contract.evidenceRefs.sorted().joinToString(","), contract.unresolvedRefs.sorted().joinToString(","),
    )

    private val valueOrder = compareBy<ContractValue>({ it.semanticOrder ?: Int.MAX_VALUE }, { it.id })
    private val memberOrder = compareBy<ContractMember>({ it.semanticOrder ?: Int.MAX_VALUE }, { it.id })
    private fun value(value: ContractValue): String = record(
        value.id, value.semanticKey, value.name, type(value.type), value.direction.name, value.cardinality.name,
        value.sourceEntityStableIds.sorted().joinToString(","), value.evidenceRefs.sorted().joinToString(","),
        value.unresolvedRefs.sorted().joinToString(","), value.semanticOrder?.toString().orEmpty(),
    )
    private fun member(value: ContractMember): String = record(
        value.id, value.semanticKey, value.name, type(value.type), value.cardinality.name,
        value.sourceEntityStableIds.sorted().joinToString(","), value.evidenceRefs.sorted().joinToString(","),
        value.unresolvedRefs.sorted().joinToString(","), value.semanticOrder?.toString().orEmpty(),
    )
    private fun type(value: ContractTypeReference): String = record(
        value.kind.name, value.stableId.orEmpty(), value.displayName, value.nullable.toString(),
        value.arguments.joinToString("\u001f") { type(it) }, value.evidenceRefs.sorted().joinToString(","),
        value.unresolvedRefs.sorted().joinToString(","),
    )
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun record(vararg values: String): String = buildString {
        values.forEach { append(it.toByteArray(Charsets.UTF_8).size).append(':').append(it) }
        append('\n')
    }
}

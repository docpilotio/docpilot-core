package io.docpilot.core.incremental.specification.ai

import io.docpilot.core.incremental.specification.ChangeKind
import io.docpilot.core.incremental.specification.IncrementalUpdateAction
import io.docpilot.core.incremental.specification.IncrementalUpdateTarget
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.specification.RelationshipEndpointSemantics

public fun interface SpecificationIncrementalPromptBuilder {
    public fun build(
        previous: ProjectSpecification,
        current: ProjectSpecification,
        actions: List<IncrementalUpdateAction>,
    ): String
}

public class DefaultSpecificationIncrementalPromptBuilder : SpecificationIncrementalPromptBuilder {
    override fun build(
        previous: ProjectSpecification,
        current: ProjectSpecification,
        actions: List<IncrementalUpdateAction>,
    ): String = buildString {
        appendLine("Update documentation only for the changed specification targets below.")
        appendLine("Return one block per target using exactly:")
        appendLine("<<<DOCPILOT_PATCH id=TARGET_ID>>>")
        appendLine("Markdown for that target only")
        appendLine("<<<END_DOCPILOT_PATCH>>>")
        appendLine("Do not return the full document. Do not invent facts. Preserve stable IDs.")
        appendLine()
        actions.sortedWith(compareBy({ it.target.ordinal }, { it.id }, { it.changeKind.ordinal })).forEach { action ->
            appendLine("CHANGE ${action.changeKind} ${action.target} ${action.id}")
            renderTarget(previous, action, "BEFORE")?.let(::appendLine)
            renderTarget(current, action, "AFTER")?.let(::appendLine)
            appendLine()
        }
    }.trimEnd()

    private fun renderTarget(
        specification: ProjectSpecification,
        action: IncrementalUpdateAction,
        label: String,
    ): String? {
        val value = when (action.target) {
            IncrementalUpdateTarget.PACKAGE -> specification.packages.firstOrNull { it.id == action.id }
                ?.let { "id=${it.id}; name=${it.qualifiedName}; description=${it.description.orEmpty()}" }
            IncrementalUpdateTarget.TYPE -> specification.components.firstOrNull { it.id == action.id }
                ?.let { "id=${it.id}; name=${it.qualifiedName ?: it.name}; kind=${it.kind}; role=${it.role}; responsibilities=${it.responsibilities.sorted().joinToString()}" }
            IncrementalUpdateTarget.API -> specification.components.asSequence().flatMap { it.apis.asSequence() }
                .firstOrNull { it.id == action.id }
                ?.let { "id=${it.id}; name=${it.name}; kind=${it.kind}; signature=${it.signature.orEmpty()}; purpose=${it.purpose.orEmpty()}" }
            IncrementalUpdateTarget.PROPERTY -> specification.components.asSequence().flatMap { it.properties.asSequence() }
                .firstOrNull { it.id == action.id }
                ?.let { "id=${it.id}; name=${it.name}; type=${it.type.orEmpty()}; purpose=${it.purpose.orEmpty()}" }
            IncrementalUpdateTarget.RELATIONSHIP -> specification.relationships.firstOrNull { it.id == action.id }
                ?.let {
                    val internalIds = internalEndpointIds(specification)
                    "id=${it.id}; type=${it.type}; source=${it.sourceId}; target=${it.targetId}; " +
                        "sourceKind=${RelationshipEndpointSemantics.kindOf(it.sourceId, internalIds)}; " +
                        "targetKind=${RelationshipEndpointSemantics.kindOf(it.targetId, internalIds)}; " +
                        "description=${it.description.orEmpty()}; evidence=${it.evidenceRefs.sorted().joinToString()}"
                }
        }
        return value?.let { "$label $it" } ?: if (action.changeKind == ChangeKind.REMOVED) "$label id=${action.id}; removed=true" else null
    }

    private fun internalEndpointIds(specification: ProjectSpecification): Set<String> = buildSet {
        addAll(specification.modules.map { it.id })
        addAll(specification.packages.map { it.id })
        specification.components.forEach { component ->
            add(component.id)
            addAll(component.apis.map { it.id })
            addAll(component.properties.map { it.id })
        }
    }
}

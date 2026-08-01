package io.docpilot.core.render

import io.docpilot.core.api.DocumentationArtifactDescriptor
import io.docpilot.core.api.DocumentationArtifactId
import io.docpilot.core.api.DocumentationArtifactKind
import io.docpilot.core.api.SelectiveSpecificationRenderer
import io.docpilot.core.documentation.profile.DocumentationProfileCanonicalizer
import io.docpilot.core.documentation.profile.DocumentationRendererCapabilityProvider
import io.docpilot.core.documentation.profile.RendererCapability
import io.docpilot.core.model.ContractMember
import io.docpilot.core.model.ContractSpecification
import io.docpilot.core.model.ContractTypeReference
import io.docpilot.core.model.ContractValue
import io.docpilot.core.model.Evidence
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.RenderedArtifact
import io.docpilot.core.model.UnresolvedItem

/** Deterministic RFC-0067 renderer. Its only fact input is the persisted DIR 0.5 model. */
public class ContractDocumentationMarkdownRenderer :
    SelectiveSpecificationRenderer,
    DocumentationRendererCapabilityProvider {
    override fun capabilities(): Set<RendererCapability> = setOf(
        RendererCapability.MARKDOWN_SECTION_RENDERING,
        RendererCapability.EVIDENCE_REFERENCE_RENDERING,
        RendererCapability.UNKNOWN_FINDING_RENDERING,
        RendererCapability.CONTRACT_DOCUMENTATION_RENDERING,
    )

    override fun describe(specification: ProjectSpecification): List<DocumentationArtifactDescriptor> {
        require(specification.schemaVersion == "0.5") { "Contract documentation requires persisted DIR 0.5." }
        val details = specification.contracts.sortedBy { it.id }.map { contract ->
            DocumentationArtifactDescriptor(
                DocumentationArtifactId("contract-detail:${contract.id}"),
                detailPath(contract),
                MARKDOWN,
                DocumentationArtifactKind.CONTRACT_DETAIL,
                contractScope(contract),
            )
        }
        val catalog = DocumentationArtifactDescriptor(
            DocumentationArtifactId("contract-catalog:${specification.project.id}"),
            "docs/contracts/catalog.md",
            MARKDOWN,
            DocumentationArtifactKind.CONTRACT_CATALOG,
            specification.contracts.flatMap(::contractScope).distinct().sorted(),
            details.map { it.artifactId },
        )
        return (listOf(catalog) + details).sortedBy { it.artifactId.value }
    }

    override fun render(
        specification: ProjectSpecification,
        artifactIds: Set<DocumentationArtifactId>,
    ): List<RenderedArtifact> {
        val descriptors = describe(specification).associateBy { it.artifactId }
        require(artifactIds.all(descriptors::containsKey)) { "Unknown Contract documentation artifact id requested." }
        return artifactIds.map { id ->
            val descriptor = descriptors.getValue(id)
            val content = when (descriptor.kind) {
                DocumentationArtifactKind.CONTRACT_CATALOG -> renderCatalog(specification)
                DocumentationArtifactKind.CONTRACT_DETAIL -> renderDetail(
                    specification,
                    specification.contracts.single { "contract-detail:${it.id}" == id.value },
                )
                else -> error("Unsupported Contract artifact kind: ${descriptor.kind}")
            }
            RenderedArtifact(descriptor.relativePath, descriptor.mediaType, content)
        }.sortedBy { it.relativePath }
    }

    private fun renderCatalog(specification: ProjectSpecification): String = buildString {
        val contracts = specification.contracts.sortedBy { it.id }
        appendLine("# Contract Catalog")
        appendLine()
        appendLine("Generated deterministically from canonical DIR 0.5 Contracts. No source scan or inferred facts are used.")
        appendLine()
        appendLine("## Generation Identity")
        appendLine()
        field("Specification", specification.project.id)
        field("DIR schema", specification.schemaVersion)
        field("Profile", "kotlin-android@1")
        appendLine()
        appendLine("## Contract Summary")
        appendLine()
        appendLine("- Total: ${contracts.size}")
        appendLine("- Contracts with unresolved references: ${contracts.count { it.unresolvedRefs.isNotEmpty() }}")
        appendLine("- Unresolved references: ${contracts.sumOf { it.unresolvedRefs.size }}")
        appendLine()
        appendLine("### By Role")
        appendLine()
        enumCounts(io.docpilot.core.model.ContractRole.entries, contracts.groupingBy { it.role }.eachCount())
        appendLine()
        appendLine("### By Kind")
        appendLine()
        enumCounts(io.docpilot.core.model.ContractKind.entries, contracts.groupingBy { it.kind }.eachCount())
        appendLine()
        appendLine("## Contracts")
        appendLine()
        if (contracts.isEmpty()) appendLine("None.") else {
            appendLine("| Stable ID | Display Name | Kind | Role | Owner | Inputs | Outputs | Members | Relationships | Evidence | Unresolved | Detail |")
            appendLine("|---|---|---|---|---|---:|---:|---:|---:|---:|---:|---|")
            contracts.forEach { contract ->
                appendLine("| ${cell(contract.id)} | ${cell(contract.displayName.ifBlank { contract.id })} | ${contract.kind} | ${contract.role} | ${cell(contract.owner.stableId)} | ${contract.inputs.size} | ${contract.outputs.size} | ${contract.members.size} | ${contract.relationships.size} | ${contract.evidenceRefs.size} | ${contract.unresolvedRefs.size} | [Detail](${detailPath(contract).removePrefix("docs/contracts/")}) |")
            }
        }
        appendLine()
        appendLine("## Interpretation Policy")
        appendLine()
        appendLine("Evidence entries reproduce canonical Evidence fields. Unresolved references remain unresolved and are never converted into links or inferred facts.")
    }

    private fun renderDetail(specification: ProjectSpecification, contract: ContractSpecification): String = buildString {
        val evidence = specification.evidence.filter { it.id in contract.evidenceRefs }.sortedBy { it.id }
        val unresolved = specification.unresolved.filter { it.id in contract.unresolvedRefs }.sortedBy { it.id }
        appendLine("# ${text(contract.displayName.ifBlank { contract.id })}")
        section("Identity") { field("Stable ID", contract.id); field("Semantic key", contract.semanticKey); field("Display name", contract.displayName.ifBlank { contract.id }) }
        section("Classification") { field("Contract kind", contract.kind.name); field("Contract role", contract.role.name) }
        section("Ownership") { field("Owner Stable ID", contract.owner.stableId); field("Owner kind", contract.owner.kind.name); if (contract.owner.kind.name == "EXTERNAL") appendLine("- External owner: Yes") }
        section("Source Bindings") {
            list("Source entity", contract.sourceEntityStableIds.sorted())
            val locations = evidence.mapNotNull(::evidenceLocation).distinct().sorted()
            list("Canonical source", locations)
        }
        values("Inputs", contract.inputs)
        values("Outputs", contract.outputs)
        members(contract.members)
        section("Relationships") {
            if (contract.relationships.isEmpty()) appendLine("None.") else contract.relationships.sortedBy { it.id }.forEach {
                appendLine("- ${code(it.kind.name)} from ${code(contract.id)} to ${code(it.targetStableId)}; Evidence ${codes(it.evidenceRefs)}; Unresolved ${codes(it.unresolvedRefs)}")
            }
        }
        section("Evidence") { if (evidence.isEmpty()) appendLine("None.") else evidence.forEach { renderEvidence(it) } }
        section("Unresolved") { if (unresolved.isEmpty()) appendLine("None.") else unresolved.forEach { renderUnresolved(it) } }
        section("Generation Metadata") { field("DIR schema", specification.schemaVersion); field("Profile", "kotlin-android@1"); field("Source model", contract.id); appendLine("- Rendering mode: Deterministic canonical facts only") }
    }

    private fun StringBuilder.values(title: String, values: List<ContractValue>) = section(title) {
        if (values.isEmpty()) appendLine("None.") else values.sortedWith(compareBy({ it.semanticOrder ?: Int.MAX_VALUE }, { it.id })).forEach {
            appendLine("- ${code(it.name)} — ${type(it.type)}; direction ${it.direction}; cardinality ${it.cardinality}; resolved target ${code(it.type.stableId ?: "None")}; unresolved ${codes(it.unresolvedRefs + it.type.unresolvedRefs)}")
        }
    }

    private fun StringBuilder.members(values: List<ContractMember>) = section("Members") {
        if (values.isEmpty()) appendLine("None.") else values.sortedWith(compareBy({ it.semanticOrder ?: Int.MAX_VALUE }, { it.id })).forEach {
            appendLine("- ${code(it.name)} — ${type(it.type)}; cardinality ${it.cardinality}; resolved target ${code(it.type.stableId ?: "None")}; unresolved ${codes(it.unresolvedRefs + it.type.unresolvedRefs)}")
        }
    }

    private fun type(value: ContractTypeReference): String = buildString {
        append(code(value.displayName)); append("; kind ${value.kind}; nullable ${value.nullable}")
        if (value.arguments.isNotEmpty()) append("; arguments [${value.arguments.joinToString { type(it) }}]")
    }

    private fun StringBuilder.renderEvidence(value: Evidence) {
        append("- ${code(value.id)} — type ${code(value.type)}; confidence ${value.confidence}; source ${code(evidenceLocation(value) ?: "None")}; summary ${text(value.summary)}")
        appendLine()
    }

    private fun StringBuilder.renderUnresolved(value: UnresolvedItem) = appendLine("- ${code(value.id)} — subject ${code(value.subject)}; reason ${text(value.question)}; required action ${text(value.requiredAction ?: "None")}")

    private fun evidenceLocation(value: Evidence): String? = value.file?.let { file ->
        require(!Regex("^(?:[A-Za-z]:|/|\\\\)").containsMatchIn(file)) { "Absolute Evidence paths cannot be rendered." }
        buildString { append(file.replace('\\', '/')); value.symbol?.let { append("#").append(it) }; value.lineStart?.let { append(":").append(it); if (value.lineEnd != null && value.lineEnd != it) append("-").append(value.lineEnd) } }
    }

    private fun contractScope(contract: ContractSpecification): List<String> = buildList {
        add(contract.id); add(contract.owner.stableId); addAll(contract.sourceEntityStableIds)
        addAll(contract.inputs.map { it.id }); addAll(contract.outputs.map { it.id }); addAll(contract.members.map { it.id })
        addAll(contract.relationships.map { it.id }); addAll(contract.evidenceRefs); addAll(contract.unresolvedRefs)
    }.distinct().sorted()

    private fun detailPath(contract: ContractSpecification): String = "docs/contracts/details/${DocumentationProfileCanonicalizer.slug(contract.displayName.ifBlank { contract.id })}-${DocumentationProfileCanonicalizer.scopeHash(contract.id).take(8)}.md"
    private fun <E : Enum<E>> StringBuilder.enumCounts(values: List<E>, counts: Map<E, Int>) = values.forEach { appendLine("- ${it.name}: ${counts[it] ?: 0}") }
    private inline fun StringBuilder.section(title: String, body: StringBuilder.() -> Unit) { appendLine(); appendLine("## $title"); appendLine(); body() }
    private fun StringBuilder.field(label: String, value: String) = appendLine("- $label: ${code(value)}")
    private fun StringBuilder.list(label: String, values: List<String>) = appendLine("- $label: ${if (values.isEmpty()) "None" else values.joinToString { code(it) }}")
    private fun codes(values: Collection<String>): String = if (values.isEmpty()) "None" else values.sorted().joinToString { code(it) }
    private fun code(value: String): String = "`${value.replace("`", "\\`").replace("\n", " ")}`"
    private fun text(value: String): String = value.replace("\r", " ").replace("\n", " ").replace("|", "\\|")
    private fun cell(value: String): String = text(value).replace("`", "\\`")

    private companion object { const val MARKDOWN = "text/markdown" }
}

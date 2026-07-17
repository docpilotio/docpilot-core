package io.docpilot.core.render

import io.docpilot.core.api.SpecificationRenderer
import io.docpilot.core.model.ApiSpecification
import io.docpilot.core.model.ComponentSpecification
import io.docpilot.core.model.Evidence
import io.docpilot.core.model.ModuleSpecification
import io.docpilot.core.model.PackageSpecification
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.PropertySpecification
import io.docpilot.core.model.RenderedArtifact
import io.docpilot.core.model.UnresolvedItem

/**
 * Renders a ProjectSpecification (DIR) as deterministic, reviewable Markdown.
 *
 * This renderer is intentionally presentation-only: it does not inspect source files,
 * SourceIndex, or the Knowledge Graph and it does not infer missing specification data.
 */
public class ProjectSpecificationMarkdownRenderer : SpecificationRenderer {
    override fun render(specification: ProjectSpecification): List<RenderedArtifact> =
        listOf(
            RenderedArtifact(
                relativePath = OUTPUT_PATH,
                mediaType = MARKDOWN_MEDIA_TYPE,
                content = buildMarkdown(specification),
            ),
        )

    private fun buildMarkdown(specification: ProjectSpecification): String = buildString {
        appendLine("# ${escapeText(specification.project.name)}")
        appendLine()
        appendLine("## Project")
        appendLine()
        appendField("ID", specification.project.id)
        appendField("Schema version", specification.schemaVersion)
        appendOptionalField("Description", specification.project.description)
        appendCollectionField("Platforms", specification.project.platforms)
        appendCollectionField("Languages", specification.project.languages)
        appendCollectionField("Build systems", specification.project.buildSystems)
        appendLine()

        appendLine("## Modules")
        appendLine()
        val modules = specification.modules.sortedWith(compareBy({ it.path ?: "" }, { it.name }, { it.id }))
        if (modules.isEmpty()) {
            appendLine("- None")
            appendLine()
        } else {
            modules.forEach { module ->
                appendModule(module, specification)
            }
        }

        appendRelationships(specification)
        appendEvidence(specification.evidence)
        appendUnresolved(specification.unresolved)
    }

    private fun StringBuilder.appendModule(
        module: ModuleSpecification,
        specification: ProjectSpecification,
    ) {
        appendLine("### ${escapeText(module.name)}")
        appendLine()
        appendField("ID", module.id)
        appendOptionalField("Path", module.path)
        appendOptionalField("Description", module.description)
        appendCollectionField("Source sets", module.sourceSets)
        appendEvidenceRefs(module.evidenceRefs)
        appendLine()
        appendLine("#### Packages")
        appendLine()

        val packages = specification.packages
            .filter { it.moduleId == module.id }
            .sortedWith(compareBy({ it.qualifiedName }, { it.name }, { it.id }))
        if (packages.isEmpty()) {
            appendLine("- None")
            appendLine()
        } else {
            packages.forEach { packageSpecification ->
                appendPackage(packageSpecification, specification)
            }
        }

        val unassignedComponents = specification.components
            .filter { it.moduleId == module.id && it.packageId == null }
            .sortedWith(componentComparator)
        if (unassignedComponents.isNotEmpty()) {
            appendLine("#### Unassigned Types")
            appendLine()
            unassignedComponents.forEach { appendComponent(it) }
        }
    }

    private fun StringBuilder.appendPackage(
        packageSpecification: PackageSpecification,
        specification: ProjectSpecification,
    ) {
        val title = packageSpecification.qualifiedName.ifBlank { "(default)" }
        appendLine("##### ${escapeText(title)}")
        appendLine()
        appendField("ID", packageSpecification.id)
        appendOptionalField("Description", packageSpecification.description)
        appendEvidenceRefs(packageSpecification.evidenceRefs)
        appendLine()
        appendLine("###### Types")
        appendLine()

        val components = specification.components
            .filter { it.packageId == packageSpecification.id }
            .sortedWith(componentComparator)
        if (components.isEmpty()) {
            appendLine("- None")
            appendLine()
        } else {
            components.forEach { appendComponent(it) }
        }
    }

    private fun StringBuilder.appendComponent(component: ComponentSpecification) {
        val title = component.qualifiedName ?: component.name
        appendLine("**${escapeText(title)}**")
        appendLine()
        appendField("ID", component.id)
        appendField("Kind", component.kind)
        appendField("Role", component.role)
        appendOptionalField("Visibility", component.visibility)
        appendCollectionField("Modifiers", component.modifiers)
        appendCollectionField("Annotations", component.annotations)
        appendCollectionField("Type parameters", component.typeParameters)
        appendCollectionField("Super types", component.superTypes)
        appendCollectionField("Responsibilities", component.responsibilities)
        appendCollectionField("Dependencies", component.dependencyIds)
        appendEvidenceRefs(component.evidenceRefs)
        appendLine()
        appendLine("APIs:")
        appendLine()

        val apis = component.apis.sortedWith(compareBy({ it.signature ?: it.name }, { it.kind }, { it.id }))
        if (apis.isEmpty()) {
            appendLine("- None")
        } else {
            apis.forEach { appendApi(it) }
        }
        appendLine()
        appendLine("Properties:")
        appendLine()

        val properties = component.properties.sortedWith(compareBy({ it.name }, { it.type ?: "" }, { it.id }))
        if (properties.isEmpty()) {
            appendLine("- None")
        } else {
            properties.forEach { appendProperty(it) }
        }
        appendLine()
    }

    private fun StringBuilder.appendApi(api: ApiSpecification) {
        appendLine("- ${code(api.signature ?: api.name)}")
        appendLine("  - ID: ${code(api.id)}")
        appendLine("  - Kind: ${escapeText(api.kind)}")
        appendOptionalNestedField("Visibility", api.visibility)
        appendOptionalNestedField("Receiver type", api.receiverType)
        appendOptionalNestedField("Return type", api.returnType)
        appendNestedCollectionField("Modifiers", api.modifiers)
        appendNestedCollectionField("Annotations", api.annotations)
        appendOptionalNestedField("Purpose", api.purpose)
        if (api.parameters.isEmpty()) {
            appendLine("  - Parameters: None")
        } else {
            appendLine("  - Parameters:")
            api.parameters.sortedWith(compareBy({ it.name }, { it.type ?: "" })).forEach { parameter ->
                val type = parameter.type?.let(::code) ?: "Unspecified"
                val defaultSuffix = if (parameter.hasDefaultValue) " (default value)" else ""
                appendLine("    - ${code(parameter.name)}: $type$defaultSuffix")
            }
        }
        appendNestedEvidenceRefs(api.evidenceRefs)
    }

    private fun StringBuilder.appendProperty(property: PropertySpecification) {
        appendLine("- ${code(property.name)}")
        appendLine("  - ID: ${code(property.id)}")
        appendOptionalNestedField("Type", property.type)
        appendOptionalNestedField("Visibility", property.visibility)
        appendOptionalNestedBooleanField("Mutable", property.mutable)
        appendOptionalNestedBooleanField("Initializer", property.hasInitializer)
        appendNestedCollectionField("Modifiers", property.modifiers)
        appendNestedCollectionField("Annotations", property.annotations)
        appendOptionalNestedField("Purpose", property.purpose)
        appendNestedEvidenceRefs(property.evidenceRefs)
    }

    private fun StringBuilder.appendRelationships(specification: ProjectSpecification) {
        appendLine("## Relationships")
        appendLine()
        val relationships = specification.relationships.sortedWith(
            compareBy({ it.sourceId }, { it.type }, { it.targetId }, { it.id }),
        )
        if (relationships.isEmpty()) {
            appendLine("- None")
            appendLine()
            return
        }
        relationships.forEach { relationship ->
            appendLine("- ${code(relationship.sourceId)} -> **${escapeText(relationship.type)}** -> ${code(relationship.targetId)}")
            appendLine("  - ID: ${code(relationship.id)}")
            appendOptionalNestedField("Description", relationship.description)
            appendNestedEvidenceRefs(relationship.evidenceRefs)
        }
        appendLine()
    }

    private fun StringBuilder.appendEvidence(evidence: List<Evidence>) {
        appendLine("## Evidence")
        appendLine()
        val sorted = evidence.sortedWith(compareBy({ it.file ?: "" }, { it.lineStart ?: Int.MAX_VALUE }, { it.symbol ?: "" }, { it.id }))
        if (sorted.isEmpty()) {
            appendLine("- None")
            appendLine()
            return
        }
        sorted.forEach { item ->
            appendLine("- ${code(item.id)} - ${escapeText(item.summary)}")
            appendLine("  - Type: ${escapeText(item.type)}")
            appendLine("  - Confidence: ${item.confidence.name}")
            appendOptionalNestedField("File", item.file)
            appendOptionalNestedField("Symbol", item.symbol)
            appendLineRange(item)
        }
        appendLine()
    }

    private fun StringBuilder.appendUnresolved(unresolved: List<UnresolvedItem>) {
        appendLine("## Unresolved")
        appendLine()
        val sorted = unresolved.sortedWith(compareBy({ it.subject }, { it.question }, { it.id }))
        if (sorted.isEmpty()) {
            appendLine("- None")
            return
        }
        sorted.forEach { item ->
            appendLine("- **${escapeText(item.subject)}** - ${escapeText(item.question)}")
            appendLine("  - ID: ${code(item.id)}")
            appendOptionalNestedField("Required action", item.requiredAction)
        }
    }

    private fun StringBuilder.appendField(label: String, value: String) {
        appendLine("- $label: ${code(value)}")
    }

    private fun StringBuilder.appendOptionalField(label: String, value: String?) {
        appendLine("- $label: ${value?.let(::escapeText) ?: "Unspecified"}")
    }

    private fun StringBuilder.appendCollectionField(label: String, values: Collection<String>) {
        appendLine("- $label: ${renderCollection(values)}")
    }

    private fun StringBuilder.appendEvidenceRefs(refs: Collection<String>) {
        appendLine("- Evidence refs: ${renderCodeCollection(refs)}")
    }

    private fun StringBuilder.appendOptionalNestedField(label: String, value: String?) {
        appendLine("  - $label: ${value?.let(::escapeText) ?: "Unspecified"}")
    }

    private fun StringBuilder.appendOptionalNestedBooleanField(label: String, value: Boolean?) {
        appendLine("  - $label: ${value?.let { if (it) "Yes" else "No" } ?: "Unspecified"}")
    }

    private fun StringBuilder.appendNestedCollectionField(label: String, values: Collection<String>) {
        appendLine("  - $label: ${renderCollection(values)}")
    }

    private fun StringBuilder.appendNestedEvidenceRefs(refs: Collection<String>) {
        appendLine("  - Evidence refs: ${renderCodeCollection(refs)}")
    }

    private fun StringBuilder.appendLineRange(evidence: Evidence) {
        val value = when {
            evidence.lineStart == null && evidence.lineEnd == null -> "Unspecified"
            evidence.lineStart == evidence.lineEnd || evidence.lineEnd == null -> evidence.lineStart.toString()
            evidence.lineStart == null -> evidence.lineEnd.toString()
            else -> "${evidence.lineStart}-${evidence.lineEnd}"
        }
        appendLine("  - Lines: $value")
    }

    private fun renderCollection(values: Collection<String>): String =
        values.sorted().takeIf { it.isNotEmpty() }?.joinToString(", ") { code(it) } ?: "None"

    private fun renderCodeCollection(values: Collection<String>): String =
        values.sorted().takeIf { it.isNotEmpty() }?.joinToString(", ") { code(it) } ?: "None"

    private fun code(value: String): String {
        val normalized = value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')
        val longestRun = Regex("`+").findAll(normalized).maxOfOrNull { it.value.length } ?: 0
        val fence = "`".repeat(longestRun + 1)
        val padding = if (normalized.startsWith('`') || normalized.endsWith('`')) " " else ""
        return "$fence$padding$normalized$padding$fence"
    }

    private fun escapeText(value: String): String = buildString {
        value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ').forEach { character ->
            if (character in MARKDOWN_SPECIAL_CHARACTERS) append('\\')
            append(character)
        }
    }

    private companion object {
        const val OUTPUT_PATH: String = "docs/project-specification.md"
        const val MARKDOWN_MEDIA_TYPE: String = "text/markdown"
        val componentComparator: Comparator<ComponentSpecification> =
            compareBy({ it.qualifiedName ?: it.name }, { it.kind }, { it.id })
        val MARKDOWN_SPECIAL_CHARACTERS: Set<Char> =
            setOf('\\', '`', '*', '_', '{', '}', '[', ']', '<', '>', '#', '+', '-', '!', '|')
    }
}

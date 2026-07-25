package io.docpilot.core.render

import io.docpilot.core.api.DocumentationArtifactDescriptor
import io.docpilot.core.api.DocumentationArtifactId
import io.docpilot.core.api.DocumentationArtifactKind
import io.docpilot.core.api.SelectiveSpecificationRenderer
import io.docpilot.core.model.ApiSpecification
import io.docpilot.core.model.ComponentSpecification
import io.docpilot.core.model.Evidence
import io.docpilot.core.model.ModuleSpecification
import io.docpilot.core.model.PackageSpecification
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.PropertySpecification
import io.docpilot.core.model.RenderedArtifact
import io.docpilot.core.model.UnresolvedItem
import io.docpilot.core.specification.RelationshipEndpointSemantics
import java.security.MessageDigest

/**
 * Renders a ProjectSpecification (DIR) as deterministic, reviewable Markdown.
 *
 * This renderer is intentionally presentation-only: it does not inspect source files,
 * SourceIndex, or the Knowledge Graph and it does not infer missing specification data.
 */
public class ProjectSpecificationMarkdownRenderer : SelectiveSpecificationRenderer {
    override fun describe(specification: ProjectSpecification): List<DocumentationArtifactDescriptor> {
        val componentDescriptors = specification.components.map { component ->
            descriptor(
                id = "component:${component.id}",
                path = "docs/specification/components/${safeSegment(component.id)}.md",
                kind = DocumentationArtifactKind.COMPONENT,
                scopes = buildList {
                    add(component.id)
                    addAll(component.apis.map { it.id })
                    addAll(component.properties.map { it.id })
                },
            )
        }
        val packageDescriptors = specification.packages.map { packageSpecification ->
            descriptor(
                id = "package:${packageSpecification.id}",
                path = "docs/specification/packages/${safeSegment(packageSpecification.id)}.md",
                kind = DocumentationArtifactKind.PACKAGE,
                scopes = listOf(packageSpecification.id),
                dependencies = componentDescriptors.filter { descriptor ->
                    specification.components.any {
                        it.packageId == packageSpecification.id &&
                            descriptor.artifactId.value == "component:${it.id}"
                    }
                }.map { it.artifactId },
            )
        }
        val moduleDescriptors = specification.modules.map { module ->
            descriptor(
                id = "module:${module.id}",
                path = "docs/specification/modules/${safeSegment(module.id)}.md",
                kind = DocumentationArtifactKind.MODULE,
                scopes = listOf(module.id),
                dependencies = packageDescriptors.filter { descriptor ->
                    specification.packages.any {
                        it.moduleId == module.id && descriptor.artifactId.value == "package:${it.id}"
                    }
                }.map { it.artifactId } + componentDescriptors.filter { descriptor ->
                    specification.components.any {
                        it.moduleId == module.id && it.packageId == null &&
                            descriptor.artifactId.value == "component:${it.id}"
                    }
                }.map { it.artifactId },
            )
        }
        val relationship = descriptor(
            id = "relationships:${specification.project.id}",
            path = "docs/specification/relationships.md",
            kind = DocumentationArtifactKind.RELATIONSHIP,
            scopes = specification.relationships.map { it.id },
        )
        val evidence = descriptor(
            id = "evidence:${specification.project.id}",
            path = "docs/specification/evidence.md",
            kind = DocumentationArtifactKind.EVIDENCE,
            scopes = specification.evidence.map { it.id } + specification.unresolved.map { it.id },
        )
        val overview = descriptor(
            id = "project:${specification.project.id}",
            path = "docs/specification/project.md",
            kind = DocumentationArtifactKind.PROJECT_OVERVIEW,
            scopes = listOf(specification.project.id),
            dependencies = moduleDescriptors.map { it.artifactId } + listOf(relationship.artifactId, evidence.artifactId),
        )
        val architecture = descriptor(
            id = "architecture:${specification.project.id}",
            path = "docs/architecture/overview.md",
            kind = DocumentationArtifactKind.ARCHITECTURE_OVERVIEW,
            scopes = specification.modules.map { it.id } + specification.relationships.map { it.id },
            dependencies = moduleDescriptors.map { it.artifactId } + listOf(relationship.artifactId),
        )
        val index = descriptor(
            id = "index:${specification.project.id}",
            path = OUTPUT_PATH,
            kind = DocumentationArtifactKind.INDEX,
            scopes = emptyList(),
            dependencies = listOf(overview.artifactId, architecture.artifactId),
        )
        return (listOf(index, overview, architecture) + moduleDescriptors + packageDescriptors +
            componentDescriptors + relationship + evidence).sortedBy { it.artifactId.value }
    }

    override fun render(
        specification: ProjectSpecification,
        artifactIds: Set<DocumentationArtifactId>,
    ): List<RenderedArtifact> {
        val descriptors = describe(specification).associateBy { it.artifactId }
        require(artifactIds.all(descriptors::containsKey)) {
            "Unknown documentation artifact id requested"
        }
        return artifactIds.sortedBy { it.value }.map { artifactId ->
            val descriptor = descriptors.getValue(artifactId)
            RenderedArtifact(
                relativePath = descriptor.relativePath,
                mediaType = descriptor.mediaType,
                content = renderDescriptor(specification, descriptor),
            )
        }.sortedBy { it.relativePath }
    }

    private fun descriptor(
        id: String,
        path: String,
        kind: DocumentationArtifactKind,
        scopes: List<String>,
        dependencies: List<DocumentationArtifactId> = emptyList(),
    ): DocumentationArtifactDescriptor = DocumentationArtifactDescriptor(
        artifactId = DocumentationArtifactId(id),
        relativePath = path,
        mediaType = MARKDOWN_MEDIA_TYPE,
        kind = kind,
        scopeIds = scopes.distinct().sorted(),
        dependencyArtifactIds = dependencies.distinct().sortedBy { it.value },
    )

    private fun renderDescriptor(
        specification: ProjectSpecification,
        descriptor: DocumentationArtifactDescriptor,
    ): String = when (descriptor.kind) {
        DocumentationArtifactKind.INDEX -> buildIndex(specification)
        DocumentationArtifactKind.PROJECT_OVERVIEW -> buildMarkdown(
            specification.copy(modules = emptyList(), packages = emptyList(), components = emptyList()),
        )
        DocumentationArtifactKind.MODULE -> {
            val id = descriptor.scopeIds.single()
            val module = specification.modules.single { it.id == id }
            buildMarkdown(
                specification.copy(
                    modules = listOf(module),
                    packages = emptyList(),
                    components = emptyList(),
                    relationships = emptyList(),
                    evidence = emptyList(),
                    unresolved = emptyList(),
                ),
            )
        }
        DocumentationArtifactKind.PACKAGE -> {
            val id = descriptor.scopeIds.single()
            val pkg = specification.packages.single { it.id == id }
            val module = specification.modules.single { it.id == pkg.moduleId }
            buildMarkdown(
                specification.copy(
                    modules = listOf(module),
                    packages = listOf(pkg),
                    components = emptyList(),
                    relationships = emptyList(),
                    evidence = emptyList(),
                    unresolved = emptyList(),
                ),
            )
        }
        DocumentationArtifactKind.COMPONENT -> {
            val componentId = descriptor.artifactId.value.removePrefix("component:")
            val component = specification.components.single { it.id == componentId }
            val module = specification.modules.single { it.id == component.moduleId }
            buildMarkdown(
                specification.copy(
                    modules = listOf(module),
                    packages = specification.packages.filter { it.id == component.packageId },
                    components = listOf(component),
                    relationships = emptyList(),
                    evidence = emptyList(),
                    unresolved = emptyList(),
                ),
            )
        }
        DocumentationArtifactKind.RELATIONSHIP -> buildMarkdown(
            specification.copy(modules = emptyList(), evidence = emptyList(), unresolved = emptyList()),
        )
        DocumentationArtifactKind.EVIDENCE -> buildMarkdown(
            specification.copy(modules = emptyList(), packages = emptyList(), components = emptyList(),
                relationships = emptyList()),
        )
        DocumentationArtifactKind.ARCHITECTURE_OVERVIEW -> buildArchitectureOverview(specification)
    }

    private fun buildIndex(specification: ProjectSpecification): String = buildString {
        appendLine("# ${escapeText(specification.project.name)} specification")
        appendLine()
        appendLine("Generated documentation is split into deterministic project, module, package, component, relationship, and evidence artifacts.")
        appendLine()
        describe(specification).filter { it.kind != DocumentationArtifactKind.INDEX }
            .sortedBy { it.relativePath }
            .forEach {
                appendLine(
                    "- [${escapeText(indexLabel(specification, it))}](${it.relativePath.removePrefix("docs/")})",
                )
            }
    }

    private fun indexLabel(
        specification: ProjectSpecification,
        descriptor: DocumentationArtifactDescriptor,
    ): String = when (descriptor.kind) {
        DocumentationArtifactKind.COMPONENT -> {
            val id = descriptor.artifactId.value.removePrefix("component:")
            "Component — ${specification.components.single { it.id == id }.qualifiedName ?:
                specification.components.single { it.id == id }.name}"
        }
        DocumentationArtifactKind.MODULE -> "Module — ${specification.modules.single {
            it.id == descriptor.scopeIds.single()
        }.name}"
        DocumentationArtifactKind.PACKAGE -> "Package — ${specification.packages.single {
            it.id == descriptor.scopeIds.single()
        }.qualifiedName}"
        DocumentationArtifactKind.ARCHITECTURE_OVERVIEW -> "Architecture overview"
        DocumentationArtifactKind.PROJECT_OVERVIEW -> "Project facts"
        DocumentationArtifactKind.RELATIONSHIP -> "Relationships"
        DocumentationArtifactKind.EVIDENCE -> "Evidence and unresolved items"
        DocumentationArtifactKind.INDEX -> "Index"
    }

    private fun buildArchitectureOverview(specification: ProjectSpecification): String = buildString {
        appendLine("# ${escapeText(specification.project.name)} architecture overview")
        appendLine()
        appendLine("> Classification: CORE_DERIVED from DIR ${escapeText(specification.schemaVersion)} and source Evidence.")
        appendLine()
        appendLine("## System context")
        appendLine()
        appendLine(
            "The analyzed system contains ${specification.modules.size} modules, " +
                "${specification.packages.size} packages, and ${specification.components.size} components.",
        )
        appendLine(
            "Its detected platform is ${renderCollection(specification.project.platforms)}; " +
                "languages are ${renderCollection(specification.project.languages)}; " +
                "build systems are ${renderCollection(specification.project.buildSystems)}.",
        )
        appendLine()
        appendLine("## Module boundaries")
        appendLine()
        specification.modules.sortedBy { it.id }.forEach { module ->
            val componentCount = specification.components.count { it.moduleId == module.id }
            val packageCount = specification.packages.count { it.moduleId == module.id }
            appendLine("- **${escapeText(module.name)}** — $packageCount packages, $componentCount components; " +
                "source sets: ${renderCollection(module.sourceSets)}.")
        }
        if (specification.modules.isEmpty()) appendLine("- No module boundary was observed.")
        appendLine()
        appendLine("## Relationship profile")
        appendLine()
        specification.relationships.groupingBy { it.type }.eachCount().toSortedMap().forEach { (type, count) ->
            appendLine("- **${escapeText(type)}**: $count")
        }
        if (specification.relationships.isEmpty()) appendLine("- No relationship was observed.")
        appendLine()
        appendLine("## External boundaries")
        appendLine()
        val external = specification.relationships.filter { it.targetId.startsWith("external:") }
            .groupingBy { it.targetId.removePrefix("external:").substringBefore('.') }
            .eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        external.take(12).forEach { appendLine("- ${code(it.key)}: ${it.value} relationships") }
        if (external.isEmpty()) appendLine("- No external boundary was observed.")
        appendLine()
        appendLine("## Risks, constraints, and unknowns")
        appendLine()
        appendLine("- Observed unresolved items: ${specification.unresolved.size}.")
        appendLine("- Architecture intent, business rationale, deployment topology, and operational SLOs are UNKNOWN unless supplied as separate Evidence.")
        appendLine("- Import and call volume describes static source relationships; it does not prove runtime frequency or criticality.")
        appendLine()
        appendLine("## Navigation")
        appendLine()
        appendLine("- [Project facts](../specification/project.md)")
        appendLine("- [Relationships](../specification/relationships.md)")
        appendLine("- [Evidence and unresolved items](../specification/evidence.md)")
        appendLine("- [Artifact index](../project-specification.md)")
    }

    private fun safeSegment(id: String): String =
        MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
            .take(12).joinToString("") { "%02x".format(it) }

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
        val internalEndpointIds = buildSet {
            addAll(specification.modules.map { it.id })
            addAll(specification.packages.map { it.id })
            specification.components.forEach { component ->
                add(component.id)
                addAll(component.apis.map { it.id })
                addAll(component.properties.map { it.id })
            }
        }
        relationships.forEach { relationship ->
            appendLine("- ${code(relationship.sourceId)} -> **${escapeText(relationship.type)}** -> ${code(relationship.targetId)}")
            appendLine("  - ID: ${code(relationship.id)}")
            appendLine("  - Source kind: ${RelationshipEndpointSemantics.kindOf(relationship.sourceId, internalEndpointIds).name}")
            appendLine("  - Target kind: ${RelationshipEndpointSemantics.kindOf(relationship.targetId, internalEndpointIds).name}")
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

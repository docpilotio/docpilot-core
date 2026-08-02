package io.docpilot.core.render

import io.docpilot.core.api.DocumentationArtifactDescriptor
import io.docpilot.core.api.DocumentationArtifactId
import io.docpilot.core.api.DocumentationArtifactKind
import io.docpilot.core.api.SelectiveSpecificationRenderer
import io.docpilot.core.documentation.profile.DocumentationRendererCapabilityProvider
import io.docpilot.core.documentation.profile.RendererCapability
import io.docpilot.core.model.ContractKind
import io.docpilot.core.model.ContractSpecification
import io.docpilot.core.model.Evidence
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.RenderedArtifact

/**
 * RFC-0077: renders the five `kotlin-android@1` Profile document types (RFC-0058) that
 * previously had no renderer coverage — Module Architecture, Domain Model, Database Schema,
 * External API Contract, and Test Strategy. Consumes only the persisted `ProjectSpecification`;
 * it does not rescan source, infer entities, or classify Contract roles. RFC-0066 remains the
 * sole authority for Contract role/kind classification, so Domain Model, Database Schema, and
 * External API Contract may legitimately render as empty documents when extraction has not yet
 * populated the corresponding Contract kind for a given project.
 */
public class ProfileDocumentCoverageMarkdownRenderer :
    SelectiveSpecificationRenderer,
    DocumentationRendererCapabilityProvider {

    override fun capabilities(): Set<RendererCapability> = setOf(
        RendererCapability.MARKDOWN_SECTION_RENDERING,
        RendererCapability.EVIDENCE_REFERENCE_RENDERING,
        RendererCapability.UNKNOWN_FINDING_RENDERING,
    )

    override fun describe(specification: ProjectSpecification): List<DocumentationArtifactDescriptor> {
        val moduleArchitecture = descriptor(
            id = "module-architecture:${specification.project.id}",
            path = "docs/architecture/module-architecture.md",
            kind = DocumentationArtifactKind.MODULE_ARCHITECTURE,
            scopes = specification.modules.map { it.id } + specification.packages.map { it.id },
        )
        val domainModel = contractFilteredDescriptor(
            specification, DocumentationArtifactKind.DOMAIN_MODEL,
            "domain-model:${specification.project.id}", "docs/contracts/domain-model.md", ContractKind.DATA,
        )
        val databaseSchema = contractFilteredDescriptor(
            specification, DocumentationArtifactKind.DATABASE_SCHEMA,
            "database-schema:${specification.project.id}", "docs/contracts/database-schema.md", ContractKind.PERSISTENCE,
        )
        val externalApiContract = contractFilteredDescriptor(
            specification, DocumentationArtifactKind.EXTERNAL_API_CONTRACT,
            "external-api-contract:${specification.project.id}", "docs/contracts/external-apis.md", ContractKind.EXTERNAL,
        )
        val testStrategy = descriptor(
            id = "test-strategy:${specification.project.id}",
            path = "docs/quality/test-strategy.md",
            kind = DocumentationArtifactKind.TEST_STRATEGY,
            scopes = specification.modules.map { it.id } + testEvidence(specification).map { it.id },
        )
        return listOf(moduleArchitecture, domainModel, databaseSchema, externalApiContract, testStrategy)
            .sortedBy { it.artifactId.value }
    }

    override fun render(
        specification: ProjectSpecification,
        artifactIds: Set<DocumentationArtifactId>,
    ): List<RenderedArtifact> {
        val descriptors = describe(specification).associateBy { it.artifactId }
        require(artifactIds.all(descriptors::containsKey)) { "Unknown Profile document artifact id requested." }
        return artifactIds.map { id ->
            val descriptor = descriptors.getValue(id)
            val content = when (descriptor.kind) {
                DocumentationArtifactKind.MODULE_ARCHITECTURE -> renderModuleArchitecture(specification)
                DocumentationArtifactKind.DOMAIN_MODEL -> renderContractDocument(
                    specification, "Domain Model", "Domain Concepts", ContractKind.DATA,
                )
                DocumentationArtifactKind.DATABASE_SCHEMA -> renderContractDocument(
                    specification, "Database Schema", "Schema Inventory", ContractKind.PERSISTENCE,
                )
                DocumentationArtifactKind.EXTERNAL_API_CONTRACT -> renderContractDocument(
                    specification, "External API Contract", "External APIs", ContractKind.EXTERNAL,
                )
                DocumentationArtifactKind.TEST_STRATEGY -> renderTestStrategy(specification)
                else -> error("Unsupported Profile document artifact kind: ${descriptor.kind}")
            }
            RenderedArtifact(descriptor.relativePath, descriptor.mediaType, content)
        }.sortedBy { it.relativePath }
    }

    // ---- Module Architecture ----

    private fun renderModuleArchitecture(specification: ProjectSpecification): String = buildString {
        val modules = specification.modules.sortedWith(compareBy({ it.path ?: "" }, { it.name }, { it.id }))
        appendLine("# Module Architecture")
        appendLine()
        appendLine(
            "Generated deterministically from DIR ${specification.schemaVersion} module, package, and component " +
                "facts. No source scan or inferred boundary is used.",
        )
        appendLine()
        appendLine("## Overview")
        appendLine()
        field("Specification", specification.project.id)
        field("DIR schema", specification.schemaVersion)
        appendLine("- Modules: ${specification.modules.size}")
        appendLine()
        appendLine("## Module Inventory")
        appendLine()
        if (modules.isEmpty()) {
            appendLine("None.")
        } else {
            appendLine("| Module | Path | Packages | Components | Source Sets |")
            appendLine("|---|---|---:|---:|---|")
            modules.forEach { module ->
                val packageCount = specification.packages.count { it.moduleId == module.id }
                val componentCount = specification.components.count { it.moduleId == module.id }
                val sourceSets = module.sourceSets.sorted().joinToString(", ").ifBlank { "None" }
                appendLine(
                    "| ${cell(module.name)} | ${cell(module.path ?: "Unspecified")} | $packageCount | " +
                        "$componentCount | $sourceSets |",
                )
            }
        }
        appendLine()
        appendLine("## Package and Component Boundaries")
        appendLine()
        if (modules.isEmpty()) appendLine("None.")
        modules.forEach { module ->
            appendLine("### ${escapeText(module.name)}")
            appendLine()
            val packages = specification.packages.filter { it.moduleId == module.id }
                .sortedWith(compareBy({ it.qualifiedName }, { it.id }))
            if (packages.isEmpty()) {
                appendLine("- No package boundary was observed.")
            } else {
                packages.forEach { pkg ->
                    val componentCount = specification.components.count { it.packageId == pkg.id }
                    appendLine("- ${code(pkg.qualifiedName.ifBlank { "(default)" })} — $componentCount components")
                }
            }
            val unassigned = specification.components.count { it.moduleId == module.id && it.packageId == null }
            if (unassigned > 0) appendLine("- Unassigned types (no package): $unassigned")
            appendLine()
        }
        appendLine("## Dependencies")
        appendLine()
        val moduleIds = specification.modules.map { it.id }.toSet()
        val interModule = specification.relationships
            .filter { it.sourceId in moduleIds && it.targetId in moduleIds }
            .sortedWith(compareBy({ it.sourceId }, { it.type }, { it.targetId }, { it.id }))
        if (interModule.isEmpty()) {
            appendLine("- No inter-module dependency relationship was observed.")
        } else {
            val moduleNameById = specification.modules.associate { it.id to it.name }
            interModule.forEach { relationship ->
                appendLine(
                    "- ${code(moduleNameById.getValue(relationship.sourceId))} — " +
                        "**${escapeText(relationship.type)}** → ${code(moduleNameById.getValue(relationship.targetId))} " +
                        "(Evidence ${relationship.evidenceRefs.size})",
                )
            }
        }
        appendLine()
        appendLine("## Evidence")
        appendLine()
        val evidenceById = specification.evidence.associateBy { it.id }
        val evidenceRefs = (
            modules.flatMap { it.evidenceRefs } +
                specification.packages.filter { it.moduleId in moduleIds }.flatMap { it.evidenceRefs }
            ).distinct().sorted()
        val evidence = evidenceRefs.mapNotNull(evidenceById::get)
        if (evidence.isEmpty()) {
            appendLine("None.")
        } else {
            evidence.take(EVIDENCE_DETAIL_LIMIT).forEach { renderEvidenceLine(it) }
            if (evidence.size > EVIDENCE_DETAIL_LIMIT) {
                appendLine(
                    "- ${evidence.size - EVIDENCE_DETAIL_LIMIT} additional Evidence entries are available in the " +
                        "detailed Module and Package Artifacts.",
                )
            }
        }
        appendLine()
        appendLine("## Unknowns")
        appendLine()
        appendLine(
            "- Observed unresolved items: ${specification.unresolved.size}. See " +
                "[Evidence and unresolved items](../specification/evidence.md) for the full list.",
        )
    }

    // ---- Domain Model / Database Schema / External API Contract ----

    private fun contractFilteredDescriptor(
        specification: ProjectSpecification,
        kind: DocumentationArtifactKind,
        id: String,
        path: String,
        contractKind: ContractKind,
    ): DocumentationArtifactDescriptor {
        val contracts = specification.contracts.filter { it.kind == contractKind }
        return descriptor(id, path, kind, contracts.flatMap(::contractScope).distinct().sorted())
    }

    private fun contractScope(contract: ContractSpecification): List<String> = buildList {
        add(contract.id)
        add(contract.owner.stableId)
        addAll(contract.inputs.map { it.id })
        addAll(contract.outputs.map { it.id })
        addAll(contract.members.map { it.id })
        addAll(contract.relationships.map { it.id })
        addAll(contract.evidenceRefs)
        addAll(contract.unresolvedRefs)
    }.distinct().sorted()

    private fun renderContractDocument(
        specification: ProjectSpecification,
        title: String,
        inventoryTitle: String,
        contractKind: ContractKind,
    ): String = buildString {
        val contracts = specification.contracts.filter { it.kind == contractKind }.sortedBy { it.id }
        appendLine("# $title")
        appendLine()
        appendLine(
            "Generated deterministically from canonical DIR 0.5 Contracts filtered to kind `$contractKind`. " +
                "No source scan or inferred facts are used.",
        )
        appendLine()
        appendLine("## Overview")
        appendLine()
        field("Specification", specification.project.id)
        field("DIR schema", specification.schemaVersion)
        field("Profile", "kotlin-android@1")
        appendLine("- Matching Contracts: ${contracts.size}")
        appendLine()
        appendLine("## $inventoryTitle")
        appendLine()
        if (contracts.isEmpty()) {
            appendLine(
                "None. No Contract in the current DIR 0.5 collection is classified under kind `$contractKind`. " +
                    "This is a legitimately empty deterministic result under RFC-0065 Integrity policy — it " +
                    "reflects current RFC-0066 extraction coverage for this project, not a rendering failure.",
            )
        } else {
            appendLine("| Stable ID | Display Name | Role | Owner | Evidence | Unresolved |")
            appendLine("|---|---|---|---|---:|---:|")
            contracts.forEach { contract ->
                appendLine(
                    "| ${cell(contract.id)} | ${cell(contract.displayName.ifBlank { contract.id })} | " +
                        "${contract.role} | ${cell(contract.owner.stableId)} | ${contract.evidenceRefs.size} | " +
                        "${contract.unresolvedRefs.size} |",
                )
            }
            appendLine()
            appendLine("See [Contract Catalog](catalog.md) for full per-Contract detail navigation.")
        }
        appendLine()
        appendLine("## Relationships")
        appendLine()
        val relationships = contracts.flatMap { contract -> contract.relationships.map { contract to it } }
            .sortedBy { it.second.id }
        if (relationships.isEmpty()) {
            appendLine("None.")
        } else {
            relationships.forEach { (contract, relationship) ->
                appendLine("- ${code(relationship.kind.name)} from ${code(contract.id)} to ${code(relationship.targetStableId)}")
            }
        }
        appendLine()
        appendLine("## Constraints")
        appendLine()
        val unresolvedRefs = contracts.flatMap { it.unresolvedRefs }.distinct().sorted()
        if (unresolvedRefs.isEmpty()) {
            appendLine("None.")
        } else {
            appendLine("- Unresolved references present: ${unresolvedRefs.size}. See Unknowns for detail.")
        }
        appendLine()
        appendLine("## Evidence")
        appendLine()
        val evidenceById = specification.evidence.associateBy { it.id }
        val evidence = contracts.flatMap { it.evidenceRefs }.distinct().sorted().mapNotNull(evidenceById::get)
        if (evidence.isEmpty()) appendLine("None.") else evidence.forEach { renderEvidenceLine(it) }
        appendLine()
        appendLine("## Unknowns")
        appendLine()
        val unresolvedById = specification.unresolved.associateBy { it.id }
        val unresolvedItems = unresolvedRefs.mapNotNull(unresolvedById::get)
        if (unresolvedItems.isEmpty()) {
            appendLine("None.")
        } else {
            unresolvedItems.forEach { appendLine("- ${code(it.id)} — ${escapeText(it.question)}") }
        }
    }

    // ---- Test Strategy ----

    private fun testEvidence(specification: ProjectSpecification): List<Evidence> =
        specification.evidence.filter { evidence ->
            val file = evidence.file?.replace('\\', '/') ?: return@filter false
            file.contains("/test/") || file.contains("/androidTest/")
        }

    private fun renderTestStrategy(specification: ProjectSpecification): String = buildString {
        val testEv = testEvidence(specification).sortedBy { it.id }
        val unitTestFiles = testEv.filter { it.file!!.contains("/test/") }.mapNotNull { it.file }.distinct().sorted()
        val instrumentedTestFiles = testEv.filter { it.file!!.contains("/androidTest/") }
            .mapNotNull { it.file }.distinct().sorted()
        val modules = specification.modules.sortedBy { it.id }
        appendLine("# Test Strategy")
        appendLine()
        appendLine(
            "Generated deterministically from DIR ${specification.schemaVersion} module source sets and " +
                "file-path Evidence. No test execution result, coverage tool output, or CI log is consulted.",
        )
        appendLine()
        appendLine("## Overview")
        appendLine()
        field("Specification", specification.project.id)
        field("DIR schema", specification.schemaVersion)
        appendLine("- Distinct JVM unit test files (path contains `/test/`): ${unitTestFiles.size}")
        appendLine("- Distinct instrumented test files (path contains `/androidTest/`): ${instrumentedTestFiles.size}")
        appendLine()
        appendLine("## Observed Test Boundaries")
        appendLine()
        if (modules.isEmpty()) {
            appendLine("None.")
        } else {
            modules.forEach { module ->
                val prefix = "${(module.path ?: module.name).trim('/')}/"
                val testSourceSets = module.sourceSets.filter { it.contains("test", ignoreCase = true) }.sorted()
                val moduleUnit = unitTestFiles.count { it.startsWith(prefix) }
                val moduleInstrumented = instrumentedTestFiles.count { it.startsWith(prefix) }
                appendLine(
                    "- ${code(module.name)} — test source sets: ${testSourceSets.joinToString(", ").ifBlank { "None observed" }}; " +
                        "JVM unit test files: $moduleUnit; instrumented test files: $moduleInstrumented",
                )
            }
        }
        appendLine()
        appendLine("## Test Levels")
        appendLine()
        appendLine("- **JVM unit test** (`src/test`-style path): ${unitTestFiles.size} distinct files.")
        appendLine("- **Instrumented/Android test** (`src/androidTest`-style path): ${instrumentedTestFiles.size} distinct files.")
        if (unitTestFiles.isEmpty() && instrumentedTestFiles.isEmpty()) appendLine("- No test-path Evidence was observed.")
        appendLine()
        appendLine("## Execution")
        appendLine()
        appendLine(
            "- UNKNOWN: DocPilot does not consult CI configuration, build logs, or execution results. Pass/fail " +
                "state, execution frequency, and release-gate behavior are not represented by the current DIR.",
        )
        appendLine()
        appendLine("## Coverage Gaps")
        appendLine()
        val modulesWithoutTests = modules.filter { module ->
            val prefix = "${(module.path ?: module.name).trim('/')}/"
            unitTestFiles.none { it.startsWith(prefix) } && instrumentedTestFiles.none { it.startsWith(prefix) }
        }
        if (modules.isNotEmpty() && modulesWithoutTests.isEmpty()) {
            appendLine("- Every module has at least one observed test-path Evidence entry.")
        } else if (modulesWithoutTests.isNotEmpty()) {
            appendLine("- Modules with no observed test-path Evidence: ${modulesWithoutTests.joinToString(", ") { code(it.name) }}.")
        }
        appendLine(
            "- UNKNOWN: line/branch coverage percentage requires an external coverage tool; the current DIR " +
                "does not ingest one.",
        )
        appendLine()
        appendLine("## Evidence")
        appendLine()
        if (testEv.isEmpty()) {
            appendLine("None.")
        } else {
            testEv.take(EVIDENCE_DETAIL_LIMIT).forEach { renderEvidenceLine(it) }
            if (testEv.size > EVIDENCE_DETAIL_LIMIT) {
                appendLine(
                    "- ${testEv.size - EVIDENCE_DETAIL_LIMIT} additional test-path Evidence entries exist; see " +
                        "[Evidence and unresolved items](../specification/evidence.md).",
                )
            }
        }
    }

    // ---- Shared helpers ----

    private fun descriptor(
        id: String,
        path: String,
        kind: DocumentationArtifactKind,
        scopes: List<String>,
    ): DocumentationArtifactDescriptor = DocumentationArtifactDescriptor(
        artifactId = DocumentationArtifactId(id),
        relativePath = path,
        mediaType = MARKDOWN_MEDIA_TYPE,
        kind = kind,
        scopeIds = scopes.distinct().sorted(),
    )

    private fun StringBuilder.renderEvidenceLine(value: Evidence) {
        val location = buildString {
            append(value.file?.let(::code) ?: "location Unspecified")
            value.symbol?.let { append("; symbol ${code(it)}") }
            value.lineStart?.let { start ->
                append("; lines $start")
                value.lineEnd?.takeIf { end -> end != start }?.let { end -> append("-$end") }
            }
        }
        appendLine("- ${code(value.id)} — ${escapeText(value.summary)}; $location")
    }

    private fun StringBuilder.field(label: String, value: String) {
        appendLine("- $label: ${code(value)}")
    }

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

    private fun cell(value: String): String = escapeText(value).replace("|", "\\|")

    private companion object {
        const val MARKDOWN_MEDIA_TYPE: String = "text/markdown"
        const val EVIDENCE_DETAIL_LIMIT: Int = 40
        val MARKDOWN_SPECIAL_CHARACTERS: Set<Char> =
            setOf('\\', '`', '*', '_', '{', '}', '[', ']', '<', '>', '#', '+', '-', '!', '|')
    }
}

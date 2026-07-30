package io.docpilot.core.specification

import io.docpilot.core.api.SpecificationBuilder
import io.docpilot.core.model.ApiSpecification
import io.docpilot.core.model.ComponentSpecification
import io.docpilot.core.model.ModuleSpecification
import io.docpilot.core.model.PackageSpecification
import io.docpilot.core.model.ParameterSpecification
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.PropertySpecification
import io.docpilot.core.model.RelationshipSpecification
import io.docpilot.core.model.UnresolvedItem
import io.docpilot.core.model.knowledge.KnowledgeNode
import io.docpilot.core.model.source.SourceFile
import io.docpilot.core.model.source.SourceIndex
import io.docpilot.core.model.source.SourceSymbol
import io.docpilot.core.model.source.SourceSymbolKind
import io.docpilot.core.specification.discovery.DeterministicFeatureDiscoveryEngine

public class DefaultSpecificationBuilder : SpecificationBuilder {
    override fun build(request: SpecificationBuildRequest): ProjectSpecification =
        buildWithReport(request).specification

    public fun buildWithReport(
        request: SpecificationBuildRequest,
        policy: RelationshipProjectionPolicy = RelationshipProjectionPolicy(),
    ): SpecificationBuildResult {
        val sourceIndex = request.sourceIndex
        val nodeById = request.knowledge.graph.nodes.associateBy { it.id }
        val nodeByQualifiedName = request.knowledge.graph.nodes
            .mapNotNull { node -> node.attributes["qualifiedName"]?.let { it to node } }
            .toMap()

        val modules = buildModules(sourceIndex, nodeById)
        val packages = buildPackages(sourceIndex, modules, nodeById)
        val packageByFile = sourceIndex?.files.orEmpty().associate { file ->
            file.relativePath to packageId(moduleId(file), file.packageName)
        }

        val components = mutableListOf<ComponentSpecification>()
        val symbolSpecIdByNodeId = mutableMapOf<String, String>()
        val unresolved = mutableListOf<UnresolvedItem>()

        sourceIndex?.files.orEmpty().sortedBy { it.relativePath }.forEach { file ->
            file.symbols.forEachIndexed { index, symbol ->
                collectComponents(
                    file = file,
                    symbol = symbol,
                    siblingIndex = index,
                    ownerComponentId = null,
                    packageId = packageByFile.getValue(file.relativePath),
                    nodeById = nodeById,
                    nodeByQualifiedName = nodeByQualifiedName,
                    components = components,
                    symbolSpecIdByNodeId = symbolSpecIdByNodeId,
                    unresolved = unresolved,
                )
            }
        }

        request.knowledge.graph.unresolved.sortedBy { it.id }.forEach { item ->
            unresolved += UnresolvedItem(
                id = "knowledge:${item.id}",
                subject = item.subjectNodeId ?: request.project.id,
                question = item.question,
                requiredAction = item.reason,
            )
        }
        sourceIndex?.failures.orEmpty().sortedBy { it.relativePath }.forEachIndexed { index, failure ->
            unresolved += UnresolvedItem(
                id = "source-index:${failure.relativePath}:$index",
                subject = failure.relativePath,
                question = "Why could this source file not be indexed?",
                requiredAction = failure.message,
            )
        }

        val resolver = RelationshipEndpointResolver(
            files = sourceIndex?.files.orEmpty(),
            modules = modules,
            packages = packages,
            components = components,
            nodesById = nodeById,
            symbolSpecIdByNodeId = symbolSpecIdByNodeId,
        )

        val relationshipCandidates = request.knowledge.graph.edges
            .filter { edge -> edge.relationship.name in SemanticRelationshipKind.entries.map { it.name } }
            .flatMap { edge ->
                val sourceId = resolver.resolve(edge.sourceNodeId, edge.targetNodeId, "source")
                val targetId = resolver.resolve(edge.targetNodeId, edge.sourceNodeId, "target")
                val relationship = RelationshipSpecification(
                    id = RelationshipIdentity.of(edge.relationship.name, sourceId, targetId),
                    type = edge.relationship.name,
                    sourceId = sourceId,
                    targetId = targetId,
                    description = "${edge.relationship.name} relationship from $sourceId to $targetId.",
                    evidenceRefs = edge.evidenceRefs.toSortedSet(),
                )
                List(edge.attributes["occurrenceCount"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1) {
                    relationship
                }
            }
            .filterNot { relationship ->
                relationship.sourceId == relationship.targetId && relationship.type != "CALLS"
            }
        val projection = RelationshipProjector.project(relationshipCandidates, policy)
        val relationships = projection.relationships

        relationships.flatMap { listOf(it.sourceId, it.targetId) }
            .filter { it.startsWith(RelationshipEndpointSemantics.UNRESOLVED_PREFIX) }
            .map { it.removePrefix(RelationshipEndpointSemantics.UNRESOLVED_PREFIX).substringBeforeLast(':') }
            .filter(String::isNotBlank)
            .distinct()
            .forEach { reference ->
                if (unresolved.none { it.id == reference }) {
                    unresolved += UnresolvedItem(
                        id = reference,
                        subject = reference,
                        question = "Resolve relationship endpoint $reference.",
                    )
                }
            }

        val dependencyIdsByComponent = relationships
            .asSequence()
            .filter { it.type == "DEPENDS_ON" }
            .filter { it.sourceId in components.map(ComponentSpecification::id) }
            .filterNot { it.targetId.startsWith(RelationshipEndpointSemantics.UNRESOLVED_PREFIX) }
            .groupBy(RelationshipSpecification::sourceId)
            .mapValues { (_, values) -> values.mapTo(sortedSetOf(), RelationshipSpecification::targetId) }
        val componentsWithDependencies = components.map { component ->
            component.copy(dependencyIds = dependencyIdsByComponent[component.id].orEmpty())
        }

        val baseSpecification = ProjectSpecification(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            project = request.project,
            modules = modules,
            packages = packages,
            components = componentsWithDependencies.sortedWith(compareBy({ it.moduleId }, { it.qualifiedName ?: it.name }, { it.id })),
            relationships = relationships,
            evidence = request.knowledge.evidence.items.sortedBy { it.id.value }.map(DirEvidenceMapper::map),
            unresolved = unresolved.distinctBy { it.id }.sortedBy { it.id },
        )
        val discovery = DeterministicFeatureDiscoveryEngine().discover(
            graph = request.knowledge.graph,
            baseSpecification = baseSpecification,
            sourceIndex = request.sourceIndex,
        )
        val specification = baseSpecification.copy(
            features = discovery.features,
            entryPoints = discovery.entryPoints,
            scenarios = discovery.scenarios,
            unresolved = (baseSpecification.unresolved + discovery.unresolved)
                .distinctBy { it.id }
                .sortedBy { it.id },
        )
        ProjectSpecificationValidator.validate(specification)
        return SpecificationBuildResult(specification, projection.report)
    }

    private fun buildModules(sourceIndex: SourceIndex?, nodeById: Map<String, KnowledgeNode>): List<ModuleSpecification> {
        val files = sourceIndex?.files.orEmpty()
        val grouped = if (files.isEmpty()) mapOf("root" to emptyList()) else files.groupBy(::modulePath)
        return grouped.entries.map { (path, moduleFiles) ->
            ModuleSpecification(
                id = moduleId(path),
                name = if (path == "root") "root" else path.substringAfterLast('/'),
                path = path.takeUnless { it == "root" },
                description = "Source module $path.",
                sourceSets = moduleFiles.mapNotNull { it.sourceSetName }.toSortedSet(),
                evidenceRefs = moduleFiles.flatMap { nodeById["file:${it.relativePath}"]?.evidenceRefs.orEmpty() }.toSortedSet(),
            )
        }.sortedWith(compareBy({ it.path ?: "" }, { it.id }))
    }

    private fun buildPackages(
        sourceIndex: SourceIndex?,
        modules: List<ModuleSpecification>,
        nodeById: Map<String, KnowledgeNode>,
    ): List<PackageSpecification> {
        val files = sourceIndex?.files.orEmpty()
        if (files.isEmpty()) return emptyList()
        return files.groupBy { moduleId(it) to (it.packageName ?: "") }.map { (key, packageFiles) ->
            val (moduleId, qualifiedName) = key
            val refs = packageFiles.flatMap { file ->
                if (qualifiedName.isBlank()) nodeById["file:${file.relativePath}"]?.evidenceRefs.orEmpty()
                else nodeById["package:$qualifiedName"]?.evidenceRefs.orEmpty()
            }.toSortedSet()
            PackageSpecification(
                id = packageId(moduleId, qualifiedName.ifBlank { null }),
                name = qualifiedName.substringAfterLast('.').ifBlank { "(default)" },
                qualifiedName = qualifiedName,
                moduleId = moduleId,
                description = if (qualifiedName.isBlank()) "Default package." else "Package $qualifiedName.",
                evidenceRefs = refs,
            )
        }.filter { it.moduleId in modules.map(ModuleSpecification::id) }
            .sortedWith(compareBy({ it.qualifiedName }, { it.id }))
    }

    private fun collectComponents(
        file: SourceFile,
        symbol: SourceSymbol,
        siblingIndex: Int,
        ownerComponentId: String?,
        packageId: String,
        nodeById: Map<String, KnowledgeNode>,
        nodeByQualifiedName: Map<String, KnowledgeNode>,
        components: MutableList<ComponentSpecification>,
        symbolSpecIdByNodeId: MutableMap<String, String>,
        unresolved: MutableList<UnresolvedItem>,
    ) {
        val node = findNode(file, symbol, siblingIndex, nodeById, nodeByQualifiedName)
        val specId = node?.id ?: fallbackSymbolId(file, symbol, siblingIndex)
        if (node != null) symbolSpecIdByNodeId[node.id] = specId

        if (symbol.kind.isComponentKind()) {
            val apis = symbol.children.mapIndexedNotNull { index, child ->
                if (child.kind == SourceSymbolKind.FUNCTION || child.kind == SourceSymbolKind.CONSTRUCTOR) {
                    val childNode = findNode(file, child, index, nodeById, nodeByQualifiedName)
                    val childId = childNode?.id ?: fallbackSymbolId(file, child, index)
                    if (childNode != null) symbolSpecIdByNodeId[childNode.id] = childId
                    child.toApi(childId, childNode)
                } else null
            }.sortedWith(compareBy({ lineOf(it.id, nodeById) }, { it.signature ?: it.name }, { it.id }))
            val properties = symbol.children.mapIndexedNotNull { index, child ->
                if (child.kind == SourceSymbolKind.PROPERTY) {
                    val childNode = findNode(file, child, index, nodeById, nodeByQualifiedName)
                    val childId = childNode?.id ?: fallbackSymbolId(file, child, index)
                    if (childNode != null) symbolSpecIdByNodeId[childNode.id] = childId
                    child.toProperty(childId, childNode)
                } else null
            }.sortedWith(compareBy({ lineOf(it.id, nodeById) }, { it.name }, { it.id }))

            components += ComponentSpecification(
                id = specId,
                name = symbol.name,
                moduleId = moduleId(file),
                packageId = packageId,
                qualifiedName = symbol.qualifiedName,
                kind = symbol.kind.name,
                role = "Declared ${symbol.kind.name.lowercase()} ${symbol.qualifiedName ?: symbol.name}.",
                visibility = symbol.visibility.name,
                modifiers = symbol.modifiers.mapTo(sortedSetOf()) { it.name },
                annotations = symbol.annotations.sorted(),
                typeParameters = symbol.typeParameters,
                superTypes = symbol.superTypes,
                apis = apis,
                properties = properties,
                evidenceRefs = node?.evidenceRefs.orEmpty().toSortedSet(),
            )
        } else if (ownerComponentId == null && symbol.kind in setOf(SourceSymbolKind.FUNCTION, SourceSymbolKind.CONSTRUCTOR, SourceSymbolKind.PROPERTY)) {
            unresolved += UnresolvedItem(
                id = "unsupported-top-level:$specId",
                subject = specId,
                question = "How should this top-level ${symbol.kind.name.lowercase()} be represented in DIR?",
                requiredAction = "Add a top-level declaration model in a future RFC if renderer support is required.",
            )
        }

        val nextOwner = if (symbol.kind.isComponentKind()) specId else ownerComponentId
        symbol.children.forEachIndexed { index, child ->
            if (child.kind.isComponentKind()) {
                collectComponents(file, child, index, nextOwner, packageId, nodeById, nodeByQualifiedName, components, symbolSpecIdByNodeId, unresolved)
            }
        }
    }

    private fun SourceSymbol.toApi(id: String, node: KnowledgeNode?) = ApiSpecification(
        id = id,
        name = name,
        kind = kind.name.lowercase(),
        signature = signature,
        visibility = visibility.name,
        receiverType = receiverType,
        returnType = type,
        parameters = parameters.map { ParameterSpecification(it.name, it.type, it.hasDefaultValue) },
        modifiers = modifiers.mapTo(sortedSetOf()) { it.name },
        annotations = annotations.sorted(),
        purpose = "${kind.name} $name is declared.",
        evidenceRefs = node?.evidenceRefs.orEmpty().toSortedSet(),
    )

    private fun SourceSymbol.toProperty(id: String, node: KnowledgeNode?) = PropertySpecification(
        id = id,
        name = name,
        type = type,
        visibility = visibility.name,
        mutable = mutable,
        hasInitializer = hasInitializer,
        modifiers = modifiers.mapTo(sortedSetOf()) { it.name },
        annotations = annotations.sorted(),
        purpose = "PROPERTY $name is declared.",
        evidenceRefs = node?.evidenceRefs.orEmpty().toSortedSet(),
    )

    private fun findNode(file: SourceFile, symbol: SourceSymbol, index: Int, byId: Map<String, KnowledgeNode>, byQualifiedName: Map<String, KnowledgeNode>): KnowledgeNode? =
        symbol.id.takeIf { it.isNotBlank() }?.let { byId["symbol:$it"] }
            ?: symbol.qualifiedName?.let(byQualifiedName::get)
            ?: byId[fallbackSymbolId(file, symbol, index)]

    private fun fallbackSymbolId(file: SourceFile, symbol: SourceSymbol, index: Int): String =
        "symbol:${file.relativePath}:${symbol.kind.name}:${symbol.name}:${symbol.location?.lineStart ?: "unknown"}:$index"

    private fun lineOf(id: String, nodeById: Map<String, KnowledgeNode>): Int = nodeById[id]?.attributes?.get("lineStart")?.toIntOrNull() ?: Int.MAX_VALUE

    private fun SourceSymbolKind.isComponentKind(): Boolean = this in setOf(
        SourceSymbolKind.CLASS, SourceSymbolKind.INTERFACE, SourceSymbolKind.OBJECT,
        SourceSymbolKind.ENUM_CLASS, SourceSymbolKind.ANNOTATION_CLASS, SourceSymbolKind.TYPE_ALIAS,
    )

    private fun modulePath(file: SourceFile): String = file.candidateModulePath?.trim('/')?.ifBlank { null } ?: "root"
    private fun moduleId(file: SourceFile): String = moduleId(modulePath(file))
    private fun moduleId(path: String): String = "module:$path"
    private fun packageId(moduleId: String, packageName: String?): String = "$moduleId:package:${packageName ?: "<default>"}"

    public companion object {
        public const val CURRENT_SCHEMA_VERSION: String = "0.4"
    }
}

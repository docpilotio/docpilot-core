package io.docpilot.core.knowledge

import io.docpilot.core.api.KnowledgeGraphBuilder
import io.docpilot.core.model.evidence.Evidence
import io.docpilot.core.model.evidence.EvidenceCollection
import io.docpilot.core.model.evidence.EvidenceId
import io.docpilot.core.model.evidence.EvidenceLocation
import io.docpilot.core.model.evidence.EvidenceType
import io.docpilot.core.model.knowledge.KnowledgeBuildResult
import io.docpilot.core.model.knowledge.KnowledgeEdge
import io.docpilot.core.model.knowledge.KnowledgeGraph
import io.docpilot.core.model.knowledge.KnowledgeNode
import io.docpilot.core.model.knowledge.KnowledgeNodeKind
import io.docpilot.core.model.knowledge.RelationshipType
import io.docpilot.core.model.source.SourceFile
import io.docpilot.core.model.source.SourceImport
import io.docpilot.core.model.source.SourceIndex
import io.docpilot.core.model.source.SourceSymbol
import io.docpilot.core.model.source.SourceSymbolKind
import io.docpilot.core.model.source.SourceSuperTypeKind
import io.docpilot.core.model.source.ComposeNavigationArgumentObservation

class DefaultKnowledgeGraphBuilder : KnowledgeGraphBuilder {

    override fun buildWithEvidence(
        sourceIndex: SourceIndex,
    ): KnowledgeBuildResult {
        val nodes = linkedMapOf<String, KnowledgeNode>()
        val edges = linkedMapOf<String, KnowledgeEdge>()
        val evidence = linkedMapOf<EvidenceId, Evidence>()

        sourceIndex.files
            .sortedBy { it.relativePath }
            .forEach { file ->
                addFile(
                    file = file,
                    nodes = nodes,
                    edges = edges,
                    evidence = evidence,
                )
            }
        addSemanticRelationships(sourceIndex, nodes, edges, evidence)

        return KnowledgeBuildResult(
            graph = KnowledgeGraph(
                nodes = nodes.values.sortedBy { it.id },
                edges = edges.values.sortedBy { it.id },
            ),
            evidence = EvidenceCollection(
                items = evidence.values
                    .sortedBy { it.id.value },
            ),
        )
    }

    private fun addSemanticRelationships(
        sourceIndex: SourceIndex,
        nodes: MutableMap<String, KnowledgeNode>,
        edges: MutableMap<String, KnowledgeEdge>,
        evidence: MutableMap<EvidenceId, Evidence>,
    ) {
        val nodesByQualifiedName = nodes.values
            .mapNotNull { node -> node.attributes["qualifiedName"]?.let { it to node } }
            .groupBy({ it.first }, { it.second })
        sourceIndex.files.sortedBy { it.relativePath }.forEach { file ->
            fun visit(symbol: SourceSymbol, siblingIndex: Int) {
                val sourceNodeId = symbolNodeId(file.relativePath, symbol, siblingIndex)
                val provenLegacyReferences = symbol.location?.let { location ->
                    symbol.superTypes.mapNotNull { rawName ->
                        val simpleName = rawName.substringBefore('<').substringBefore('(').trim()
                        val candidateNames = buildList {
                            add(simpleName)
                            if (!simpleName.contains('.') && file.packageName != null) {
                                add("${file.packageName}.$simpleName")
                            }
                        }
                        val candidates = candidateNames.flatMap { nodesByQualifiedName[it].orEmpty() }
                            .distinctBy { it.id }
                        val target = candidates.singleOrNull() ?: return@mapNotNull null
                        val kind = when {
                            symbol.kind == SourceSymbolKind.INTERFACE &&
                                target.kind == KnowledgeNodeKind.INTERFACE -> SourceSuperTypeKind.EXTENDS
                            target.kind == KnowledgeNodeKind.INTERFACE -> SourceSuperTypeKind.IMPLEMENTS
                            target.kind in setOf(
                                KnowledgeNodeKind.CLASS,
                                KnowledgeNodeKind.OBJECT,
                                KnowledgeNodeKind.ENUM_CLASS,
                                KnowledgeNodeKind.ANNOTATION_CLASS,
                            ) -> SourceSuperTypeKind.EXTENDS
                            else -> return@mapNotNull null
                        }
                        io.docpilot.core.model.source.SourceSuperTypeReference(
                            target.attributes["qualifiedName"] ?: target.name,
                            kind,
                            location,
                        )
                    }
                }.orEmpty()
                (symbol.superTypeReferences + provenLegacyReferences).distinct()
                    .sortedWith(
                    compareBy({ it.kind.ordinal }, { it.qualifiedName }, { it.location.lineStart ?: Int.MAX_VALUE }),
                ).forEach { reference ->
                    val evidenceId = addEvidence(
                        evidence = evidence,
                        type = EvidenceType.RELATIONSHIP,
                        relativePath = reference.location.relativePath,
                        lineStart = reference.location.lineStart,
                        columnStart = reference.location.columnStart,
                        lineEnd = reference.location.lineEnd,
                        columnEnd = reference.location.columnEnd,
                        summary = "${reference.kind.name} ${reference.qualifiedName} is declared.",
                        attributes = mapOf(
                            "relationshipKind" to reference.kind.name,
                            "targetQualifiedName" to reference.qualifiedName,
                        ),
                    )
                    val targetId = semanticTarget(
                        reference.qualifiedName,
                        nodesByQualifiedName,
                        nodes,
                        evidenceId.value,
                        "supertype",
                    )
                    addEdge(
                        edges,
                        sourceNodeId,
                        targetId,
                        if (reference.kind == SourceSuperTypeKind.EXTENDS) {
                            RelationshipType.EXTENDS
                        } else {
                            RelationshipType.IMPLEMENTS
                        },
                        setOf(evidenceId.value),
                    )
                }
                symbol.calls.sortedWith(
                    compareBy({ it.targetQualifiedName }, { it.targetSignature ?: "" },
                        { it.location.lineStart ?: Int.MAX_VALUE }),
                ).forEach { call ->
                    val evidenceId = addEvidence(
                        evidence = evidence,
                        type = EvidenceType.RELATIONSHIP,
                        relativePath = call.location.relativePath,
                        lineStart = call.location.lineStart,
                        columnStart = call.location.columnStart,
                        lineEnd = call.location.lineEnd,
                        columnEnd = call.location.columnEnd,
                        summary = "CALLS ${call.targetQualifiedName} is observed.",
                        attributes = buildMap {
                            put("relationshipKind", RelationshipType.CALLS.name)
                            put("targetQualifiedName", call.targetQualifiedName)
                            call.targetSignature?.let { put("targetSignature", it) }
                        },
                    )
                    val candidates = nodesByQualifiedName[call.targetQualifiedName].orEmpty().filter { node ->
                        call.targetSignature == null || node.attributes["signature"] == call.targetSignature
                    }
                    val targetId = when (candidates.size) {
                        1 -> candidates.single().id
                        0 -> semanticTarget(
                            call.targetQualifiedName,
                            nodesByQualifiedName,
                            nodes,
                            evidenceId.value,
                            "call",
                        )
                        else -> ambiguousTarget(call.targetQualifiedName, nodes, evidenceId.value, "call")
                    }
                    addEdge(
                        edges,
                        sourceNodeId,
                        targetId,
                        RelationshipType.CALLS,
                        setOf(evidenceId.value),
                    )
                }
                symbol.children.forEachIndexed { index, child -> visit(child, index) }
            }
            file.symbols.forEachIndexed { index, symbol -> visit(symbol, index) }
        }
    }

    private fun semanticTarget(
        qualifiedName: String,
        nodesByQualifiedName: Map<String, List<KnowledgeNode>>,
        nodes: MutableMap<String, KnowledgeNode>,
        evidenceRef: String,
        category: String,
    ): String {
        val candidates = nodesByQualifiedName[qualifiedName].orEmpty()
        if (candidates.size == 1) return candidates.single().id
        if (candidates.size > 1) return ambiguousTarget(qualifiedName, nodes, evidenceRef, category)
        val id = "external:$qualifiedName"
        nodes.putIfAbsent(
            id,
            KnowledgeNode(
                id = id,
                name = qualifiedName,
                kind = KnowledgeNodeKind.EXTERNAL_TYPE,
                attributes = mapOf("qualifiedName" to qualifiedName),
                evidenceRefs = setOf(evidenceRef),
            ),
        )
        return id
    }

    private fun ambiguousTarget(
        qualifiedName: String,
        nodes: MutableMap<String, KnowledgeNode>,
        evidenceRef: String,
        category: String,
    ): String {
        val id = "unresolved:$category:$qualifiedName"
        nodes.putIfAbsent(
            id,
            KnowledgeNode(
                id = id,
                name = qualifiedName,
                kind = KnowledgeNodeKind.UNKNOWN,
                attributes = mapOf("qualifiedName" to qualifiedName),
                evidenceRefs = setOf(evidenceRef),
            ),
        )
        return id
    }

    private fun addFile(
        file: SourceFile,
        nodes: MutableMap<String, KnowledgeNode>,
        edges: MutableMap<String, KnowledgeEdge>,
        evidence: MutableMap<EvidenceId, Evidence>,
    ) {
        val fileEvidence = addEvidence(
            evidence = evidence,
            type = EvidenceType.SOURCE_FILE,
            relativePath = file.relativePath,
            summary = "Source file ${file.relativePath} was indexed.",
        )

        val fileNode = KnowledgeNode(
            id = "file:${file.relativePath}",
            name = file.relativePath.substringAfterLast('/'),
            kind = KnowledgeNodeKind.FILE,
            attributes = mapOf(
                "relativePath" to file.relativePath,
                "language" to file.language.name,
            ),
            evidenceRefs = setOf(fileEvidence.value),
        )
        nodes.putIfAbsent(fileNode.id, fileNode)

        file.packageName?.let { packageName ->
            val packageEvidence = addEvidence(
                evidence = evidence,
                type = EvidenceType.PACKAGE_DECLARATION,
                relativePath = file.relativePath,
                summary = "Package $packageName is declared.",
                attributes = mapOf(
                    "packageName" to packageName,
                ),
            )

            val packageNode = KnowledgeNode(
                id = "package:$packageName",
                name = packageName,
                kind = KnowledgeNodeKind.PACKAGE,
                attributes = mapOf(
                    "qualifiedName" to packageName,
                ),
                evidenceRefs = setOf(packageEvidence.value),
            )
            nodes.putIfAbsent(packageNode.id, packageNode)

            addEdge(
                edges = edges,
                sourceNodeId = packageNode.id,
                targetNodeId = fileNode.id,
                relationship = RelationshipType.CONTAINS,
                evidenceRefs = setOf(packageEvidence.value),
            )
        }

        file.imports.forEachIndexed { index, sourceImport ->
            addImport(
                file = file,
                index = index,
                fileNode = fileNode,
                sourceImport = sourceImport,
                nodes = nodes,
                edges = edges,
                evidence = evidence,
            )
        }

        file.symbols.forEachIndexed { index, symbol ->
            addSymbol(
                file = file,
                ownerNodeId = fileNode.id,
                symbol = symbol,
                siblingIndex = index,
                nodes = nodes,
                edges = edges,
                evidence = evidence,
            )
        }
        file.composeNavigation.routes.forEach { route ->
            addEvidence(
                evidence = evidence,
                type = EvidenceType.COMPOSE_ROUTE_DECLARATION,
                relativePath = route.location.relativePath,
                lineStart = route.location.lineStart,
                columnStart = route.location.columnStart,
                lineEnd = route.location.lineEnd,
                columnEnd = route.location.columnEnd,
                summary = "Compose route ${route.qualifiedName} is declared.",
                attributes = mapOf(
                    "routeId" to route.id,
                    "routeKind" to route.kind.name,
                    "routeSymbolId" to route.symbolId,
                    "routeExpression" to route.expression,
                ),
            )
        }
        file.composeNavigation.registrations.forEach { registration ->
            addEvidence(
                evidence = evidence,
                type = EvidenceType.COMPOSE_NAVIGATION_REGISTRATION,
                relativePath = registration.location.relativePath,
                lineStart = registration.location.lineStart,
                columnStart = registration.location.columnStart,
                lineEnd = registration.location.lineEnd,
                columnEnd = registration.location.columnEnd,
                summary = "Compose ${registration.apiKind.name} registration is declared.",
                attributes = mapOf(
                    "registrationId" to registration.id,
                    "callee" to registration.calleeQualifiedName,
                    "ownerSymbolId" to registration.ownerSymbolId,
                    "routeExpression" to registration.routeExpression,
                ),
            )
            registration.functionReferences.forEach { reference ->
                addEvidence(
                    evidence = evidence,
                    type = EvidenceType.COMPOSE_FUNCTION_REFERENCE,
                    relativePath = reference.location.relativePath,
                    lineStart = reference.location.lineStart,
                    columnStart = reference.location.columnStart,
                    lineEnd = reference.location.lineEnd,
                    columnEnd = reference.location.columnEnd,
                    summary = "Compose destination function reference ${reference.expression} is declared.",
                    attributes = mapOf(
                        "referenceId" to reference.id,
                        "ownerRegistrationId" to reference.ownerRegistrationId,
                        "referenceKind" to reference.kind.name,
                        "referencedName" to reference.referencedName,
                    ),
                )
            }
            registration.arguments.forEach { argument -> addComposeArgumentEvidence(argument, evidence) }
        }
        file.composeNavigation.graphs.forEach { graph ->
            addEvidence(
                evidence = evidence,
                type = EvidenceType.COMPOSE_NAVIGATION_GRAPH,
                relativePath = graph.location.relativePath,
                lineStart = graph.location.lineStart,
                columnStart = graph.location.columnStart,
                lineEnd = graph.location.lineEnd,
                columnEnd = graph.location.columnEnd,
                summary = "Compose navigation graph ${graph.routeExpression} is declared.",
                attributes = mapOf(
                    "graphId" to graph.id,
                    "graphKind" to graph.kind.name,
                    "registrationId" to graph.registrationId,
                    "parentGraphId" to graph.parentGraphId.orEmpty(),
                ),
            )
        }
        file.composeNavigation.routeArguments.forEach { argument -> addComposeArgumentEvidence(argument, evidence) }
    }

    private fun addComposeArgumentEvidence(
        argument: ComposeNavigationArgumentObservation,
        evidence: MutableMap<EvidenceId, Evidence>,
    ) {
        addEvidence(
            evidence = evidence,
            type = EvidenceType.COMPOSE_NAVIGATION_ARGUMENT,
            relativePath = argument.location.relativePath,
            lineStart = argument.location.lineStart,
            columnStart = argument.location.columnStart,
            lineEnd = argument.location.lineEnd,
            columnEnd = argument.location.columnEnd,
            summary = "Compose navigation argument ${argument.name} is declared.",
            attributes = mapOf(
                "argumentId" to argument.id,
                "argumentName" to argument.name,
                "argumentSourceKind" to argument.sourceKind.name,
                "declaredType" to argument.declaredType.orEmpty(),
                "nullable" to argument.nullable?.toString().orEmpty(),
            ),
        )
    }

    private fun addImport(
        file: SourceFile,
        index: Int,
        fileNode: KnowledgeNode,
        sourceImport: SourceImport,
        nodes: MutableMap<String, KnowledgeNode>,
        edges: MutableMap<String, KnowledgeEdge>,
        evidence: MutableMap<EvidenceId, Evidence>,
    ) {
        val importedName = buildString {
            append(sourceImport.qualifiedName)
            if (sourceImport.wildcard) append(".*")
        }

        val importEvidence = addEvidence(
            evidence = evidence,
            type = EvidenceType.IMPORT_DECLARATION,
            relativePath = file.relativePath,
            summary = "Import $importedName is declared.",
            attributes = buildMap {
                put("qualifiedName", sourceImport.qualifiedName)
                put("wildcard", sourceImport.wildcard.toString())
                put("index", index.toString())
                sourceImport.alias?.let { put("alias", it) }
            },
        )

        val externalNode = KnowledgeNode(
            id = "external:$importedName",
            name = importedName,
            kind = KnowledgeNodeKind.EXTERNAL_TYPE,
            attributes = buildMap {
                put("qualifiedName", sourceImport.qualifiedName)
                put("wildcard", sourceImport.wildcard.toString())
                sourceImport.alias?.let { put("alias", it) }
            },
            evidenceRefs = setOf(importEvidence.value),
        )
        nodes.putIfAbsent(externalNode.id, externalNode)

        addEdge(
            edges = edges,
            sourceNodeId = fileNode.id,
            targetNodeId = externalNode.id,
            relationship = RelationshipType.IMPORTS,
            evidenceRefs = setOf(importEvidence.value),
        )
    }

    private fun addSymbol(
        file: SourceFile,
        ownerNodeId: String,
        symbol: SourceSymbol,
        siblingIndex: Int,
        nodes: MutableMap<String, KnowledgeNode>,
        edges: MutableMap<String, KnowledgeEdge>,
        evidence: MutableMap<EvidenceId, Evidence>,
    ) {
        val symbolEvidence = addEvidence(
            evidence = evidence,
            type = EvidenceType.SYMBOL_DECLARATION,
            relativePath = file.relativePath,
            lineStart = symbol.location?.lineStart,
            columnStart = symbol.location?.columnStart,
            lineEnd = symbol.location?.lineEnd,
            columnEnd = symbol.location?.columnEnd,
            summary = "${symbol.kind.name} ${symbol.name} is declared.",
            attributes = buildMap {
                put("symbolName", symbol.name)
                put("symbolKind", symbol.kind.name)
                put("visibility", symbol.visibility.name)
                symbol.qualifiedName?.let { put("qualifiedName", it) }
                symbol.signature?.let { put("signature", it) }
                symbol.type?.let { put("declaredType", it) }
                symbol.receiverType?.let { put("receiverType", it) }
                if (symbol.modifiers.isNotEmpty()) {
                    put("modifiers", symbol.modifiers
                        .sortedBy { it.ordinal }
                        .joinToString(",") { it.name })
                }
            },
        )

        val symbolNode = KnowledgeNode(
            id = symbolNodeId(
                file.relativePath,
                symbol,
                siblingIndex,
            ),
            name = symbol.name,
            kind = symbol.kind.toKnowledgeNodeKind(),
            attributes = buildMap {
                put("visibility", symbol.visibility.name)
                symbol.location?.lineStart?.let {
                    put("lineStart", it.toString())
                }
                symbol.location?.columnStart?.let {
                    put("columnStart", it.toString())
                }
                symbol.qualifiedName?.let { put("qualifiedName", it) }
                symbol.signature?.let { put("signature", it) }
                symbol.type?.let { put("declaredType", it) }
                symbol.receiverType?.let { put("receiverType", it) }
                if (symbol.modifiers.isNotEmpty()) {
                    put("modifiers", symbol.modifiers
                        .sortedBy { it.ordinal }
                        .joinToString(",") { it.name })
                }
                if (symbol.parameters.isNotEmpty()) {
                    put("parameters", symbol.parameters.joinToString(",") { parameter ->
                        buildString {
                            append(parameter.name)
                            parameter.type?.let { append(":").append(it) }
                            if (parameter.hasDefaultValue) append("=")
                        }
                    })
                }
            },
            evidenceRefs = setOf(symbolEvidence.value),
        )
        nodes.putIfAbsent(symbolNode.id, symbolNode)

        addEdge(
            edges = edges,
            sourceNodeId = ownerNodeId,
            targetNodeId = symbolNode.id,
            relationship = RelationshipType.DECLARES,
            evidenceRefs = setOf(symbolEvidence.value),
        )

        symbol.children.forEachIndexed { index, child ->
            addSymbol(
                file = file,
                ownerNodeId = symbolNode.id,
                symbol = child,
                siblingIndex = index,
                nodes = nodes,
                edges = edges,
                evidence = evidence,
            )
        }
    }

    private fun addEvidence(
        evidence: MutableMap<EvidenceId, Evidence>,
        type: EvidenceType,
        relativePath: String,
        summary: String,
        lineStart: Int? = null,
        columnStart: Int? = null,
        lineEnd: Int? = null,
        columnEnd: Int? = null,
        attributes: Map<String, String> = emptyMap(),
    ): EvidenceId {
        val id = EvidenceId(
            buildString {
                append("evidence:")
                append(type.name)
                append(':')
                append(relativePath)
                append(':')
                append(lineStart ?: "unknown")
                append(':')
                append(attributes.entries
                    .sortedBy { it.key }
                    .joinToString("|") { "${it.key}=${it.value}" })
            },
        )

        evidence.putIfAbsent(
            id,
            Evidence(
                id = id,
                type = type,
                location = EvidenceLocation(
                    relativePath = relativePath,
                    lineStart = lineStart,
                    columnStart = columnStart,
                    lineEnd = lineEnd,
                    columnEnd = columnEnd,
                ),
                summary = summary,
                attributes = attributes,
            ),
        )

        return id
    }

    private fun addEdge(
        edges: MutableMap<String, KnowledgeEdge>,
        sourceNodeId: String,
        targetNodeId: String,
        relationship: RelationshipType,
        evidenceRefs: Set<String>,
    ) {
        val id =
            "edge:${relationship.name}:$sourceNodeId->$targetNodeId"

        val edge = KnowledgeEdge(
                id = id,
                sourceNodeId = sourceNodeId,
                targetNodeId = targetNodeId,
                relationship = relationship,
                evidenceRefs = evidenceRefs,
            )
        val existing = edges[id]
        edges[id] = existing?.copy(
            attributes = existing.attributes + (
                "occurrenceCount" to ((existing.attributes["occurrenceCount"]?.toIntOrNull() ?: 1) + 1).toString()
                ),
            evidenceRefs = (existing.evidenceRefs + evidenceRefs).toSortedSet(),
        ) ?: edge
    }

    private fun symbolNodeId(
        relativePath: String,
        symbol: SourceSymbol,
        siblingIndex: Int,
    ): String =
        symbol.id.takeIf(String::isNotBlank)
            ?.let { "symbol:$it" }
            ?: ("symbol:$relativePath:${symbol.kind.name}:${symbol.name}:" +
                "${symbol.location?.lineStart ?: "unknown"}:$siblingIndex")

    private fun SourceSymbolKind.toKnowledgeNodeKind():
        KnowledgeNodeKind =
        when (this) {
            SourceSymbolKind.CLASS ->
                KnowledgeNodeKind.CLASS
            SourceSymbolKind.INTERFACE ->
                KnowledgeNodeKind.INTERFACE
            SourceSymbolKind.OBJECT ->
                KnowledgeNodeKind.OBJECT
            SourceSymbolKind.ENUM_CLASS ->
                KnowledgeNodeKind.ENUM_CLASS
            SourceSymbolKind.ANNOTATION_CLASS ->
                KnowledgeNodeKind.ANNOTATION_CLASS
            SourceSymbolKind.FUNCTION ->
                KnowledgeNodeKind.FUNCTION
            SourceSymbolKind.PROPERTY ->
                KnowledgeNodeKind.PROPERTY
            SourceSymbolKind.CONSTRUCTOR ->
                KnowledgeNodeKind.CONSTRUCTOR
            SourceSymbolKind.ENUM_ENTRY ->
                KnowledgeNodeKind.ENUM_ENTRY
            SourceSymbolKind.TYPE_ALIAS ->
                KnowledgeNodeKind.TYPE_ALIAS
            SourceSymbolKind.UNKNOWN ->
                KnowledgeNodeKind.UNKNOWN
        }
}

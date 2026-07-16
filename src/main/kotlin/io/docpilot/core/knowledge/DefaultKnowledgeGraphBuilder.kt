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
            attributes = mapOf(
                "symbolName" to symbol.name,
                "symbolKind" to symbol.kind.name,
                "visibility" to symbol.visibility.name,
            ),
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

        edges.putIfAbsent(
            id,
            KnowledgeEdge(
                id = id,
                sourceNodeId = sourceNodeId,
                targetNodeId = targetNodeId,
                relationship = relationship,
                evidenceRefs = evidenceRefs,
            ),
        )
    }

    private fun symbolNodeId(
        relativePath: String,
        symbol: SourceSymbol,
        siblingIndex: Int,
    ): String =
        "symbol:$relativePath:${symbol.kind.name}:${symbol.name}:" +
            "${symbol.location?.lineStart ?: "unknown"}:$siblingIndex"

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
            SourceSymbolKind.TYPE_ALIAS ->
                KnowledgeNodeKind.TYPE_ALIAS
            SourceSymbolKind.UNKNOWN ->
                KnowledgeNodeKind.UNKNOWN
        }
}

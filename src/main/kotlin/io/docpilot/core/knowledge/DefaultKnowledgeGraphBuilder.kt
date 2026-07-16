package io.docpilot.core.knowledge

import io.docpilot.core.api.KnowledgeGraphBuilder
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

/**
 * Converts deterministic source facts into a language-neutral graph.
 *
 * Version 0.1 creates package, file, symbol, and imported external-type nodes.
 */
class DefaultKnowledgeGraphBuilder : KnowledgeGraphBuilder {

    override fun build(sourceIndex: SourceIndex): KnowledgeGraph {
        val nodesById = linkedMapOf<String, KnowledgeNode>()
        val edgesById = linkedMapOf<String, KnowledgeEdge>()

        sourceIndex.files
            .sortedBy { it.relativePath }
            .forEach { file ->
                addFileKnowledge(
                    file = file,
                    nodesById = nodesById,
                    edgesById = edgesById,
                )
            }

        return KnowledgeGraph(
            nodes = nodesById.values.sortedBy { it.id },
            edges = edgesById.values.sortedBy { it.id },
        )
    }

    private fun addFileKnowledge(
        file: SourceFile,
        nodesById: MutableMap<String, KnowledgeNode>,
        edgesById: MutableMap<String, KnowledgeEdge>,
    ) {
        val fileNode = KnowledgeNode(
            id = fileNodeId(file.relativePath),
            name = file.relativePath.substringAfterLast('/'),
            kind = KnowledgeNodeKind.FILE,
            attributes = mapOf(
                "relativePath" to file.relativePath,
                "language" to file.language.name,
            ),
            evidenceRefs = setOf(sourceEvidenceId(file.relativePath)),
        )
        nodesById.putIfAbsent(fileNode.id, fileNode)

        file.packageName?.let { packageName ->
            val packageNode = KnowledgeNode(
                id = packageNodeId(packageName),
                name = packageName,
                kind = KnowledgeNodeKind.PACKAGE,
                attributes = mapOf(
                    "qualifiedName" to packageName,
                ),
            )
            nodesById.putIfAbsent(packageNode.id, packageNode)

            addEdge(
                edgesById = edgesById,
                sourceNodeId = packageNode.id,
                targetNodeId = fileNode.id,
                relationship = RelationshipType.CONTAINS,
                evidenceRefs = setOf(
                    sourceEvidenceId(file.relativePath),
                ),
            )
        }

        file.imports.forEach { sourceImport ->
            addImportKnowledge(
                file = file,
                fileNode = fileNode,
                sourceImport = sourceImport,
                nodesById = nodesById,
                edgesById = edgesById,
            )
        }

        file.symbols.forEachIndexed { index, symbol ->
            addSymbolKnowledge(
                file = file,
                ownerNodeId = fileNode.id,
                symbol = symbol,
                siblingIndex = index,
                nodesById = nodesById,
                edgesById = edgesById,
            )
        }
    }

    private fun addImportKnowledge(
        file: SourceFile,
        fileNode: KnowledgeNode,
        sourceImport: SourceImport,
        nodesById: MutableMap<String, KnowledgeNode>,
        edgesById: MutableMap<String, KnowledgeEdge>,
    ) {
        val importedName = buildString {
            append(sourceImport.qualifiedName)
            if (sourceImport.wildcard) {
                append(".*")
            }
        }

        val externalNode = KnowledgeNode(
            id = externalTypeNodeId(importedName),
            name = importedName,
            kind = KnowledgeNodeKind.EXTERNAL_TYPE,
            attributes = buildMap {
                put(
                    "qualifiedName",
                    sourceImport.qualifiedName,
                )
                put(
                    "wildcard",
                    sourceImport.wildcard.toString(),
                )
                sourceImport.alias?.let {
                    put("alias", it)
                }
            },
        )
        nodesById.putIfAbsent(externalNode.id, externalNode)

        addEdge(
            edgesById = edgesById,
            sourceNodeId = fileNode.id,
            targetNodeId = externalNode.id,
            relationship = RelationshipType.IMPORTS,
            evidenceRefs = setOf(
                sourceEvidenceId(file.relativePath),
            ),
        )
    }

    private fun addSymbolKnowledge(
        file: SourceFile,
        ownerNodeId: String,
        symbol: SourceSymbol,
        siblingIndex: Int,
        nodesById: MutableMap<String, KnowledgeNode>,
        edgesById: MutableMap<String, KnowledgeEdge>,
    ) {
        val symbolNode = KnowledgeNode(
            id = symbolNodeId(
                relativePath = file.relativePath,
                symbol = symbol,
                siblingIndex = siblingIndex,
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
                if (symbol.annotations.isNotEmpty()) {
                    put(
                        "annotations",
                        symbol.annotations.joinToString(","),
                    )
                }
            },
            evidenceRefs = setOf(
                sourceEvidenceId(file.relativePath),
            ),
        )
        nodesById.putIfAbsent(symbolNode.id, symbolNode)

        addEdge(
            edgesById = edgesById,
            sourceNodeId = ownerNodeId,
            targetNodeId = symbolNode.id,
            relationship = RelationshipType.DECLARES,
            evidenceRefs = setOf(
                sourceEvidenceId(file.relativePath),
            ),
        )

        symbol.children.forEachIndexed { index, child ->
            addSymbolKnowledge(
                file = file,
                ownerNodeId = symbolNode.id,
                symbol = child,
                siblingIndex = index,
                nodesById = nodesById,
                edgesById = edgesById,
            )
        }
    }

    private fun addEdge(
        edgesById: MutableMap<String, KnowledgeEdge>,
        sourceNodeId: String,
        targetNodeId: String,
        relationship: RelationshipType,
        evidenceRefs: Set<String>,
    ) {
        val edge = KnowledgeEdge(
            id = edgeId(
                sourceNodeId = sourceNodeId,
                targetNodeId = targetNodeId,
                relationship = relationship,
            ),
            sourceNodeId = sourceNodeId,
            targetNodeId = targetNodeId,
            relationship = relationship,
            evidenceRefs = evidenceRefs,
        )

        edgesById.putIfAbsent(edge.id, edge)
    }

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

    private fun packageNodeId(packageName: String): String =
        "package:$packageName"

    private fun fileNodeId(relativePath: String): String =
        "file:$relativePath"

    private fun externalTypeNodeId(qualifiedName: String): String =
        "external:$qualifiedName"

    private fun symbolNodeId(
        relativePath: String,
        symbol: SourceSymbol,
        siblingIndex: Int,
    ): String {
        val line = symbol.location?.lineStart
            ?.toString()
            ?: "unknown"

        return buildString {
            append("symbol:")
            append(relativePath)
            append(':')
            append(symbol.kind.name)
            append(':')
            append(symbol.name)
            append(':')
            append(line)
            append(':')
            append(siblingIndex)
        }
    }

    private fun edgeId(
        sourceNodeId: String,
        targetNodeId: String,
        relationship: RelationshipType,
    ): String =
        "edge:${relationship.name}:$sourceNodeId->$targetNodeId"

    private fun sourceEvidenceId(relativePath: String): String =
        "source:$relativePath"
}

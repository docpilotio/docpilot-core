package io.docpilot.core.specification

import io.docpilot.core.model.ComponentSpecification
import io.docpilot.core.model.ModuleSpecification
import io.docpilot.core.model.PackageSpecification
import io.docpilot.core.model.knowledge.KnowledgeNode
import io.docpilot.core.model.knowledge.KnowledgeNodeKind
import io.docpilot.core.model.source.SourceFile

public enum class RelationshipEndpointKind {
    INTERNAL,
    EXTERNAL,
    UNRESOLVED,
}

public object RelationshipEndpointSemantics {
    public const val EXTERNAL_PREFIX: String = "external:"
    public const val UNRESOLVED_PREFIX: String = "unresolved:"

    public fun kindOf(endpointId: String, internalIds: Set<String>): RelationshipEndpointKind = when {
        endpointId in internalIds -> RelationshipEndpointKind.INTERNAL
        endpointId.startsWith(EXTERNAL_PREFIX) && endpointId.length > EXTERNAL_PREFIX.length ->
            RelationshipEndpointKind.EXTERNAL
        else -> RelationshipEndpointKind.UNRESOLVED
    }
}

internal class RelationshipEndpointResolver(
    files: List<SourceFile>,
    private val modules: List<ModuleSpecification>,
    private val packages: List<PackageSpecification>,
    private val components: List<ComponentSpecification>,
    private val nodesById: Map<String, KnowledgeNode>,
    symbolSpecIdByNodeId: Map<String, String>,
) {
    private val moduleByFileId = files.associate { "file:${it.relativePath}" to moduleId(it) }
    private val packageByFileId = files.associate { file ->
        "file:${file.relativePath}" to packageId(moduleId(file), file.packageName)
    }
    private val packagesByQualifiedName = packages
        .groupBy(PackageSpecification::qualifiedName)
        .mapValues { (_, values) -> values.sortedBy(PackageSpecification::id) }
    private val fixedInternalEndpointByNodeId = buildMap {
        putAll(symbolSpecIdByNodeId)
        modules.forEach { put(it.id, it.id) }
        packages.forEach { put(it.id, it.id) }
        components.forEach { put(it.id, it.id) }
    }

    fun resolve(nodeId: String, counterpartNodeId: String, direction: String = "target"): String {
        packageByFileId[nodeId]?.let { return it }
        fixedInternalEndpointByNodeId[nodeId]?.let { return it }

        val node = nodesById[nodeId] ?: return unresolved(nodeId, direction)
        if (node.kind == KnowledgeNodeKind.EXTERNAL_TYPE) {
            return node.id.takeIf {
                it.startsWith(RelationshipEndpointSemantics.EXTERNAL_PREFIX) &&
                    it.length > RelationshipEndpointSemantics.EXTERNAL_PREFIX.length
            } ?: RelationshipEndpointSemantics.EXTERNAL_PREFIX + stableValue(node)
        }
        if (node.kind == KnowledgeNodeKind.PACKAGE) {
            val qualifiedName = node.attributes["qualifiedName"] ?: node.name
            val candidates = packagesByQualifiedName[qualifiedName].orEmpty()
            if (candidates.size == 1) return candidates.single().id
            val counterpartModuleId = moduleOf(counterpartNodeId)
            candidates.singleOrNull { it.moduleId == counterpartModuleId }?.let { return it.id }
            return candidates.singleOrNull()?.id ?: unresolved(nodeId, direction)
        }
        return unresolved(nodeId, direction)
    }

    private fun moduleOf(nodeId: String): String? {
        moduleByFileId[nodeId]?.let { return it }
        fixedInternalEndpointByNodeId[nodeId]?.let { endpoint ->
            return modulesOfInternalEndpoint(endpoint).singleOrNull()
        }
        val node = nodesById[nodeId] ?: return null
        if (node.kind == KnowledgeNodeKind.PACKAGE) {
            val qualifiedName = node.attributes["qualifiedName"] ?: node.name
            return packagesByQualifiedName[qualifiedName].orEmpty().singleOrNull()?.moduleId
        }
        return null
    }

    private fun modulesOfInternalEndpoint(endpointId: String): Set<String> = buildSet {
        components.filter { component ->
            endpointId == component.id ||
                component.apis.any { it.id == endpointId } ||
                component.properties.any { it.id == endpointId }
        }.forEach { add(it.moduleId) }
        packages.firstOrNull { it.id == endpointId }?.let { add(it.moduleId) }
        modules.firstOrNull { it.id == endpointId }?.let { add(it.id) }
    }

    private fun stableValue(node: KnowledgeNode): String =
        node.attributes["qualifiedName"]?.takeIf(String::isNotBlank)
            ?: node.name.takeIf(String::isNotBlank)
            ?: node.id

    private fun unresolved(nodeId: String, direction: String): String =
        RelationshipEndpointSemantics.UNRESOLVED_PREFIX +
            nodeId.removePrefix(RelationshipEndpointSemantics.UNRESOLVED_PREFIX) + ":" + direction

    private fun modulePath(file: SourceFile): String =
        file.candidateModulePath?.trim('/')?.ifBlank { null } ?: "root"

    private fun moduleId(file: SourceFile): String = "module:${modulePath(file)}"

    private fun packageId(moduleId: String, packageName: String?): String =
        "$moduleId:package:${packageName ?: "<default>"}"
}

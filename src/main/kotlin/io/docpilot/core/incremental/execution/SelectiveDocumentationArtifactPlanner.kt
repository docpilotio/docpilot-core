package io.docpilot.core.incremental.execution

import io.docpilot.core.api.DocumentationArtifactDescriptor
import io.docpilot.core.api.DocumentationArtifactId
import io.docpilot.core.model.ProjectSpecification
import java.security.MessageDigest

public enum class DocumentationArtifactOwnership { DOCPILOT, UNKNOWN }

public enum class DocumentationArtifactReason {
    DIRECT_SPECIFICATION_CHANGE,
    DEPENDENCY_REFRESH,
    MISSING_EXPECTED_ARTIFACT,
    ARTIFACT_ADDED_TO_CATALOG,
    FULL_RENDER_FALLBACK,
}

public enum class OrphanedDocumentationArtifactReason {
    ARTIFACT_REMOVED_FROM_CATALOG,
    ARTIFACT_PATH_CHANGED,
    REMOVED_SCOPE,
}

public data class DocumentationArtifactPlanAction(
    public val artifactId: DocumentationArtifactId,
    public val relativePath: String,
    public val operation: DocumentationArtifactOperation,
    public val reasons: List<DocumentationArtifactReason>,
    public val sourceChangeIds: List<String>,
)

public data class OrphanedDocumentationArtifact(
    public val relativePath: String,
    public val reason: OrphanedDocumentationArtifactReason,
)

public data class DocumentationArtifactPlan(
    public val actions: List<DocumentationArtifactPlanAction>,
    public val orphanedArtifacts: List<OrphanedDocumentationArtifact>,
    public val planSha256: String,
)

public data class DocumentationArtifactPlanningRequest(
    public val previousSpecification: ProjectSpecification,
    public val currentSpecification: ProjectSpecification,
    public val previousCatalog: List<DocumentationArtifactDescriptor>,
    public val currentCatalog: List<DocumentationArtifactDescriptor>,
    public val updatePlan: io.docpilot.core.incremental.specification.IncrementalUpdatePlan,
    public val existingArtifacts: List<ExistingDocumentationArtifact>,
)

public fun interface SelectiveDocumentationArtifactPlanner {
    public fun plan(request: DocumentationArtifactPlanningRequest): DocumentationArtifactPlan
}

public class DefaultSelectiveDocumentationArtifactPlanner : SelectiveDocumentationArtifactPlanner {
    override fun plan(request: DocumentationArtifactPlanningRequest): DocumentationArtifactPlan {
        validateCatalog(request.previousCatalog)
        validateCatalog(request.currentCatalog)
        val previousById = request.previousCatalog.associateBy { it.artifactId }
        val currentById = request.currentCatalog.associateBy { it.artifactId }
        val existingByPath = request.existingArtifacts.associateBy { it.relativePath }
        require(existingByPath.size == request.existingArtifacts.size) {
            "Existing documentation contains duplicate artifact paths"
        }

        val changeIds = request.updatePlan.actions.map { it.id }.toSortedSet()
        val selected = linkedMapOf<DocumentationArtifactId, MutableSet<DocumentationArtifactReason>>()
        val sources = linkedMapOf<DocumentationArtifactId, MutableSet<String>>()
        currentById.values.forEach { descriptor ->
            val existing = existingByPath[descriptor.relativePath]
            if (existing == null) {
                selected.getOrPut(descriptor.artifactId, ::linkedSetOf) +=
                    if (previousById.containsKey(descriptor.artifactId)) {
                        DocumentationArtifactReason.MISSING_EXPECTED_ARTIFACT
                    } else {
                        DocumentationArtifactReason.ARTIFACT_ADDED_TO_CATALOG
                    }
            }
            val matching = changeIds.intersect(
                (descriptor.scopeIds + previousById[descriptor.artifactId]?.scopeIds.orEmpty()).toSet(),
            )
            if (matching.isNotEmpty()) {
                selected.getOrPut(descriptor.artifactId, ::linkedSetOf) +=
                    DocumentationArtifactReason.DIRECT_SPECIFICATION_CHANGE
                sources.getOrPut(descriptor.artifactId, ::linkedSetOf) += matching
            }
        }

        val dependents = buildMap<DocumentationArtifactId, MutableSet<DocumentationArtifactId>> {
            request.currentCatalog.forEach { descriptor ->
                descriptor.dependencyArtifactIds.forEach { dependency ->
                    getOrPut(dependency, ::linkedSetOf) += descriptor.artifactId
                }
            }
        }
        val queue = ArrayDeque(selected.keys.sortedBy { it.value })
        while (queue.isNotEmpty()) {
            val dependency = queue.removeFirst()
            dependents[dependency].orEmpty().sortedBy { it.value }.forEach { dependent ->
                if (selected.putIfAbsent(
                        dependent,
                        linkedSetOf(DocumentationArtifactReason.DEPENDENCY_REFRESH),
                    ) == null
                ) {
                    queue.addLast(dependent)
                } else {
                    selected.getValue(dependent) += DocumentationArtifactReason.DEPENDENCY_REFRESH
                }
                sources.getOrPut(dependent, ::linkedSetOf) += sources[dependency].orEmpty()
            }
        }
        selected.keys.forEach { artifactId ->
            val descriptor = currentById.getValue(artifactId)
            val existing = existingByPath[descriptor.relativePath]
            require(existing == null || existing.ownership == DocumentationArtifactOwnership.DOCPILOT) {
                "Selected artifact has unknown ownership: ${descriptor.relativePath}"
            }
        }

        val actions = request.currentCatalog.sortedBy { it.artifactId.value }.map { descriptor ->
            val reasons = selected[descriptor.artifactId].orEmpty().sortedBy { it.ordinal }
            DocumentationArtifactPlanAction(
                artifactId = descriptor.artifactId,
                relativePath = descriptor.relativePath,
                operation = when {
                    reasons.isEmpty() -> DocumentationArtifactOperation.KEEP
                    existingByPath.containsKey(descriptor.relativePath) -> DocumentationArtifactOperation.UPDATE
                    else -> DocumentationArtifactOperation.CREATE
                },
                reasons = reasons,
                sourceChangeIds = sources[descriptor.artifactId].orEmpty().sorted(),
            )
        }
        val orphans = request.previousCatalog.mapNotNull { previous ->
            val current = currentById[previous.artifactId]
            when {
                current == null -> OrphanedDocumentationArtifact(
                    previous.relativePath,
                    OrphanedDocumentationArtifactReason.ARTIFACT_REMOVED_FROM_CATALOG,
                )
                current.relativePath != previous.relativePath -> OrphanedDocumentationArtifact(
                    previous.relativePath,
                    OrphanedDocumentationArtifactReason.ARTIFACT_PATH_CHANGED,
                )
                else -> null
            }
        }.distinct().sortedBy { it.relativePath }
        return DocumentationArtifactPlan(actions, orphans, sha256(request, actions, orphans))
    }

    private fun validateCatalog(catalog: List<DocumentationArtifactDescriptor>) {
        require(catalog.distinctBy { it.artifactId }.size == catalog.size) { "Duplicate artifact id" }
        require(catalog.distinctBy { it.relativePath }.size == catalog.size) { "Duplicate artifact path" }
        val ids = catalog.mapTo(hashSetOf()) { it.artifactId }
        catalog.forEach { descriptor ->
            require(descriptor.relativePath.isNotBlank() && !descriptor.relativePath.startsWith("/") &&
                !descriptor.relativePath.contains('\\') &&
                descriptor.relativePath.split('/').none { it == ".." }
            ) { "Unsafe artifact path: ${descriptor.relativePath}" }
            require(descriptor.scopeIds == descriptor.scopeIds.distinct().sorted()) {
                "Artifact scope ids must be sorted and unique: ${descriptor.artifactId.value}"
            }
            require(descriptor.dependencyArtifactIds.all(ids::contains)) {
                "Unknown artifact dependency: ${descriptor.artifactId.value}"
            }
        }
        val visiting = hashSetOf<DocumentationArtifactId>()
        val visited = hashSetOf<DocumentationArtifactId>()
        val byId = catalog.associateBy { it.artifactId }
        fun visit(id: DocumentationArtifactId) {
            if (!visiting.add(id)) error("Artifact dependency cycle")
            if (visited.add(id)) byId.getValue(id).dependencyArtifactIds.forEach(::visit)
            visiting.remove(id)
        }
        ids.forEach(::visit)
    }

    private fun sha256(
        request: DocumentationArtifactPlanningRequest,
        actions: List<DocumentationArtifactPlanAction>,
        orphans: List<OrphanedDocumentationArtifact>,
    ): String {
        val canonical = buildString {
            append(request.currentSpecification.schemaVersion).append('|')
                .append(request.currentSpecification.project.id).append('\n')
            listOf("previous" to request.previousCatalog, "current" to request.currentCatalog)
                .forEach { (catalogKind, catalog) ->
                catalog.sortedWith(compareBy({ it.artifactId.value }, { it.relativePath })).forEach { descriptor ->
                    append(catalogKind).append('|')
                    append(descriptor.artifactId.value).append('|')
                        .append(descriptor.relativePath).append('|')
                        .append(descriptor.mediaType).append('|')
                        .append(descriptor.kind.name).append('|')
                        .append(descriptor.scopeIds.joinToString(",")).append('|')
                        .append(descriptor.dependencyArtifactIds.joinToString(",") { it.value })
                        .append('\n')
                }
            }
            request.existingArtifacts.sortedBy { it.relativePath }.forEach { existing ->
                append(existing.relativePath).append('|').append(existing.mediaType).append('|')
                    .append(existing.ownership.name).append('|')
                    .append(digest(existing.content)).append('\n')
            }
            actions.forEach { action ->
                append(action.artifactId.value).append('|').append(action.relativePath).append('|')
                    .append(action.operation.name).append('|')
                    .append(action.reasons.joinToString(",") { it.name }).append('|')
                    .append(action.sourceChangeIds.joinToString(",")).append('\n')
            }
            orphans.forEach { append(it.relativePath).append('|').append(it.reason.name).append('\n') }
        }
        return digest(canonical)
    }

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

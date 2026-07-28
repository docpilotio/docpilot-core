package io.docpilot.core.incremental.execution

import io.docpilot.core.api.DocumentationArtifactDescriptor
import io.docpilot.core.api.DocumentationArtifactId
import io.docpilot.core.model.ProjectSpecification

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
        DocumentationArtifactPlanIntegrity.validateCatalog(request.previousCatalog)
        DocumentationArtifactPlanIntegrity.validateCatalog(request.currentCatalog)
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
                    queue.add(dependent)
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
        return DocumentationArtifactPlan(
            actions,
            orphans,
            DocumentationArtifactPlanIntegrity.sha256(
                request.currentSpecification,
                request.previousCatalog,
                request.currentCatalog,
                request.existingArtifacts,
                actions,
                orphans,
            ),
        )
    }

}

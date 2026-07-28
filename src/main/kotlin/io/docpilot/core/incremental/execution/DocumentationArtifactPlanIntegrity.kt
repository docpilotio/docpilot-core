package io.docpilot.core.incremental.execution

import io.docpilot.core.api.DocumentationArtifactDescriptor
import io.docpilot.core.api.DocumentationArtifactId
import io.docpilot.core.model.ProjectSpecification
import java.security.MessageDigest

/** Canonical integrity rules for RFC-0052 documentation artifact plans. */
public object DocumentationArtifactPlanIntegrity {
    public fun validateCatalog(catalog: List<DocumentationArtifactDescriptor>) {
        require(catalog.distinctBy { it.artifactId }.size == catalog.size) { "Duplicate artifact id" }
        require(catalog.distinctBy { it.relativePath }.size == catalog.size) { "Duplicate artifact path" }
        val ids = catalog.mapTo(hashSetOf()) { it.artifactId }
        catalog.forEach { descriptor ->
            require(
                descriptor.relativePath.isNotBlank() &&
                    !descriptor.relativePath.startsWith("/") &&
                    !descriptor.relativePath.contains('\\') &&
                    descriptor.relativePath.split('/').none { it == ".." || it.isBlank() },
            ) { "Unsafe artifact path: ${descriptor.relativePath}" }
            require(descriptor.scopeIds == descriptor.scopeIds.distinct().sorted()) {
                "Artifact scope ids must be sorted and unique: ${descriptor.artifactId.value}"
            }
            require(descriptor.dependencyArtifactIds == descriptor.dependencyArtifactIds.distinct().sortedBy { it.value }) {
                "Artifact dependency ids must be sorted and unique: ${descriptor.artifactId.value}"
            }
            require(descriptor.dependencyArtifactIds.all(ids::contains)) {
                "Unknown artifact dependency: ${descriptor.artifactId.value}"
            }
        }
        val visiting = hashSetOf<DocumentationArtifactId>()
        val visited = hashSetOf<DocumentationArtifactId>()
        val byId = catalog.associateBy { it.artifactId }
        fun visit(id: DocumentationArtifactId) {
            require(visiting.add(id)) { "Artifact dependency cycle" }
            if (visited.add(id)) byId.getValue(id).dependencyArtifactIds.forEach(::visit)
            visiting.remove(id)
        }
        ids.sortedBy { it.value }.forEach(::visit)
    }

    public fun sha256(
        currentSpecification: ProjectSpecification,
        previousCatalog: List<DocumentationArtifactDescriptor>,
        currentCatalog: List<DocumentationArtifactDescriptor>,
        existingArtifacts: List<ExistingDocumentationArtifact>,
        actions: List<DocumentationArtifactPlanAction>,
        orphans: List<OrphanedDocumentationArtifact>,
    ): String {
        val canonical = buildString {
            append(currentSpecification.schemaVersion).append('|')
                .append(currentSpecification.project.id).append('\n')
            listOf("previous" to previousCatalog, "current" to currentCatalog)
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
            existingArtifacts.sortedBy { it.relativePath }.forEach { existing ->
                append(existing.relativePath).append('|').append(existing.mediaType).append('|')
                    .append(existing.ownership.name).append('|')
                    .append(digest(existing.content)).append('\n')
            }
            actions.sortedBy { it.artifactId.value }.forEach { action ->
                append(action.artifactId.value).append('|').append(action.relativePath).append('|')
                    .append(action.operation.name).append('|')
                    .append(action.reasons.joinToString(",") { it.name }).append('|')
                    .append(action.sourceChangeIds.joinToString(",")).append('\n')
            }
            orphans.sortedBy { it.relativePath }.forEach {
                append(it.relativePath).append('|').append(it.reason.name).append('\n')
            }
        }
        return digest(canonical)
    }

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

/** Offline verifier for a plan and the exact evidence used to create it. */
public class DocumentationArtifactPlanVerifier {
    public fun verify(
        plan: DocumentationArtifactPlan,
        currentSpecification: ProjectSpecification,
        previousCatalog: List<DocumentationArtifactDescriptor>,
        currentCatalog: List<DocumentationArtifactDescriptor>,
        existingArtifacts: List<ExistingDocumentationArtifact>,
    ): Boolean = runCatching {
        DocumentationArtifactPlanIntegrity.validateCatalog(previousCatalog)
        DocumentationArtifactPlanIntegrity.validateCatalog(currentCatalog)
        require(plan.actions == plan.actions.sortedBy { it.artifactId.value })
        require(plan.actions.map { it.artifactId }.distinct().size == plan.actions.size)
        require(plan.actions.all { action ->
            action.relativePath.isNotBlank() &&
                action.reasons == action.reasons.distinct().sortedBy { it.ordinal } &&
                action.sourceChangeIds == action.sourceChangeIds.distinct().sorted()
        })
        require(plan.orphanedArtifacts == plan.orphanedArtifacts.distinct().sortedBy { it.relativePath })
        require(plan.actions.map { it.artifactId }.toSet() == currentCatalog.map { it.artifactId }.toSet())
        require(plan.actions.all { action ->
            currentCatalog.single { it.artifactId == action.artifactId }.relativePath == action.relativePath
        })
        val expected = DocumentationArtifactPlanIntegrity.sha256(
            currentSpecification,
            previousCatalog,
            currentCatalog,
            existingArtifacts,
            plan.actions,
            plan.orphanedArtifacts,
        )
        require(plan.planSha256 == expected)
    }.isSuccess
}

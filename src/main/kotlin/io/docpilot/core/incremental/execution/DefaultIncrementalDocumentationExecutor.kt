package io.docpilot.core.incremental.execution

import io.docpilot.core.api.SpecificationRenderer
import io.docpilot.core.api.SelectiveSpecificationRenderer
import io.docpilot.core.model.RenderedArtifact

/**
 * Application service connecting an IncrementalUpdatePlan to rendering and output execution.
 *
 * Diff calculation remains outside this class. The renderer remains presentation-only and the
 * writer remains unaware of change impact or fallback policy.
 */
public class DefaultIncrementalDocumentationExecutor(
    private val renderer: SpecificationRenderer,
    private val writer: DocumentationArtifactWriter,
    private val selectivePlanner: SelectiveDocumentationArtifactPlanner =
        DefaultSelectiveDocumentationArtifactPlanner(),
) : IncrementalDocumentationExecutor {
    override fun execute(
        request: IncrementalDocumentationExecutionRequest,
    ): IncrementalDocumentationExecutionResult {
        if (!request.updatePlan.requiresUpdate &&
            request.previousSpecification != null &&
            renderer !is SelectiveSpecificationRenderer
        ) {
            return IncrementalDocumentationExecutionResult(mode = IncrementalExecutionMode.NO_CHANGES)
        }

        val fallbackReason = fallbackReason(request)
        val mode = if (fallbackReason == null) {
            IncrementalExecutionMode.INCREMENTAL_UPDATE
        } else {
            IncrementalExecutionMode.FULL_REGENERATION
        }

        return try {
            if (fallbackReason == null && renderer is SelectiveSpecificationRenderer) {
                return executeSelective(request, renderer)
            }
            val renderedArtifacts = renderer.render(request.currentSpecification)
            validateArtifacts(renderedArtifacts)
            val actions = planArtifactActions(renderedArtifacts, request.existingArtifacts)
            executeActions(actions, renderedArtifacts)

            IncrementalDocumentationExecutionResult(
                mode = mode,
                artifactActions = actions,
                renderedArtifacts = renderedArtifacts.sortedBy { it.relativePath },
                fallbackReason = fallbackReason,
                warnings = fallbackReason?.let { listOf("Full regeneration selected: $it") }.orEmpty(),
                writePerformed = actions.any {
                    it.operation == DocumentationArtifactOperation.CREATE ||
                        it.operation == DocumentationArtifactOperation.UPDATE ||
                        it.operation == DocumentationArtifactOperation.DELETE
                },
            )
        } catch (exception: Exception) {
            IncrementalDocumentationExecutionResult(
                mode = IncrementalExecutionMode.FAILED,
                fallbackReason = fallbackReason,
                errorMessage = exception.message ?: exception::class.simpleName ?: "Documentation execution failed",
            )
        }
    }

    private fun executeSelective(
        request: IncrementalDocumentationExecutionRequest,
        selectiveRenderer: SelectiveSpecificationRenderer,
    ): IncrementalDocumentationExecutionResult {
        val previous = checkNotNull(request.previousSpecification)
        val previousCatalog = selectiveRenderer.describe(previous)
        val currentCatalog = selectiveRenderer.describe(request.currentSpecification)
        val plan = selectivePlanner.plan(
            DocumentationArtifactPlanningRequest(
                previousSpecification = previous,
                currentSpecification = request.currentSpecification,
                previousCatalog = previousCatalog,
                currentCatalog = currentCatalog,
                updatePlan = request.updatePlan,
                existingArtifacts = request.existingArtifacts,
            ),
        )
        val selected = plan.actions.filter {
            it.operation == DocumentationArtifactOperation.CREATE ||
                it.operation == DocumentationArtifactOperation.UPDATE
        }
        val selectedIds = selected.mapTo(linkedSetOf()) { it.artifactId }
        val rendered = selectiveRenderer.render(request.currentSpecification, selectedIds)
        validateArtifacts(rendered)
        val descriptorsById = currentCatalog.associateBy { it.artifactId }
        val expectedPaths = selected.associate { action ->
            action.relativePath to descriptorsById.getValue(action.artifactId)
        }
        require(rendered.map { it.relativePath }.toSet() == expectedPaths.keys) {
            "Selective renderer output does not match planned artifacts"
        }
        rendered.forEach { artifact ->
            val descriptor = expectedPaths.getValue(artifact.relativePath)
            require(artifact.mediaType == descriptor.mediaType) {
                "Selective renderer media type does not match descriptor: ${artifact.relativePath}"
            }
        }
        rendered.sortedBy { it.relativePath }.forEach(writer::write)
        val actions = plan.actions.map {
            DocumentationArtifactAction(
                relativePath = it.relativePath,
                operation = it.operation,
                mediaType = descriptorsById.getValue(it.artifactId).mediaType,
            )
        }.sortedBy { it.relativePath }
        return IncrementalDocumentationExecutionResult(
            mode = IncrementalExecutionMode.INCREMENTAL_UPDATE,
            artifactActions = actions,
            renderedArtifacts = rendered.sortedBy { it.relativePath },
            artifactPlanSha256 = plan.planSha256,
            orphanedArtifacts = plan.orphanedArtifacts,
            warnings = plan.orphanedArtifacts.map {
                "Orphan retained: ${it.relativePath} (${it.reason})"
            },
            writePerformed = rendered.isNotEmpty(),
        )
    }

    private fun fallbackReason(request: IncrementalDocumentationExecutionRequest): IncrementalFallbackReason? {
        val previous = request.previousSpecification
            ?: return IncrementalFallbackReason.PREVIOUS_SPECIFICATION_MISSING
        if (previous.schemaVersion != request.currentSpecification.schemaVersion) {
            return IncrementalFallbackReason.SCHEMA_VERSION_MISMATCH
        }
        if (request.updatePlan.requiresUpdate && request.existingArtifacts.isEmpty()) {
            return IncrementalFallbackReason.EXISTING_DOCUMENTATION_MISSING
        }
        return null
    }

    private fun validateArtifacts(artifacts: List<RenderedArtifact>) {
        require(artifacts.none { it.relativePath.isBlank() }) { "Rendered artifact path must not be blank" }
        val duplicatePaths = artifacts.groupingBy { it.relativePath }.eachCount().filterValues { it > 1 }.keys
        require(duplicatePaths.isEmpty()) {
            "Renderer produced duplicate artifact paths: ${duplicatePaths.sorted().joinToString()}"
        }
    }

    private fun planArtifactActions(
        renderedArtifacts: List<RenderedArtifact>,
        existingArtifacts: List<ExistingDocumentationArtifact>,
    ): List<DocumentationArtifactAction> {
        val renderedByPath = renderedArtifacts.associateBy { it.relativePath }
        val existingByPath = existingArtifacts.associateBy { it.relativePath }
        require(existingByPath.size == existingArtifacts.size) { "Existing documentation contains duplicate artifact paths" }

        val outputActions = renderedByPath.values.map { rendered ->
            val existing = existingByPath[rendered.relativePath]
            val operation = when {
                existing == null -> DocumentationArtifactOperation.CREATE
                existing.content == rendered.content && existing.mediaType == rendered.mediaType ->
                    DocumentationArtifactOperation.KEEP
                else -> DocumentationArtifactOperation.UPDATE
            }
            DocumentationArtifactAction(rendered.relativePath, operation, rendered.mediaType)
        }
        val deleteActions = existingByPath.keys
            .filterNot(renderedByPath::containsKey)
            .map { DocumentationArtifactAction(it, DocumentationArtifactOperation.DELETE) }

        return (outputActions + deleteActions).sortedWith(
            compareBy<DocumentationArtifactAction>({ it.relativePath }, { it.operation.ordinal }),
        )
    }

    private fun executeActions(
        actions: List<DocumentationArtifactAction>,
        renderedArtifacts: List<RenderedArtifact>,
    ) {
        val renderedByPath = renderedArtifacts.associateBy { it.relativePath }
        actions.forEach { action ->
            when (action.operation) {
                DocumentationArtifactOperation.CREATE,
                DocumentationArtifactOperation.UPDATE,
                -> writer.write(checkNotNull(renderedByPath[action.relativePath]))

                DocumentationArtifactOperation.DELETE -> writer.delete(action.relativePath)
                DocumentationArtifactOperation.KEEP -> Unit
            }
        }
    }
}

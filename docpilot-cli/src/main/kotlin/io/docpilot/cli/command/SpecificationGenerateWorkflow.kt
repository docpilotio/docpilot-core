package io.docpilot.cli.command

import io.docpilot.cli.bootstrap.ProjectKnowledgeLoader
import io.docpilot.cli.io.FileDocumentationArtifactWriter
import io.docpilot.core.incremental.execution.DefaultIncrementalDocumentationExecutor
import io.docpilot.core.incremental.execution.ExistingDocumentationArtifact
import io.docpilot.core.incremental.specification.IncrementalDocumentationEngine
import io.docpilot.core.incremental.specification.snapshot.FileSpecificationSnapshotRepository
import io.docpilot.core.incremental.specification.snapshot.SpecificationSnapshotExecutionCoordinator
import io.docpilot.core.incremental.specification.snapshot.SpecificationSnapshotExecutionResult
import io.docpilot.core.render.ProjectSpecificationMarkdownRenderer
import io.docpilot.core.specification.DefaultSpecificationBuilder
import io.docpilot.core.specification.SpecificationBuildRequest
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

fun interface SpecificationGenerateWorkflow {
    fun execute(projectRoot: Path, outputRoot: Path): SpecificationSnapshotExecutionResult
}

class DefaultSpecificationGenerateWorkflow(
    private val knowledgeLoader: ProjectKnowledgeLoader = ProjectKnowledgeLoader(),
) : SpecificationGenerateWorkflow {
    override fun execute(projectRoot: Path, outputRoot: Path): SpecificationSnapshotExecutionResult {
        val normalizedProjectRoot = projectRoot.toAbsolutePath().normalize()
        val normalizedOutputRoot = outputRoot.toAbsolutePath().normalize()
        val analysis = knowledgeLoader.analyze(normalizedProjectRoot)
        val specification = DefaultSpecificationBuilder().build(
            SpecificationBuildRequest(
                project = analysis.project,
                knowledge = analysis.knowledge,
                sourceIndex = analysis.sourceIndex,
            ),
        )
        val renderer = ProjectSpecificationMarkdownRenderer()
        val coordinator = SpecificationSnapshotExecutionCoordinator(
            repository = FileSpecificationSnapshotRepository(normalizedProjectRoot),
            engine = IncrementalDocumentationEngine(),
            executor = DefaultIncrementalDocumentationExecutor(
                renderer = renderer,
                writer = FileDocumentationArtifactWriter(normalizedOutputRoot),
            ),
        )
        return coordinator.execute(
            currentSpecification = specification,
            existingArtifacts = renderer.describe(specification).mapNotNull { descriptor ->
                loadExistingArtifact(normalizedOutputRoot, descriptor.relativePath, descriptor.mediaType)
            },
        )
    }

    private fun loadExistingArtifact(
        outputRoot: Path,
        relativePath: String,
        mediaType: String,
    ): ExistingDocumentationArtifact? {
        val path = outputRoot.resolve(relativePath).normalize()
        require(path.startsWith(outputRoot)) { "Specification output path escapes output root: $relativePath" }
        if (!Files.isRegularFile(path)) return null
        return ExistingDocumentationArtifact(
            relativePath = relativePath,
            mediaType = mediaType,
            content = Files.readString(path, StandardCharsets.UTF_8),
        )
    }
}

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

private const val SPECIFICATION_OUTPUT_PATH: String = "docs/project-specification.md"

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
            existingArtifacts = loadExistingArtifacts(normalizedOutputRoot),
        )
    }

    private fun loadExistingArtifacts(outputRoot: Path): List<ExistingDocumentationArtifact> {
        val relativePath = SPECIFICATION_OUTPUT_PATH
        val path = outputRoot.resolve(relativePath).normalize()
        require(path.startsWith(outputRoot)) { "Specification output path escapes output root: $relativePath" }
        if (!Files.isRegularFile(path)) return emptyList()
        return listOf(
            ExistingDocumentationArtifact(
                relativePath = relativePath,
                mediaType = "text/markdown",
                content = Files.readString(path, StandardCharsets.UTF_8),
            ),
        )
    }
}

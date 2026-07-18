package io.docpilot.cli.bootstrap

import io.docpilot.core.extractor.SimpleKotlinSymbolExtractor
import io.docpilot.core.indexer.DefaultProjectSourceIndexer
import io.docpilot.core.knowledge.DefaultKnowledgeGraphBuilder
import io.docpilot.core.lexer.SimpleKotlinLexer
import io.docpilot.core.loader.LocalProjectLoader
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.knowledge.KnowledgeBuildResult
import io.docpilot.core.model.source.SourceIndex
import io.docpilot.core.scanner.LocalSourceScanner
import java.nio.file.Path

data class ProjectAnalysis(
    val project: ProjectDescriptor,
    val sourceIndex: SourceIndex,
    val knowledge: KnowledgeBuildResult,
)

class ProjectKnowledgeLoader {
    fun load(projectPath: Path): KnowledgeBuildResult = analyze(projectPath).knowledge

    fun analyze(projectPath: Path): ProjectAnalysis {
        val project = LocalProjectLoader().load(projectPath.toAbsolutePath().normalize())
        val inventory = LocalSourceScanner().scan(project)
        val index = DefaultProjectSourceIndexer(
            lexer = SimpleKotlinLexer(),
            extractor = SimpleKotlinSymbolExtractor(),
        ).index(inventory)
        return ProjectAnalysis(
            project = ProjectDescriptor(
                id = project.name.lowercase(),
                name = project.name,
            ),
            sourceIndex = index,
            knowledge = DefaultKnowledgeGraphBuilder().buildWithEvidence(index),
        )
    }
}

package io.docpilot.cli.bootstrap

import io.docpilot.core.extractor.SimpleKotlinSymbolExtractor
import io.docpilot.core.indexer.DefaultProjectSourceIndexer
import io.docpilot.core.knowledge.DefaultKnowledgeGraphBuilder
import io.docpilot.core.lexer.SimpleKotlinLexer
import io.docpilot.core.loader.LocalProjectLoader
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.ProjectBuildSystem
import io.docpilot.core.model.ProjectFileType
import io.docpilot.core.model.ProjectLanguage
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
        val summary = io.docpilot.core.summary.DefaultProjectSummaryBuilder().build(inventory)
        return ProjectAnalysis(
            project = ProjectDescriptor(
                id = project.name.lowercase(),
                name = project.name,
                platforms = buildSet {
                    if (inventory.filesOfType(ProjectFileType.ANDROID_MANIFEST).isNotEmpty()) add("Android")
                },
                languages = buildSet {
                    if (ProjectLanguage.KOTLIN in summary.languages) add("Kotlin")
                    if (ProjectLanguage.JAVA in summary.languages) add("Java")
                },
                buildSystems = buildSet {
                    if (ProjectBuildSystem.GRADLE in summary.buildSystems) add("Gradle")
                },
            ),
            sourceIndex = index,
            knowledge = DefaultKnowledgeGraphBuilder().buildWithEvidence(index),
        )
    }
}

package io.docpilot.cli.bootstrap

import io.docpilot.core.extractor.SimpleKotlinSymbolExtractor
import io.docpilot.core.indexer.DefaultProjectSourceIndexer
import io.docpilot.core.knowledge.DefaultKnowledgeGraphBuilder
import io.docpilot.core.lexer.SimpleKotlinLexer
import io.docpilot.core.loader.LocalProjectLoader
import io.docpilot.core.model.knowledge.KnowledgeBuildResult
import io.docpilot.core.scanner.LocalSourceScanner
import java.nio.file.Path

class ProjectKnowledgeLoader {
    fun load(projectPath: Path): KnowledgeBuildResult {
        val project = LocalProjectLoader().load(projectPath.toAbsolutePath().normalize())
        val inventory = LocalSourceScanner().scan(project)
        val index = DefaultProjectSourceIndexer(
            lexer = SimpleKotlinLexer(),
            extractor = SimpleKotlinSymbolExtractor(),
        ).index(inventory)
        return DefaultKnowledgeGraphBuilder().buildWithEvidence(index)
    }
}

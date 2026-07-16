package io.docpilot.core.indexer

import io.docpilot.core.api.KotlinLexer
import io.docpilot.core.api.KotlinSymbolExtractor
import io.docpilot.core.api.ProjectSourceIndexer
import io.docpilot.core.model.ProjectFileType
import io.docpilot.core.model.ProjectInventory
import io.docpilot.core.model.source.SourceFile
import io.docpilot.core.model.source.SourceIndex
import io.docpilot.core.model.source.SourceIndexFailure
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class DefaultProjectSourceIndexer(
    private val lexer: KotlinLexer,
    private val extractor: KotlinSymbolExtractor,
) : ProjectSourceIndexer {

    override fun index(inventory: ProjectInventory): SourceIndex {
        val files = mutableListOf<SourceFile>()
        val failures = mutableListOf<SourceIndexFailure>()

        inventory.files
            .filter { it.type == ProjectFileType.KOTLIN_SOURCE }
            .sortedBy { it.relativePath }
            .forEach { projectFile ->
                try {
                    val path = inventory.project.path.resolve(projectFile.relativePath)
                    val source = Files.readString(path, StandardCharsets.UTF_8)
                    files += extractor.extract(
                        projectFile.relativePath,
                        lexer.tokenize(source),
                    )
                } catch (e: Exception) {
                    failures += SourceIndexFailure(
                        projectFile.relativePath,
                        e.message ?: e::class.simpleName ?: "Unknown indexing failure",
                    )
                }
            }

        return SourceIndex(
            files = files.sortedBy { it.relativePath },
            failures = failures.sortedBy { it.relativePath },
        )
    }
}

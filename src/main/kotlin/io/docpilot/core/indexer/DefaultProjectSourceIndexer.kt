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
                    val extracted = extractor.extract(
                        projectFile.relativePath,
                        lexer.tokenize(source),
                    )
                    files += extracted.copy(
                        candidateModulePath = candidateModulePath(
                            extracted.relativePath,
                        ),
                        sourceSetName = sourceSetName(
                            extracted.relativePath,
                        ),
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

    private fun candidateModulePath(relativePath: String): String? {
        val normalized = relativePath.replace('\\', '/')
        val marker = "/src/"
        val markerIndex = normalized.indexOf(marker)
        if (markerIndex <= 0) return null
        return normalized.substring(0, markerIndex).takeIf(String::isNotBlank)
    }

    private fun sourceSetName(relativePath: String): String? {
        val normalized = relativePath.replace('\\', '/')
        val segments = normalized.split('/')
        val srcIndex = segments.indexOf("src")
        return segments.getOrNull(srcIndex + 1)?.takeIf(String::isNotBlank)
    }
}

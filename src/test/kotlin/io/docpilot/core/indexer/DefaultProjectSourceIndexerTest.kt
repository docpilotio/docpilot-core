package io.docpilot.core.indexer

import io.docpilot.core.extractor.SimpleKotlinSymbolExtractor
import io.docpilot.core.lexer.SimpleKotlinLexer
import io.docpilot.core.loader.LocalProjectLoader
import io.docpilot.core.model.ProjectFile
import io.docpilot.core.model.ProjectFileType
import io.docpilot.core.model.ProjectInventory
import io.docpilot.core.scanner.LocalSourceScanner
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultProjectSourceIndexerTest {

    private val indexer = DefaultProjectSourceIndexer(
        SimpleKotlinLexer(),
        SimpleKotlinSymbolExtractor(),
    )

    @Test
    fun `indexes Kotlin files in deterministic order`() {
        val root = Files.createTempDirectory("docpilot-index")

        root.resolve("feature").createDirectories()
            .resolve("Zeta.kt")
            .writeText("package feature\nclass Zeta\nfun refresh()")

        root.resolve("app").createDirectories()
            .resolve("Alpha.kt")
            .writeText("package app\nclass Alpha")

        val inventory = LocalSourceScanner().scan(
            LocalProjectLoader().load(root),
        )

        val index = indexer.index(inventory)

        assertEquals(
            listOf("app/Alpha.kt", "feature/Zeta.kt"),
            index.files.map { it.relativePath },
        )
        assertEquals(2, index.totalFileCount)
        assertEquals(3, index.totalSymbolCount)
        assertTrue(index.failures.isEmpty())
    }

    @Test
    fun `reports missing Kotlin file`() {
        val root = Files.createTempDirectory("docpilot-index-failure")
        val inventory = ProjectInventory(
            project = LocalProjectLoader().load(root),
            directories = emptyList(),
            files = listOf(
                ProjectFile(
                    "missing/Missing.kt",
                    ProjectFileType.KOTLIN_SOURCE,
                ),
            ),
        )

        val index = indexer.index(inventory)

        assertTrue(index.files.isEmpty())
        assertEquals(1, index.failures.size)
    }
    @Test
    fun `derives candidate module and source set from source path`() {
        val root = Files.createTempDirectory("docpilot-index-path")
        root.resolve("app/src/main/kotlin/example").createDirectories()
            .resolve("Sample.kt")
            .writeText("package example\nclass Sample")

        val inventory = LocalSourceScanner().scan(
            LocalProjectLoader().load(root),
        )

        val file = indexer.index(inventory).files.single()

        assertEquals("app", file.candidateModulePath)
        assertEquals("main", file.sourceSetName)
    }

}

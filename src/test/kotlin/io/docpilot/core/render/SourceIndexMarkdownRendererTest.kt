package io.docpilot.core.render

import io.docpilot.core.model.source.SourceFile
import io.docpilot.core.model.source.SourceImport
import io.docpilot.core.model.source.SourceIndex
import io.docpilot.core.model.source.SourceIndexFailure
import io.docpilot.core.model.source.SourceLanguage
import io.docpilot.core.model.source.SourceLocation
import io.docpilot.core.model.source.SourceSymbol
import io.docpilot.core.model.source.SourceSymbolKind
import io.docpilot.core.model.source.SourceVisibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceIndexMarkdownRendererTest {

    private val renderer =
        SourceIndexMarkdownRenderer()

    @Test
    fun `renders indexed files and symbols`() {
        val artifact = renderer.render(
            SourceIndex(
                files = listOf(
                    SourceFile(
                        relativePath =
                            "app/src/main/kotlin/example/Tasks.kt",
                        language = SourceLanguage.KOTLIN,
                        packageName = "example.tasks",
                        imports = listOf(
                            SourceImport(
                                qualifiedName =
                                    "kotlinx.coroutines.flow.Flow",
                                alias = "TaskFlow",
                            ),
                        ),
                        symbols = listOf(
                            SourceSymbol(
                                name = "TasksViewModel",
                                kind = SourceSymbolKind.CLASS,
                                visibility =
                                    SourceVisibility.INTERNAL,
                                location = SourceLocation(
                                    relativePath =
                                        "app/src/main/kotlin/example/Tasks.kt",
                                    lineStart = 8,
                                    columnStart = 1,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            "docs/source-index.md",
            artifact.relativePath,
        )
        assertEquals(
            "text/markdown",
            artifact.mediaType,
        )
        assertTrue(
            artifact.content.contains("# Source Index"),
        )
        assertTrue(
            artifact.content.contains(
                "`example.tasks`",
            ),
        )
        assertTrue(
            artifact.content.contains(
                "`TasksViewModel` — CLASS, INTERNAL, line 8",
            ),
        )
        assertTrue(
            artifact.content.contains(
                "`kotlinx.coroutines.flow.Flow as TaskFlow`",
            ),
        )
    }

    @Test
    fun `renders failures and empty sections`() {
        val artifact = renderer.render(
            SourceIndex(
                files = emptyList(),
                failures = listOf(
                    SourceIndexFailure(
                        relativePath = "Broken.kt",
                        message = "Unable to read file",
                    ),
                ),
            ),
        )

        assertTrue(
            artifact.content.contains("- None indexed"),
        )
        assertTrue(
            artifact.content.contains(
                "`Broken.kt`: Unable to read file",
            ),
        )
    }
}

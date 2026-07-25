package io.docpilot.core.extractor

import io.docpilot.core.lexer.SimpleKotlinLexer
import io.docpilot.core.model.source.SourceSymbolKind
import io.docpilot.core.model.source.SourceVisibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimpleKotlinSymbolExtractorTest {

    private val lexer = SimpleKotlinLexer()
    private val extractor =
        SimpleKotlinSymbolExtractor()

    @Test
    fun `property accessor is not included in declared type and operators remain intact`() {
        val file = extractor.extract(
            "Task.kt",
            lexer.tokenize(
                """
                class Task {
                    val displayLabel: String
                        get() = title || description
                }
                """.trimIndent(),
            ),
        )
        val property = file.symbols.single().children.single()

        assertEquals("String", property.type)
        assertTrue(property.signature!!.contains("||"))
    }

    @Test
    fun `extracts package imports and declarations`() {
        val source = """
            package example.tasks

            import kotlinx.coroutines.flow.Flow
            import kotlin.collections.*
            import example.Legacy as OldLegacy

            internal class TasksViewModel
            interface Repository
            object Defaults
            fun loadTasks()
            private val state = Unit
            typealias TaskId = String
            enum class Status
            annotation class StableApi
        """.trimIndent()

        val file = extractor.extract(
            relativePath =
                "app/src/main/kotlin/example/Tasks.kt",
            tokens = lexer.tokenize(source),
        )

        assertEquals(
            "example.tasks",
            file.packageName,
        )
        assertEquals(3, file.imports.size)
        assertTrue(file.imports[1].wildcard)
        assertEquals(
            "OldLegacy",
            file.imports[2].alias,
        )

        assertEquals(
            listOf(
                SourceSymbolKind.CLASS,
                SourceSymbolKind.INTERFACE,
                SourceSymbolKind.OBJECT,
                SourceSymbolKind.FUNCTION,
                SourceSymbolKind.PROPERTY,
                SourceSymbolKind.TYPE_ALIAS,
                SourceSymbolKind.ENUM_CLASS,
                SourceSymbolKind.ANNOTATION_CLASS,
            ),
            file.symbols.map { it.kind },
        )

        assertEquals(
            SourceVisibility.INTERNAL,
            file.symbols.first().visibility,
        )
        assertEquals(
            SourceVisibility.PRIVATE,
            file.symbols
                .first { it.name == "state" }
                .visibility,
        )
    }

    @Test
    fun `records one based source location`() {
        val file = extractor.extract(
            relativePath = "Sample.kt",
            tokens = lexer.tokenize(
                """
                package sample
                class Example
                """.trimIndent(),
            ),
        )

        val symbol = file.symbols.single()

        assertEquals(2, symbol.location?.lineStart)
        assertEquals(1, symbol.location?.columnStart)
        assertEquals(
            "Sample.kt",
            symbol.location?.relativePath,
        )
    }

    @Test
    fun `supports source without package`() {
        val file = extractor.extract(
            relativePath = "Sample.kt",
            tokens = lexer.tokenize(
                "class Sample",
            ),
        )

        assertEquals(null, file.packageName)
        assertEquals(
            "Sample",
            file.symbols.single().name,
        )
    }
}

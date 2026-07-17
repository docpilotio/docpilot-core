package io.docpilot.core.extractor

import io.docpilot.core.lexer.SimpleKotlinLexer
import io.docpilot.core.model.source.SourceModifier
import io.docpilot.core.model.source.SourceSymbolKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SimpleKotlinSymbolExtractorRfc0034Test {
    private val lexer = SimpleKotlinLexer()
    private val extractor = SimpleKotlinSymbolExtractor()

    @Test
    fun `extracts nested declarations signatures parameters and evidence`() {
        val source = """
            package sample
            import kotlinx.coroutines.flow.Flow

            @Stable
            internal data class Repository(val name: String) {
                suspend fun String.load(id: Int = 1): Flow<List<String>> = TODO()
                private lateinit var state: String
                constructor(name: String, enabled: Boolean) : this(name)
                object Factory
            }
        """.trimIndent()

        val file = extractor.extract("src/main/kotlin/sample/Repository.kt", lexer.tokenize(source))
        val repository = file.symbols.single()

        assertEquals("sample.Repository", repository.qualifiedName)
        assertTrue(SourceModifier.DATA in repository.modifiers)
        assertEquals("Stable", repository.annotations.single())
        assertTrue(repository.id.isNotBlank())
        assertEquals(1, repository.location?.lineStart?.let { if (it > 0) 1 else 0 })

        val function = repository.children.first { it.kind == SourceSymbolKind.FUNCTION }
        assertEquals("String", function.receiverType)
        assertEquals("Flow<List<String>>", function.type)
        assertEquals("id", function.parameters.single().name)
        assertTrue(function.parameters.single().hasDefaultValue)
        assertEquals(repository.id, function.parentSymbolId)

        assertTrue(repository.children.any { it.kind == SourceSymbolKind.CONSTRUCTOR })
        assertTrue(repository.children.any { it.kind == SourceSymbolKind.OBJECT })
    }

    @Test
    fun `generates deterministic ids and ordering`() {
        val source = "class Z { fun b() = Unit; fun a() = Unit }"
        val first = extractor.extract("Z.kt", lexer.tokenize(source))
        val second = extractor.extract("Z.kt", lexer.tokenize(source))
        assertEquals(first, second)
        assertNotEquals("", first.symbols.single().id)
    }
}

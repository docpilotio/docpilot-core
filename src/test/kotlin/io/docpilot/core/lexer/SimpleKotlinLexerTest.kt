package io.docpilot.core.lexer

import io.docpilot.core.model.source.KotlinTokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimpleKotlinLexerTest {

    private val lexer = SimpleKotlinLexer()

    @Test
    fun `preserves multi character operators`() {
        val texts = lexer.tokenize("val active = ready || pending && value != null")
            .map { it.text }

        assertTrue("||" in texts)
        assertTrue("&&" in texts)
        assertTrue("!=" in texts)
        assertFalse(texts.windowed(2).any { it == listOf("|", "|") })
    }

    @Test
    fun `tokenizes declarations and literals`() {
        val tokens = lexer.tokenize(
            """
            package example.tasks
            import kotlinx.coroutines.flow.Flow as TaskFlow

            internal class TasksViewModel {
                fun loadTasks(force: Boolean = false) = Unit
                val title = "Tasks"
            }
            """.trimIndent(),
        )

        assertEquals(
            listOf("package", "example", ".", "tasks"),
            tokens.take(4).map { it.text },
        )
        assertTrue(tokens.any {
            it.type == KotlinTokenType.KEYWORD &&
                it.text == "class"
        })
        assertTrue(tokens.any {
            it.type == KotlinTokenType.IDENTIFIER &&
                it.text == "TasksViewModel"
        })
        assertTrue(tokens.any {
            it.type == KotlinTokenType.STRING_LITERAL &&
                it.text == "\"Tasks\""
        })
        assertEquals(
            KotlinTokenType.END_OF_FILE,
            tokens.last().type,
        )
    }

    @Test
    fun `ignores comments`() {
        val tokenTexts = lexer.tokenize(
            """
            // class Fake
            /* fun ignored() */
            class Real
            """.trimIndent(),
        ).map { it.text }

        assertFalse("Fake" in tokenTexts)
        assertFalse("ignored" in tokenTexts)
        assertTrue("Real" in tokenTexts)
    }

    @Test
    fun `reports one based positions`() {
        val classToken = lexer.tokenize(
            """
            package sample
            class Example
            """.trimIndent(),
        ).first { it.text == "class" }

        assertEquals(2, classToken.line)
        assertEquals(1, classToken.column)
    }
    @Test
    fun `tokenizes backtick identifiers and expect actual modifiers`() {
        val tokens = lexer.tokenize("expect fun `when`(); actual fun `when`() = Unit")

        assertTrue(tokens.any { it.text == "expect" && it.type == KotlinTokenType.KEYWORD })
        assertTrue(tokens.any { it.text == "actual" && it.type == KotlinTokenType.KEYWORD })
        assertEquals(2, tokens.count { it.text == "when" && it.type == KotlinTokenType.IDENTIFIER })
    }

}

package io.docpilot.core.prompt

import io.docpilot.core.model.prompt.PromptTemplate
import io.docpilot.core.model.prompt.PromptVariables
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DefaultPromptRendererTest {

    private val renderer = DefaultPromptRenderer()

    @Test
    fun `replaces variables and preserves markdown`() {
        val result = renderer.render(
            template = PromptTemplate(
                name = "architecture.md",
                content = "# {{title}}\n\nProject: {{project}}\n",
            ),
            variables = PromptVariables(
                mapOf(
                    "title" to "Architecture",
                    "project" to "DocPilot",
                ),
            ),
        )

        assertEquals(
            "# Architecture\n\nProject: DocPilot\n",
            result.content,
        )
    }

    @Test
    fun `fails when variable is missing`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            renderer.render(
                template = PromptTemplate(
                    name = "missing.md",
                    content = "Hello {{name}}",
                ),
                variables = PromptVariables.EMPTY,
            )
        }

        assertTrue(exception.message!!.contains("name"))
    }

    @Test
    fun `preserves UTF-8 content`() {
        val result = renderer.render(
            template = PromptTemplate(
                name = "korean.md",
                content = "프로젝트: {{project}}",
            ),
            variables = PromptVariables(
                mapOf("project" to "닥파일럿"),
            ),
        )

        assertEquals("프로젝트: 닥파일럿", result.content)
    }
}

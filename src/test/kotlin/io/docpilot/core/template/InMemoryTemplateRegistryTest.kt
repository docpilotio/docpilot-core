package io.docpilot.core.template

import io.docpilot.core.model.prompt.PromptTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class InMemoryTemplateRegistryTest {

    @Test
    fun `registers and retrieves template by id`() {
        val template = template("architecture")
        val registry = InMemoryTemplateRegistry()

        registry.register(template)

        assertSame(template, registry.find(TemplateId("architecture")))
        assertSame(template, registry.get(TemplateId("architecture")))
    }

    @Test
    fun `returns null for unknown template`() {
        val registry = InMemoryTemplateRegistry()

        assertNull(registry.find(TemplateId("missing")))
    }

    @Test
    fun `get throws descriptive exception for unknown template`() {
        val registry = InMemoryTemplateRegistry()

        val error = assertFailsWith<TemplateNotFoundException> {
            registry.get(TemplateId("missing"))
        }

        assertEquals(TemplateId("missing"), error.templateId)
        assertEquals("Template was not found: missing", error.message)
    }

    @Test
    fun `rejects duplicate identifiers`() {
        val registry = InMemoryTemplateRegistry(listOf(template("architecture")))

        val error = assertFailsWith<DuplicateTemplateException> {
            registry.register(template("architecture"))
        }

        assertEquals(TemplateId("architecture"), error.templateId)
        assertEquals("Template is already registered: architecture", error.message)
    }

    @Test
    fun `lists templates in deterministic id order`() {
        val registry = InMemoryTemplateRegistry(
            listOf(
                template("readme"),
                template("adr"),
                template("architecture"),
            ),
        )

        assertEquals(
            listOf("adr", "architecture", "readme"),
            registry.list().map { it.id.value },
        )
    }

    @Test
    fun `built in registry contains the supported templates`() {
        val registry = BuiltInTemplates.registry()

        assertEquals(
            listOf("adr", "api", "architecture", "readme"),
            registry.list().map { it.id.value },
        )
        assertEquals("document-architecture", registry.get(TemplateId("architecture")).prompt.name)
        assertEquals("architecture", registry.get(TemplateId("architecture")).metadata["template.id"])
    }

    @Test
    fun `template id rejects unsupported formats`() {
        assertFailsWith<IllegalArgumentException> { TemplateId("Architecture") }
        assertFailsWith<IllegalArgumentException> { TemplateId("architecture template") }
        assertFailsWith<IllegalArgumentException> { TemplateId("") }
    }

    private fun template(id: String): DocumentTemplate = DocumentTemplate(
        id = TemplateId(id),
        name = "$id template",
        description = "Template for $id documents",
        prompt = PromptTemplate(
            name = id,
            content = "Generate $id using {{knowledge}}",
        ),
    )
}

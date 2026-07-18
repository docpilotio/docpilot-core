package io.docpilot.core.incremental.specification.review

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ManagedDocumentationBlockReaderTest {
    private val reader = HtmlCommentManagedDocumentationBlockReader()

    @Test
    fun `reads managed blocks in target order`() {
        val documentation = """
            <!-- DOCPILOT_AI_START id=type:z -->
            Z
            <!-- DOCPILOT_AI_END id=type:z -->

            <!-- DOCPILOT_AI_START id=api:a -->
            A
            <!-- DOCPILOT_AI_END id=api:a -->
        """.trimIndent()

        val blocks = reader.read(documentation)

        assertEquals(listOf("api:a", "type:z"), blocks.keys.toList())
        assertEquals("A", blocks.getValue("api:a"))
    }

    @Test
    fun `rejects malformed and duplicate blocks`() {
        assertFailsWith<IllegalArgumentException> {
            reader.read("<!-- DOCPILOT_AI_START id=api:a -->missing end")
        }
        assertFailsWith<IllegalArgumentException> {
            reader.read(
                """
                <!-- DOCPILOT_AI_START id=api:a -->one<!-- DOCPILOT_AI_END id=api:a -->
                <!-- DOCPILOT_AI_START id=api:a -->two<!-- DOCPILOT_AI_END id=api:a -->
                """.trimIndent(),
            )
        }
    }
}

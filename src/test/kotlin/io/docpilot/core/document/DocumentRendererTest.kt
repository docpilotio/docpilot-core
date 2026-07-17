package io.docpilot.core.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DocumentRendererTest {

    @Test
    fun `renders deterministic markdown document`() {
        val document = Document(
            title = "Architecture",
            sections = listOf(
                DocumentSection(
                    id = "overview",
                    title = "Overview",
                    content = "DocPilot extracts evidence before generation.",
                ),
                DocumentSection(
                    id = "components",
                    title = "Components",
                    content = "Core\nProviders",
                    level = 3,
                ),
            ),
            metadata = DocumentMetadata(
                type = "architecture",
                attributes = mapOf(
                    "version" to "1",
                    "project" to "docpilot-core",
                ),
            ),
        )

        val rendered = DocumentRenderer().render(document)

        assertEquals(
            """
            # Architecture

            <!--
            type: architecture
            project: docpilot-core
            version: 1
            -->

            ## Overview

            DocPilot extracts evidence before generation.

            ### Components

            Core
            Providers
            """.trimIndent(),
            rendered,
        )
    }

    @Test
    fun `renders plain text without markdown markers`() {
        val rendered = DocumentRenderer().render(
            Document(
                title = "Architecture",
                format = DocumentFormat.PLAIN_TEXT,
                sections = listOf(
                    DocumentSection(
                        id = "overview",
                        title = "Overview",
                        content = "Core overview",
                    ),
                ),
            ),
        )

        assertEquals(
            """
            Architecture

            Overview

            Core overview
            """.trimIndent(),
            rendered,
        )
    }

    @Test
    fun `rejects duplicate section ids`() {
        assertFailsWith<IllegalArgumentException> {
            Document(
                title = "Architecture",
                sections = listOf(
                    DocumentSection("same", "One", "First"),
                    DocumentSection("same", "Two", "Second"),
                ),
            )
        }
    }

    @Test
    fun `rejects unsupported heading level`() {
        assertFailsWith<IllegalArgumentException> {
            DocumentSection(
                id = "overview",
                title = "Overview",
                content = "Content",
                level = 7,
            )
        }
    }
}

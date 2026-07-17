package io.docpilot.core.prompt

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FilePromptRepositoryTest {

    @Test
    fun `loads UTF-8 markdown template`() {
        val root = Files.createTempDirectory("docpilot-prompts")
        val template = root.resolve("architecture.md")

        template.writeText("# {{title}}\n\n설명\n")

        val loaded = FilePromptRepository(root)
            .load("architecture.md")

        assertEquals("architecture.md", loaded.name)
        assertEquals("# {{title}}\n\n설명\n", loaded.content)
    }

    @Test
    fun `rejects path outside repository root`() {
        val root = Files.createTempDirectory("docpilot-prompts")
        val outside = Files.createTempDirectory("outside")
            .resolve("secret.md")
            .also {
                it.parent.createDirectories()
                it.writeText("secret")
            }

        assertFailsWith<IllegalArgumentException> {
            FilePromptRepository(root).load(
                root.relativize(outside).toString(),
            )
        }
    }
}

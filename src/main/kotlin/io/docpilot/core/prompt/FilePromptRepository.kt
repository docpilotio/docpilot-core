package io.docpilot.core.prompt

import io.docpilot.core.model.prompt.PromptTemplate
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Loads Markdown prompt templates from a fixed root directory.
 */
class FilePromptRepository(
    root: Path,
) {
    private val root = root.toAbsolutePath().normalize()

    init {
        require(Files.isDirectory(this.root)) {
            "Prompt template root must be an existing directory: ${this.root}"
        }
    }

    fun load(
        relativePath: String,
    ): PromptTemplate {
        require(relativePath.isNotBlank()) {
            "Prompt template path must not be blank."
        }

        val path = root.resolve(relativePath)
            .toAbsolutePath()
            .normalize()

        require(path.startsWith(root)) {
            "Prompt template path must stay inside the repository root."
        }
        require(Files.isRegularFile(path)) {
            "Prompt template does not exist: $relativePath"
        }

        return PromptTemplate(
            name = relativePath.replace('\\', '/'),
            content = Files.readString(
                path,
                StandardCharsets.UTF_8,
            ),
        )
    }
}

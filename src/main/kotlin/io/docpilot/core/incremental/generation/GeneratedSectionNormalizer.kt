package io.docpilot.core.incremental.generation

import io.docpilot.core.incremental.prompt.PromptOutputContract

fun interface GeneratedSectionNormalizer {
    fun normalize(rawResponse: String, expectedHeading: String, contract: PromptOutputContract): GeneratedSection
}

class DefaultGeneratedSectionNormalizer(
    private val maxResponseCharacters: Int = 200_000,
) : GeneratedSectionNormalizer {
    override fun normalize(rawResponse: String, expectedHeading: String, contract: PromptOutputContract): GeneratedSection {
        require(rawResponse.length <= maxResponseCharacters) { "AI response exceeds the maximum allowed size." }
        var text = rawResponse.trim()
        require(text.isNotEmpty()) { "AI response must not be blank." }
        val fenced = Regex("^```(?:markdown|md)?\\s*\\n([\\s\\S]*?)\\n```$", RegexOption.IGNORE_CASE)
        text = fenced.matchEntire(text)?.groupValues?.get(1)?.trim() ?: text
        val expected = "## $expectedHeading"
        require(text.lineSequence().firstOrNull()?.trim() == expected) {
            "Generated section must start with '$expected'."
        }
        val levelTwoHeadings = Regex("(?m)^##\\s+.+$").findAll(text).map { it.value.trim() }.toList()
        if (!contract.allowAdditionalSections) {
            require(levelTwoHeadings == listOf(expected)) { "Generated section contains an unexpected additional section." }
        }
        return GeneratedSection(contract.sectionId, expectedHeading, text.trimEnd() + "\n")
    }
}

package io.docpilot.core.documentation.profile

import kotlin.test.Test
import kotlin.test.assertFailsWith

class DocumentationProfileValidatorTest {
    @Test
    fun `rejects invalid identity and unsupported version`() {
        assertFailsWith<IllegalArgumentException> {
            DocumentationProfileValidator.validate(minimalProfile().copy(id = DocumentationProfileId("Invalid ID")))
        }
        assertFailsWith<IllegalArgumentException> {
            DocumentationProfileValidator.validate(minimalProfile(version = 2))
        }
    }

    @Test
    fun `rejects duplicate document type stable key and fixed path`() {
        val base = minimalDefinition()
        assertFailsWith<IllegalArgumentException> {
            DocumentationProfileValidator.validate(minimalProfile(definitions = listOf(base, base.copy(stableKey = DocumentStableKey("other")))))
        }
        assertFailsWith<IllegalArgumentException> {
            DocumentationProfileValidator.validate(
                minimalProfile(definitions = listOf(base, base.copy(type = DocumentType.TEST_STRATEGY))),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DocumentationProfileValidator.validate(
                minimalProfile(definitions = listOf(
                    base,
                    base.copy(
                        type = DocumentType.TEST_STRATEGY,
                        stableKey = DocumentStableKey("test-strategy"),
                    ),
                )),
            )
        }
    }

    @Test
    fun `rejects absolute drive traversal and malformed pattern paths`() {
        listOf(
            "/absolute.md",
            " /absolute.md",
            "C:/absolute.md",
            "../escape.md",
            "project/../escape.md",
            "project/ ../escape.md",
            "project/not|portable.md",
        ).forEach { path ->
            assertFailsWith<IllegalArgumentException> {
                DocumentationProfileValidator.validate(
                    minimalProfile(definitions = listOf(minimalDefinition(path = DocumentPathPolicy.Fixed(path)))),
                )
            }
        }
        listOf("modules/{unknown}.md", "modules/{scopeId}}.md").forEach { pattern ->
            assertFailsWith<IllegalArgumentException> {
                DocumentationProfileValidator.validate(
                    minimalProfile(definitions = listOf(
                        minimalDefinition(
                            path = DocumentPathPolicy.Pattern(pattern),
                            multiplicity = DocumentMultiplicity.PER_MODULE,
                        ),
                    )),
                )
            }
        }
    }

    @Test
    fun `rejects duplicate sections empty purpose and missing audience`() {
        val duplicateSections = listOf(
            SectionDefinition(SectionId("overview"), "Overview", 10, true),
            SectionDefinition(SectionId("overview"), "Other", 20, true),
        )
        assertFailsWith<IllegalArgumentException> {
            DocumentationProfileValidator.validate(
                minimalProfile(definitions = listOf(minimalDefinition(sections = duplicateSections))),
            )
        }
        val duplicateOrder = listOf(
            SectionDefinition(SectionId("overview"), "Overview", 10, true),
            SectionDefinition(SectionId("evidence"), "Evidence", 10, true),
        )
        assertFailsWith<IllegalArgumentException> {
            DocumentationProfileValidator.validate(
                minimalProfile(definitions = listOf(minimalDefinition(sections = duplicateOrder))),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DocumentationProfileValidator.validate(
                minimalProfile(definitions = listOf(minimalDefinition().copy(purpose = " "))),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DocumentationProfileValidator.validate(
                minimalProfile(definitions = listOf(minimalDefinition().copy(primaryAudiences = emptySet()))),
            )
        }
    }

    @Test
    fun `unknown profile lookup fails explicitly`() {
        val registry = ImmutableDocumentationProfileRegistry(listOf(minimalProfile()))

        assertFailsWith<IllegalArgumentException> {
            registry.resolve(DocumentationProfileId("missing-profile"), DocumentationProfileVersion(1))
        }
    }
}

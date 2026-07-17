package io.docpilot.core.render

import io.docpilot.core.knowledge.DefaultKnowledgeGraphBuilder
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.source.SourceFile
import io.docpilot.core.model.source.SourceIndex
import io.docpilot.core.model.source.SourceLanguage
import io.docpilot.core.model.source.SourceLocation
import io.docpilot.core.model.source.SourceParameter
import io.docpilot.core.model.source.SourceSymbol
import io.docpilot.core.model.source.SourceSymbolKind
import io.docpilot.core.model.source.SourceVisibility
import io.docpilot.core.specification.DefaultSpecificationBuilder
import io.docpilot.core.specification.SpecificationBuildRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectSpecificationMarkdownRendererIntegrationTest {
    @Test
    fun `renders specification produced by default builder deterministically`() {
        val sourceIndex = sampleIndex()
        val knowledge = DefaultKnowledgeGraphBuilder().buildWithEvidence(sourceIndex)
        val builder = DefaultSpecificationBuilder()
        val renderer = ProjectSpecificationMarkdownRenderer()

        val firstSpecification = builder.build(
            SpecificationBuildRequest(
                project = ProjectDescriptor(id = "sample", name = "Sample"),
                knowledge = knowledge,
                sourceIndex = sourceIndex,
            ),
        )
        val secondSpecification = builder.build(
            SpecificationBuildRequest(
                project = ProjectDescriptor(id = "sample", name = "Sample"),
                knowledge = knowledge,
                sourceIndex = sourceIndex.copy(files = sourceIndex.files.reversed()),
            ),
        )

        val firstArtifact = renderer.render(firstSpecification).single()
        val secondArtifact = renderer.render(secondSpecification).single()

        assertEquals(firstArtifact, secondArtifact)
        assertTrue(firstArtifact.content.contains("- Schema version: `0.3`"))
        assertTrue(firstArtifact.content.contains("**io.sample.UserRepository**"))
        assertTrue(firstArtifact.content.contains("`findUser(id: String): User?`"))
        assertTrue(firstArtifact.content.contains("`cache`"))
        assertTrue(firstArtifact.content.contains("## Evidence"))
    }

    private fun sampleIndex(): SourceIndex = SourceIndex(
        files = listOf(
            SourceFile(
                relativePath = "app/src/main/kotlin/io/sample/UserRepository.kt",
                language = SourceLanguage.KOTLIN,
                packageName = "io.sample",
                candidateModulePath = "app",
                sourceSetName = "main",
                symbols = listOf(
                    SourceSymbol(
                        id = "type-user-repository",
                        name = "UserRepository",
                        qualifiedName = "io.sample.UserRepository",
                        kind = SourceSymbolKind.CLASS,
                        visibility = SourceVisibility.PUBLIC,
                        location = SourceLocation(
                            "app/src/main/kotlin/io/sample/UserRepository.kt",
                            3,
                            1,
                            8,
                            1,
                        ),
                        children = listOf(
                            SourceSymbol(
                                id = "property-cache",
                                name = "cache",
                                kind = SourceSymbolKind.PROPERTY,
                                type = "Map<String, User>",
                                mutable = false,
                                hasInitializer = true,
                                location = SourceLocation(
                                    "app/src/main/kotlin/io/sample/UserRepository.kt",
                                    4,
                                    5,
                                    4,
                                    45,
                                ),
                            ),
                            SourceSymbol(
                                id = "function-find-user",
                                name = "findUser",
                                kind = SourceSymbolKind.FUNCTION,
                                signature = "findUser(id: String): User?",
                                type = "User?",
                                parameters = listOf(SourceParameter("id", "String")),
                                location = SourceLocation(
                                    "app/src/main/kotlin/io/sample/UserRepository.kt",
                                    6,
                                    5,
                                    6,
                                    50,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )
}

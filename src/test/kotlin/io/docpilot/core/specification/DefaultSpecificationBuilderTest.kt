package io.docpilot.core.specification

import io.docpilot.core.knowledge.DefaultKnowledgeGraphBuilder
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.source.SourceFile
import io.docpilot.core.model.source.SourceIndex
import io.docpilot.core.model.source.SourceIndexFailure
import io.docpilot.core.model.source.SourceLanguage
import io.docpilot.core.model.source.SourceLocation
import io.docpilot.core.model.source.SourceParameter
import io.docpilot.core.model.source.SourceSymbol
import io.docpilot.core.model.source.SourceSymbolKind
import io.docpilot.core.model.source.SourceVisibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultSpecificationBuilderTest {
    @Test
    fun `builds deterministic DIR from source index and knowledge result`() {
        val sourceIndex = sampleIndex()
        val knowledge = DefaultKnowledgeGraphBuilder().buildWithEvidence(sourceIndex)
        val request = SpecificationBuildRequest(
            project = ProjectDescriptor(id = "sample", name = "Sample"),
            knowledge = knowledge,
            sourceIndex = sourceIndex,
        )

        val first = DefaultSpecificationBuilder().build(request)
        val second = DefaultSpecificationBuilder().build(request.copy(sourceIndex = sourceIndex.copy(files = sourceIndex.files.reversed())))

        assertEquals("0.3", first.schemaVersion)
        assertEquals(first, second)
        assertEquals(listOf("module:app"), first.modules.map { it.id })
        assertEquals("io.sample", first.packages.single().qualifiedName)
        val component = first.components.single()
        assertEquals("UserRepository", component.name)
        assertEquals("io.sample.UserRepository", component.qualifiedName)
        assertEquals(listOf("findUser"), component.apis.map { it.name })
        assertEquals(listOf("cache"), component.properties.map { it.name })
        assertTrue(first.relationships.isNotEmpty())
        assertTrue(first.evidence.isNotEmpty())
        assertTrue(first.unresolved.any { it.subject == "broken.kt" })
    }

    @Test
    fun `keeps legacy project specification construction source compatible`() {
        val legacy = io.docpilot.core.model.ProjectSpecification(
            project = ProjectDescriptor(id = "legacy", name = "Legacy"),
        )
        assertEquals("0.2", legacy.schemaVersion)
        assertTrue(legacy.packages.isEmpty())
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
                        location = SourceLocation("app/src/main/kotlin/io/sample/UserRepository.kt", 3, 1, 8, 1),
                        children = listOf(
                            SourceSymbol(
                                id = "property-cache",
                                name = "cache",
                                kind = SourceSymbolKind.PROPERTY,
                                type = "Map<String, User>",
                                mutable = false,
                                hasInitializer = true,
                                location = SourceLocation("app/src/main/kotlin/io/sample/UserRepository.kt", 4, 5, 4, 45),
                            ),
                            SourceSymbol(
                                id = "function-find-user",
                                name = "findUser",
                                kind = SourceSymbolKind.FUNCTION,
                                signature = "findUser(id: String): User?",
                                type = "User?",
                                parameters = listOf(SourceParameter("id", "String")),
                                location = SourceLocation("app/src/main/kotlin/io/sample/UserRepository.kt", 6, 5, 6, 50),
                            ),
                        ),
                    ),
                ),
            ),
        ),
        failures = listOf(SourceIndexFailure("broken.kt", "Parser failed.")),
    )
}

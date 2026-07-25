package io.docpilot.core.render

import io.docpilot.core.model.ApiSpecification
import io.docpilot.core.model.ComponentSpecification
import io.docpilot.core.model.Evidence
import io.docpilot.core.model.EvidenceConfidence
import io.docpilot.core.model.ModuleSpecification
import io.docpilot.core.model.PackageSpecification
import io.docpilot.core.model.ParameterSpecification
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.PropertySpecification
import io.docpilot.core.model.RelationshipSpecification
import io.docpilot.core.model.UnresolvedItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectSpecificationMarkdownRendererTest {
    private val renderer = ProjectSpecificationMarkdownRenderer()

    @Test
    fun `renders all DIR sections into deterministic markdown artifacts`() {
        val artifacts = renderer.render(fullSpecification())
        val artifact = artifacts.single { it.relativePath == "docs/project-specification.md" }
        val content = artifacts.joinToString("\n") { it.content }

        assertEquals("docs/project-specification.md", artifact.relativePath)
        assertEquals("text/markdown", artifact.mediaType)
        assertEquals(8, artifacts.size)
        assertTrue(content.contains("# Sample\\#Project"))
        assertTrue(content.contains("## Modules"))
        assertTrue(content.contains("### app"))
        assertTrue(content.contains("##### io.sample"))
        assertTrue(content.contains("**io.sample.UserRepository**"))
        assertTrue(content.contains("`findUser(id: String): User?`"))
        assertTrue(content.contains("`cache`"))
        assertTrue(content.contains("  - Source kind: INTERNAL"))
        assertTrue(content.contains("  - Target kind: EXTERNAL"))
        assertTrue(content.contains("## Evidence"))
        assertTrue(content.contains("## Unresolved"))
    }

    @Test
    fun `renders explicit empty sections`() {
        val content = renderer.render(
            ProjectSpecification(
                schemaVersion = "0.3",
                project = ProjectDescriptor(id = "empty", name = "Empty"),
            ),
        ).joinToString("\n") { it.content }

        assertTrue(content.contains("## Modules\n\n- None"))
        assertTrue(content.contains("## Relationships\n\n- None"))
        assertTrue(content.contains("## Evidence\n\n- None"))
        assertTrue(content.contains("## Unresolved\n\n- None"))
    }

    @Test
    fun `orders unordered DIR collections deterministically`() {
        val specification = fullSpecification()
        val reversed = specification.copy(
            modules = specification.modules.reversed(),
            packages = specification.packages.reversed(),
            components = specification.components.reversed().map { component ->
                component.copy(
                    apis = component.apis.reversed(),
                    properties = component.properties.reversed(),
                    modifiers = component.modifiers.reversed().toSet(),
                    evidenceRefs = component.evidenceRefs.reversed().toSet(),
                )
            },
            relationships = specification.relationships.reversed(),
            evidence = specification.evidence.reversed(),
            unresolved = specification.unresolved.reversed(),
        )

        assertEquals(renderer.render(specification), renderer.render(reversed))
    }

    @Test
    fun `escapes markdown text and supports backticks in code spans`() {
        val content = renderer.render(
            ProjectSpecification(
                schemaVersion = "0.3",
                project = ProjectDescriptor(
                    id = "project`id",
                    name = "A *project* [draft] | v1",
                    description = "Line one\nLine two # heading",
                ),
                unresolved = listOf(
                    UnresolvedItem(
                        id = "question`1",
                        subject = "type|name",
                        question = "Use *this*?",
                    ),
                ),
            ),
        ).joinToString("\n") { it.content }

        assertTrue(content.contains("# A \\*project\\* \\[draft\\] \\| v1"))
        assertTrue(content.contains("``project`id``"))
        assertTrue(content.contains("Line one Line two \\# heading"))
        assertTrue(content.contains("**type\\|name** - Use \\*this\\*?"))
        assertTrue(content.contains("``question`1``"))
    }

    private fun fullSpecification(): ProjectSpecification = ProjectSpecification(
        schemaVersion = "0.3",
        project = ProjectDescriptor(
            id = "sample",
            name = "Sample#Project",
            description = "Sample project.",
            platforms = setOf("JVM", "Android"),
            languages = setOf("Kotlin"),
            buildSystems = setOf("Gradle"),
        ),
        modules = listOf(
            ModuleSpecification(
                id = "module:app",
                name = "app",
                path = "app",
                description = "Application module.",
                sourceSets = setOf("test", "main"),
                evidenceRefs = setOf("evidence:module"),
            ),
            ModuleSpecification(id = "module:lib", name = "lib", path = "lib"),
        ),
        packages = listOf(
            PackageSpecification(
                id = "package:sample",
                name = "sample",
                qualifiedName = "io.sample",
                moduleId = "module:app",
                description = "Sample package.",
                evidenceRefs = setOf("evidence:type"),
            ),
        ),
        components = listOf(
            ComponentSpecification(
                id = "type:user-repository",
                name = "UserRepository",
                moduleId = "module:app",
                packageId = "package:sample",
                qualifiedName = "io.sample.UserRepository",
                kind = "CLASS",
                role = "Stores users.",
                visibility = "PUBLIC",
                modifiers = setOf("FINAL"),
                annotations = listOf("Repository"),
                typeParameters = listOf("T"),
                superTypes = listOf("Store<T>"),
                responsibilities = listOf("Load users"),
                dependencyIds = setOf("external:type:user-source"),
                apis = listOf(
                    ApiSpecification(
                        id = "api:find-user",
                        name = "findUser",
                        kind = "function",
                        signature = "findUser(id: String): User?",
                        visibility = "PUBLIC",
                        returnType = "User?",
                        parameters = listOf(ParameterSpecification("id", "String")),
                        modifiers = setOf("SUSPEND"),
                        purpose = "Find a user.",
                        evidenceRefs = setOf("evidence:api"),
                    ),
                    ApiSpecification(id = "api:add-user", name = "addUser", kind = "function"),
                ),
                properties = listOf(
                    PropertySpecification(
                        id = "property:cache",
                        name = "cache",
                        type = "Map<String, User>",
                        visibility = "PRIVATE",
                        mutable = false,
                        hasInitializer = true,
                        purpose = "Caches users.",
                        evidenceRefs = setOf("evidence:property"),
                    ),
                    PropertySpecification(id = "property:source", name = "source"),
                ),
                evidenceRefs = setOf("evidence:type"),
            ),
        ),
        relationships = listOf(
            RelationshipSpecification(
                id = "relationship:depends",
                type = "DEPENDS_ON",
                sourceId = "type:user-repository",
                targetId = "external:type:user-source",
                description = "Repository depends on source.",
                evidenceRefs = setOf("evidence:relationship"),
            ),
        ),
        evidence = listOf(
            Evidence(
                id = "evidence:type",
                type = "SOURCE_SYMBOL",
                file = "app/UserRepository.kt",
                symbol = "UserRepository",
                lineStart = 3,
                lineEnd = 12,
                summary = "Type declaration.",
                confidence = EvidenceConfidence.HIGH,
            ),
            Evidence("evidence:module", "SOURCE_FILE", summary = "Module source.", confidence = EvidenceConfidence.HIGH),
            Evidence("evidence:api", "SOURCE_SYMBOL", summary = "API declaration.", confidence = EvidenceConfidence.HIGH),
            Evidence("evidence:property", "SOURCE_SYMBOL", summary = "Property declaration.", confidence = EvidenceConfidence.HIGH),
            Evidence("evidence:relationship", "KNOWLEDGE_EDGE", summary = "Dependency edge.", confidence = EvidenceConfidence.MEDIUM),
        ),
        unresolved = listOf(
            UnresolvedItem(
                id = "unresolved:role",
                subject = "type:user-source",
                question = "What is the source implementation?",
                requiredAction = "Inspect another module.",
            ),
        ),
    )
}

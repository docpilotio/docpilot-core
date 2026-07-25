package io.docpilot.core.specification

import io.docpilot.core.knowledge.DefaultKnowledgeGraphBuilder
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.source.SourceCall
import io.docpilot.core.model.source.SourceFile
import io.docpilot.core.model.source.SourceImport
import io.docpilot.core.model.source.SourceIndex
import io.docpilot.core.model.source.SourceLanguage
import io.docpilot.core.model.source.SourceLocation
import io.docpilot.core.model.source.SourceSuperTypeKind
import io.docpilot.core.model.source.SourceSuperTypeReference
import io.docpilot.core.model.source.SourceSymbol
import io.docpilot.core.model.source.SourceSymbolKind
import io.docpilot.core.incremental.execution.DefaultSelectiveDocumentationArtifactPlanner
import io.docpilot.core.incremental.execution.DocumentationArtifactOperation
import io.docpilot.core.incremental.execution.DocumentationArtifactPlanningRequest
import io.docpilot.core.incremental.execution.ExistingDocumentationArtifact
import io.docpilot.core.incremental.specification.IncrementalDocumentationEngine
import io.docpilot.core.render.ProjectSpecificationMarkdownRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SemanticRelationshipExpansionTest {
    @Test
    fun `projects extends implements calls and imports with evidence deterministically`() {
        val sourceIndex = fixture()
        val reversed = sourceIndex.copy(files = sourceIndex.files.reversed())

        val first = build(sourceIndex)
        val second = build(reversed)

        assertEquals(first, second)
        assertEquals(
            setOf("EXTENDS", "IMPLEMENTS", "CALLS", "IMPORTS"),
            first.specification.relationships.mapTo(sortedSetOf()) { it.type },
        )
        assertTrue(first.specification.relationships.all { it.evidenceRefs.isNotEmpty() })
        assertTrue(first.specification.relationships.all {
            it.id == RelationshipIdentity.of(it.type, it.sourceId, it.targetId)
        })
        assertEquals(2, first.relationshipProjectionReport.logicalCountByKind["CALLS"])
        assertEquals(1, first.relationshipProjectionReport.aggregatedOccurrenceCountByKind["CALLS"])
    }

    @Test
    fun `relationship-only change selects relationship artifact and summaries only`() {
        val current = build(fixture()).specification
        val previous = current.copy(relationships = current.relationships.dropLast(1))
        val updatePlan = IncrementalDocumentationEngine().analyze(previous, current).plan
        val renderer = ProjectSpecificationMarkdownRenderer()
        val currentCatalog = renderer.describe(current)
        val rendered = renderer.render(current)

        val plan = DefaultSelectiveDocumentationArtifactPlanner().plan(
            DocumentationArtifactPlanningRequest(
                previousSpecification = previous,
                currentSpecification = current,
                previousCatalog = renderer.describe(previous),
                currentCatalog = currentCatalog,
                updatePlan = updatePlan,
                existingArtifacts = rendered.map {
                    ExistingDocumentationArtifact(it.relativePath, it.mediaType, it.content)
                },
            ),
        )

        val updatedKinds = plan.actions.filter {
            it.operation == DocumentationArtifactOperation.UPDATE
        }.map { action ->
            currentCatalog.single { it.artifactId == action.artifactId }.kind.name
        }.toSet()
        assertEquals(setOf("RELATIONSHIP", "PROJECT_OVERVIEW", "INDEX"), updatedKinds)
        assertTrue(plan.actions.filter { it.operation == DocumentationArtifactOperation.KEEP }.any {
            currentCatalog.single { descriptor -> descriptor.artifactId == it.artifactId }.kind.name == "COMPONENT"
        })
    }

    private fun build(index: SourceIndex): SpecificationBuildResult {
        val knowledge = DefaultKnowledgeGraphBuilder().buildWithEvidence(index)
        return DefaultSpecificationBuilder().buildWithReport(
            SpecificationBuildRequest(
                project = ProjectDescriptor("project:sample", "Sample"),
                knowledge = knowledge,
                sourceIndex = index,
            ),
        )
    }

    private fun fixture(): SourceIndex {
        val path = "app/src/main/kotlin/io/sample/Types.kt"
        fun location(line: Int) = SourceLocation(path, line, 1, line, 30)
        return SourceIndex(
            files = listOf(
                SourceFile(
                    relativePath = path,
                    language = SourceLanguage.KOTLIN,
                    packageName = "io.sample",
                    candidateModulePath = "app",
                    imports = listOf(
                        SourceImport("kotlin.collections.List"),
                        SourceImport("kotlin.collections.List"),
                    ),
                    symbols = listOf(
                        SourceSymbol(
                            id = "base",
                            name = "Base",
                            qualifiedName = "io.sample.Base",
                            kind = SourceSymbolKind.CLASS,
                            location = location(1),
                            children = listOf(
                                SourceSymbol(
                                    id = "base-run",
                                    name = "run",
                                    qualifiedName = "io.sample.Base.run",
                                    signature = "run()",
                                    kind = SourceSymbolKind.FUNCTION,
                                    location = location(2),
                                ),
                            ),
                        ),
                        SourceSymbol(
                            id = "contract",
                            name = "Contract",
                            qualifiedName = "io.sample.Contract",
                            kind = SourceSymbolKind.INTERFACE,
                            location = location(4),
                        ),
                        SourceSymbol(
                            id = "child",
                            name = "Child",
                            qualifiedName = "io.sample.Child",
                            kind = SourceSymbolKind.CLASS,
                            location = location(6),
                            superTypeReferences = listOf(
                                SourceSuperTypeReference(
                                    "io.sample.Base",
                                    SourceSuperTypeKind.EXTENDS,
                                    location(6),
                                ),
                                SourceSuperTypeReference(
                                    "io.sample.Contract",
                                    SourceSuperTypeKind.IMPLEMENTS,
                                    location(6),
                                ),
                            ),
                            children = listOf(
                                SourceSymbol(
                                    id = "child-work",
                                    name = "work",
                                    qualifiedName = "io.sample.Child.work",
                                    signature = "work()",
                                    kind = SourceSymbolKind.FUNCTION,
                                    location = location(7),
                                    calls = listOf(
                                        SourceCall("io.sample.Base.run", location(8), "run()"),
                                        SourceCall("io.sample.Base.run", location(9), "run()"),
                                        SourceCall("external.Logger.log", location(10), "log(String)"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }
}

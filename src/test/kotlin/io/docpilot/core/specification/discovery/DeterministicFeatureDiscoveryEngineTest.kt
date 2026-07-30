package io.docpilot.core.specification.discovery

import io.docpilot.core.knowledge.DefaultKnowledgeGraphBuilder
import io.docpilot.core.model.EntryPointKind
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.source.SourceCall
import io.docpilot.core.model.source.SourceFile
import io.docpilot.core.model.source.SourceIndex
import io.docpilot.core.model.source.SourceImport
import io.docpilot.core.model.source.SourceLanguage
import io.docpilot.core.model.source.SourceLocation
import io.docpilot.core.model.source.SourceSuperTypeKind
import io.docpilot.core.model.source.SourceSuperTypeReference
import io.docpilot.core.model.source.SourceSymbol
import io.docpilot.core.model.source.SourceSymbolKind
import io.docpilot.core.specification.DefaultSpecificationBuilder
import io.docpilot.core.specification.ProjectSpecificationValidator
import io.docpilot.core.specification.SpecificationBuildRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DeterministicFeatureDiscoveryEngineTest {
    @Test
    fun `discovers an evidence-backed Android entry point feature and direct call scenario`() {
        val specification = build(sampleIndex())

        assertEquals("0.4", specification.schemaVersion)
        val entryPoint = specification.entryPoints.single()
        assertEquals(EntryPointKind.ANDROID_ACTIVITY.name, entryPoint.kind)
        val feature = specification.features.single()
        assertEquals(entryPoint.ownerComponentId, feature.ownerComponentId)
        assertEquals(2, feature.participantComponentIds.size)
        val scenario = specification.scenarios.single()
        assertEquals(feature.id, scenario.featureId)
        assertEquals(feature.scenarioIds, listOf(scenario.id))
        assertEquals(1, scenario.steps.size)
        assertTrue(scenario.steps.single().evidenceRefs.isNotEmpty())
        ProjectSpecificationValidator.validate(specification)
    }

    @Test
    fun `does not treat an Activity suffix as framework evidence`() {
        val index = SourceIndex(
            listOf(
                SourceFile(
                    relativePath = "src/FakeActivity.kt",
                    language = SourceLanguage.KOTLIN,
                    packageName = "sample",
                    symbols = listOf(
                        SourceSymbol(
                            id = "fake",
                            name = "FakeActivity",
                            qualifiedName = "sample.FakeActivity",
                            kind = SourceSymbolKind.CLASS,
                            location = location("src/FakeActivity.kt", 1),
                        ),
                    ),
                ),
            ),
        )

        val specification = build(index)

        assertTrue(specification.features.isEmpty())
        assertTrue(specification.entryPoints.isEmpty())
        assertTrue(specification.scenarios.isEmpty())
    }

    @Test
    fun `discovers an imported Android supertype without relying on a class suffix`() {
        val index = SourceIndex(
            listOf(
                SourceFile(
                    relativePath = "src/Launcher.kt",
                    language = SourceLanguage.KOTLIN,
                    packageName = "sample",
                    imports = listOf(SourceImport("androidx.activity.ComponentActivity")),
                    symbols = listOf(
                        SourceSymbol(
                            id = "launcher",
                            name = "Launcher",
                            qualifiedName = "sample.Launcher",
                            kind = SourceSymbolKind.CLASS,
                            superTypes = listOf("ComponentActivity()"),
                            location = location("src/Launcher.kt", 1),
                        ),
                    ),
                ),
            ),
        )

        val entryPoint = build(index).entryPoints.single()

        assertEquals(EntryPointKind.ANDROID_ACTIVITY.name, entryPoint.kind)
    }

    @Test
    fun `excludes debug and test source-set entry points`() {
        val main = SourceFile(
            relativePath = "src/main/Launcher.kt",
            language = SourceLanguage.KOTLIN,
            packageName = "sample",
            sourceSetName = "main",
            imports = listOf(SourceImport("androidx.activity.ComponentActivity")),
            symbols = listOf(androidComponent("main-launcher", "Launcher", "src/main/Launcher.kt")),
        )
        val debug = SourceFile(
            relativePath = "src/debug/TestLauncher.kt",
            language = SourceLanguage.KOTLIN,
            packageName = "sample",
            sourceSetName = "debug",
            imports = listOf(SourceImport("androidx.activity.ComponentActivity")),
            symbols = listOf(androidComponent("debug-launcher", "TestLauncher", "src/debug/TestLauncher.kt")),
        )

        val specification = build(SourceIndex(listOf(debug, main)))

        assertEquals(listOf("Launcher"), specification.entryPoints.map { it.name })
    }

    @Test
    fun `is independent of source file enumeration order`() {
        val index = sampleIndex()

        val first = build(index)
        val second = build(index.copy(files = index.files.reversed()))

        assertEquals(first, second)
    }

    @Test
    fun `step stable identity does not contain numeric order`() {
        val step = build(sampleIndex()).scenarios.single().steps.single()

        assertTrue(step.id.contains("edge:CALLS"))
        assertTrue(!step.id.endsWith(":${step.order}"))
    }

    @Test
    fun `rejects a tampered discovery result`() {
        val specification = build(sampleIndex())
        val knowledge = DefaultKnowledgeGraphBuilder().buildWithEvidence(sampleIndex())
        val result = DeterministicFeatureDiscoveryEngine().discover(knowledge.graph, specification)

        assertFailsWith<IllegalArgumentException> {
            FeatureDiscoveryIntegrity.requireValid(result.copy(policyVersion = "tampered"))
        }
    }

    @Test
    fun `bounded traversal records a deterministic discovery limit finding`() {
        val specification = build(sampleIndex())
        val knowledge = DefaultKnowledgeGraphBuilder().buildWithEvidence(sampleIndex())
        val result = DeterministicFeatureDiscoveryEngine(
            FeatureDiscoveryPolicy(maximumTraversalDepth = 1, maximumParticipantsPerFeature = 1),
        ).discover(knowledge.graph, specification.copy(features = emptyList(), entryPoints = emptyList(), scenarios = emptyList()))

        assertEquals("DISCOVERY_LIMIT_EXCEEDED", result.unresolved.single().requiredAction)
        assertTrue(result.features.single().unresolvedRefs.contains(result.unresolved.single().id))
    }

    private fun build(index: SourceIndex) = DefaultSpecificationBuilder().build(
        SpecificationBuildRequest(
            project = ProjectDescriptor("sample", "Sample"),
            knowledge = DefaultKnowledgeGraphBuilder().buildWithEvidence(index),
            sourceIndex = index,
        ),
    )

    private fun sampleIndex(): SourceIndex = SourceIndex(
        files = listOf(
            SourceFile(
                relativePath = "app/src/MainActivity.kt",
                language = SourceLanguage.KOTLIN,
                packageName = "sample",
                candidateModulePath = "app",
                symbols = listOf(
                    SourceSymbol(
                        id = "main-activity",
                        name = "MainActivity",
                        qualifiedName = "sample.MainActivity",
                        kind = SourceSymbolKind.CLASS,
                        location = location("app/src/MainActivity.kt", 1),
                        superTypeReferences = listOf(
                            SourceSuperTypeReference(
                                "android.app.Activity",
                                SourceSuperTypeKind.EXTENDS,
                                location("app/src/MainActivity.kt", 1),
                            ),
                        ),
                        children = listOf(
                            SourceSymbol(
                                id = "main-load",
                                name = "load",
                                qualifiedName = "sample.MainActivity.load",
                                kind = SourceSymbolKind.FUNCTION,
                                location = location("app/src/MainActivity.kt", 3),
                                calls = listOf(
                                    SourceCall(
                                        "sample.TaskRepository.load",
                                        location("app/src/MainActivity.kt", 4),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            SourceFile(
                relativePath = "data/src/TaskRepository.kt",
                language = SourceLanguage.KOTLIN,
                packageName = "sample",
                candidateModulePath = "data",
                symbols = listOf(
                    SourceSymbol(
                        id = "task-repository",
                        name = "TaskRepository",
                        qualifiedName = "sample.TaskRepository",
                        kind = SourceSymbolKind.CLASS,
                        location = location("data/src/TaskRepository.kt", 1),
                        children = listOf(
                            SourceSymbol(
                                id = "repository-load",
                                name = "load",
                                qualifiedName = "sample.TaskRepository.load",
                                kind = SourceSymbolKind.FUNCTION,
                                location = location("data/src/TaskRepository.kt", 3),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun location(path: String, line: Int) = SourceLocation(path, line, 1, line, 20)

    private fun androidComponent(id: String, name: String, path: String) = SourceSymbol(
        id = id,
        name = name,
        qualifiedName = "sample.$name",
        kind = SourceSymbolKind.CLASS,
        superTypes = listOf("ComponentActivity()"),
        location = location(path, 1),
    )
}

package io.docpilot.core.specification.discovery

import io.docpilot.core.extractor.SimpleKotlinSymbolExtractor
import io.docpilot.core.knowledge.DefaultKnowledgeGraphBuilder
import io.docpilot.core.lexer.SimpleKotlinLexer
import io.docpilot.core.model.EntryPointKind
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.source.SourceIndex
import io.docpilot.core.specification.DefaultSpecificationBuilder
import io.docpilot.core.specification.ProjectSpecificationValidator
import io.docpilot.core.specification.SpecificationBuildRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ComposeNavigationDiscoveryTest {
    @Test
    fun `extracts route registration and deepest composable destination`() {
        val index = index(source())
        assertEquals(listOf("sample.Routes.TASKS"), index.files.single()
            .composeNavigation.routes.map { it.qualifiedName })
        assertEquals(1, index.files.single().composeNavigation.registrations.size)
        assertEquals(
            listOf("Routes", "NavGraph", "Wrapper", "TasksScreen"),
            index.files.single().symbols.map { it.name },
        )
        assertEquals(
            listOf("sample.Routes", "sample.NavGraph", "sample.Wrapper", "sample.TasksScreen"),
            index.files.single().symbols.map { it.qualifiedName },
        )
        assertEquals(
            listOf("sample.Wrapper", "sample.TasksScreen"),
            index.files.single().composeNavigation.registrations.single()
                .destinationCalls.map { it.calleeQualifiedName },
        )
        val specification = build(index)

        val entryPoint = specification.entryPoints.single {
            it.kind == EntryPointKind.COMPOSE_DESTINATION.name
        }
        assertTrue(entryPoint.id.contains("compose-route:sample.Routes.TASKS"))
        assertEquals("TasksScreen", specification.components.flatMap { it.apis }
            .single { it.id == entryPoint.apiId }.name)
        val feature = specification.features.single { entryPoint.id in it.entryPointIds }
        val scenario = specification.scenarios.single { it.featureId == feature.id }
        assertEquals("TRIGGER", scenario.steps.first().action)
        assertTrue(scenario.steps.first().evidenceRefs.isNotEmpty())
        ProjectSpecificationValidator.validate(specification)
    }

    @Test
    fun `does not treat a user defined composable call as navigation registration`() {
        val source = """
            package sample
            fun composable(route: String, content: () -> Unit) = content()
            @Composable fun Host() { composable("tasks") { TasksScreen() } }
            @Composable fun TasksScreen() = Unit
        """.trimIndent()

        val specification = build(source)

        assertTrue(specification.entryPoints.none { it.kind == EntryPointKind.COMPOSE_DESTINATION.name })
    }

    @Test
    fun `records multiple deepest targets instead of selecting the first`() {
        val source = source().replace(
            "TasksScreen()",
            "if (ready) TasksScreen() else OtherScreen()",
        ) + "\n@Composable\nfun OtherScreen() = Unit\n"
        val index = index(source)
        val knowledge = DefaultKnowledgeGraphBuilder().buildWithEvidence(index)
        val base = DefaultSpecificationBuilder().build(
            SpecificationBuildRequest(ProjectDescriptor("sample", "Sample"), knowledge, index),
        )

        assertTrue(base.entryPoints.none { it.kind == EntryPointKind.COMPOSE_DESTINATION.name })
        assertTrue(base.unresolved.any { it.requiredAction == "MULTIPLE_DESTINATION_TARGETS" })
    }

    @Test
    fun `compose navigation observations and output ignore file order`() {
        val first = index(source())
        val second = first.copy(files = first.files.reversed())

        assertEquals(build(first), build(second))
    }

    @Test
    fun `compose evidence integrity rejects semantic tampering`() {
        val index = index(source())
        val knowledge = DefaultKnowledgeGraphBuilder().buildWithEvidence(index)
        val specification = DefaultSpecificationBuilder().build(
            SpecificationBuildRequest(ProjectDescriptor("sample", "Sample"), knowledge, index),
        )
        val result = ComposeNavigationEvidenceResolver().resolve(index, specification)

        assertFailsWith<IllegalArgumentException> {
            ComposeNavigationIntegrity.requireValid(result.copy(policyVersion = "tampered"))
        }
    }

    private fun build(source: String) = build(index(source))

    private fun build(index: SourceIndex) = DefaultSpecificationBuilder().build(
        SpecificationBuildRequest(
            ProjectDescriptor("sample", "Sample"),
            DefaultKnowledgeGraphBuilder().buildWithEvidence(index),
            index,
        ),
    )

    private fun index(source: String): SourceIndex {
        val file = SimpleKotlinSymbolExtractor().extract(
            "app/src/main/kotlin/sample/Nav.kt",
            SimpleKotlinLexer().tokenize(source),
        ).copy(candidateModulePath = "app", sourceSetName = "main")
        return SourceIndex(listOf(file))
    }

    private fun source() = """
        package sample

        import androidx.compose.runtime.Composable
        import androidx.navigation.compose.composable

        object Routes {
            const val TASKS = "tasks"
        }

        @Composable
        fun NavGraph() {
            composable(Routes.TASKS) {
                Wrapper {
                    TasksScreen()
                }
            }
        }

        @Composable
        fun Wrapper(content: () -> Unit) = content()

        @Composable
        fun TasksScreen() = Unit
    """.trimIndent()
}

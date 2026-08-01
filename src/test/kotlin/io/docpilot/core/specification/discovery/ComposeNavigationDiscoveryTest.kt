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

    @Test
    fun `resolves function reference destination without changing entry point identity`() {
        val referenceSource = functionReferenceSource("TasksScreen")
        val direct = build(referenceSource.replace(
            "composable(route = Routes.TASKS, content = ::TasksScreen)",
            "composable(route = Routes.TASKS) { TasksScreen() }",
        ))
        val referenced = build(referenceSource)

        val directEntry = direct.entryPoints.single { it.kind == EntryPointKind.COMPOSE_DESTINATION.name }
        val referencedEntry = referenced.entryPoints.single { it.kind == EntryPointKind.COMPOSE_DESTINATION.name }
        assertEquals(directEntry.id, referencedEntry.id)
        assertTrue(referenced.unresolved.none { it.requiredAction == "UNRESOLVED_FUNCTION_REFERENCE" })
        val evidenceTypes = referenced.evidence.filter { it.id in referencedEntry.evidenceRefs }.map { it.type }
        assertTrue("COMPOSE_FUNCTION_REFERENCE" in evidenceTypes)
    }

    @Test
    fun `records nested graph ownership structurally`() {
        val source = """
            package sample
            import androidx.compose.runtime.Composable
            import androidx.navigation.compose.composable
            import androidx.navigation.compose.navigation
            const val MAIN = "main"
            const val TASKS = "tasks/{taskId}"
            @Composable fun NavGraph() {
                navigation(route = MAIN, startDestination = TASKS) {
                    composable(TASKS, content = ::TasksScreen)
                }
            }
            @Composable fun TasksScreen() = Unit
        """.trimIndent()

        val observations = index(source).files.single().composeNavigation

        assertEquals(1, observations.graphs.size)
        val destination = observations.registrations.single { it.apiKind.name == "COMPOSABLE" }
        assertEquals(observations.graphs.single().id, destination.ownerGraphId)
        assertEquals(listOf("taskId"), destination.arguments.map { it.name })
        val specification = build(source)
        val entryPoint = specification.entryPoints.single { it.kind == EntryPointKind.COMPOSE_DESTINATION.name }
        val evidenceTypes = specification.evidence.filter { it.id in entryPoint.evidenceRefs }.map { it.type }
        assertTrue("COMPOSE_NAVIGATION_GRAPH" in evidenceTypes)
        assertTrue("COMPOSE_NAVIGATION_ARGUMENT" in evidenceTypes)
        val scenarioEvidence = specification.scenarios.single {
            it.entryPointId == entryPoint.id
        }.steps.flatMap { it.evidenceRefs }.toSet()
        assertTrue(entryPoint.evidenceRefs.intersect(scenarioEvidence).isNotEmpty())
    }

    @Test
    fun `extracts typed route argument evidence`() {
        val source = """
            package sample
            import kotlinx.serialization.Serializable
            @Serializable data class TaskRoute(val taskId: String, val filter: String? = null)
        """.trimIndent()

        val arguments = index(source).files.single().composeNavigation.routeArguments

        assertEquals(listOf("filter", "taskId"), arguments.map { it.name })
        assertTrue(arguments.single { it.name == "filter" }.nullable == true)
    }

    @Test
    fun `extracts navArgument declaration without inferring business meaning`() {
        val source = """
            package sample
            import androidx.compose.runtime.Composable
            import androidx.navigation.compose.composable
            import androidx.navigation.navArgument
            @Composable fun NavGraph() {
                composable(
                    route = "task/{taskId}",
                    arguments = listOf(navArgument("taskId") {
                        nullable = false
                        defaultValue = "unknown"
                    }),
                    content = ::TasksScreen,
                )
            }
            @Composable fun TasksScreen() = Unit
        """.trimIndent()

        val arguments = index(source).files.single().composeNavigation.registrations.single().arguments

        assertEquals(2, arguments.size)
        val declaration = arguments.single { it.sourceKind.name == "NAV_ARGUMENT_DECLARATION" }
        assertEquals("taskId", declaration.name)
        assertEquals(false, declaration.nullable)
        assertEquals("\"unknown\"", declaration.defaultValueExpression)
    }

    @Test
    fun `preserves unresolved function reference instead of guessing`() {
        val specification = build(functionReferenceSource("MissingScreen"))

        assertTrue(specification.entryPoints.none { it.kind == EntryPointKind.COMPOSE_DESTINATION.name })
        assertTrue(specification.unresolved.any { it.requiredAction == "UNRESOLVED_FUNCTION_REFERENCE" })
    }

    @Test
    fun `resolves a single immutable external lambda initializer`() {
        val source = """
            package sample
            import androidx.compose.runtime.Composable
            import androidx.navigation.compose.composable
            const val TASKS = "tasks"
            @Composable fun NavGraph() {
                val tasksContent = { TasksScreen() }
                composable(route = TASKS, content = tasksContent)
            }
            @Composable fun TasksScreen() = Unit
        """.trimIndent()

        val specification = build(source)

        assertTrue(specification.entryPoints.any { it.kind == EntryPointKind.COMPOSE_DESTINATION.name })
        assertTrue(specification.unresolved.none { it.requiredAction?.startsWith("EXTERNAL_LAMBDA") == true })
    }

    @Test
    fun `links typed route arguments only with matching destination signatures`() {
        val source = """
            package sample
            import androidx.compose.runtime.Composable
            import androidx.navigation.compose.composable
            import kotlinx.serialization.Serializable
            @Serializable data class TaskRoute(val taskId: String, val filter: String? = null) {}
            @Composable fun NavGraph() {
                composable<TaskRoute>(content = ::TasksScreen)
            }
            @Composable fun TasksScreen(taskId: String, filter: String?) = Unit
        """.trimIndent()
        val index = index(source)
        val knowledge = DefaultKnowledgeGraphBuilder().buildWithEvidence(index)
        val specification = DefaultSpecificationBuilder().build(
            SpecificationBuildRequest(ProjectDescriptor("sample", "Sample"), knowledge, index),
        )

        val result = ComposeNavigationEvidenceResolver().resolve(index, specification)

        assertEquals(2, result.argumentLinkIds.size)
    }

    @Test
    fun `records deterministic parent graph ownership`() {
        val source = """
            package sample
            import androidx.compose.runtime.Composable
            import androidx.navigation.compose.composable
            import androidx.navigation.compose.navigation
            @Composable fun NavGraph() {
                navigation(route = "root", startDestination = "nested") {
                    navigation(route = "nested", startDestination = "tasks") {
                        composable(route = "tasks", content = ::TasksScreen)
                    }
                }
            }
            @Composable fun TasksScreen() = Unit
        """.trimIndent()

        val graphs = index(source).files.single().composeNavigation.graphs

        val root = graphs.single { it.routeExpression == "\"root\"" }
        val nested = graphs.single { it.routeExpression == "\"nested\"" }
        assertEquals(root.id, nested.parentGraphId)
    }

    @Test
    fun `does not select a function reference overload without signature evidence`() {
        val source = """
            package sample
            import androidx.compose.runtime.Composable
            import androidx.navigation.compose.composable
            @Composable fun NavGraph() {
                composable(route = "tasks", content = ::TasksScreen)
            }
            @Composable fun TasksScreen() = Unit
            @Composable fun TasksScreen(id: String) = Unit
        """.trimIndent()

        val specification = build(source)

        assertTrue(specification.entryPoints.none { it.kind == EntryPointKind.COMPOSE_DESTINATION.name })
        assertTrue(specification.unresolved.any {
            it.requiredAction == "FUNCTION_REFERENCE_OVERLOAD_AMBIGUITY"
        })
    }

    @Test
    fun `does not resolve a bound member reference without receiver identity`() {
        val source = """
            package sample
            import androidx.compose.runtime.Composable
            import androidx.navigation.compose.composable
            @Composable fun NavGraph() {
                composable(route = "tasks", content = provider::TasksScreen)
            }
            @Composable fun TasksScreen() = Unit
        """.trimIndent()

        val specification = build(source)

        assertTrue(specification.unresolved.any {
            it.requiredAction == "FUNCTION_REFERENCE_RECEIVER_UNRESOLVED"
        })
    }

    private fun functionReferenceSource(target: String) = """
        package sample
        import androidx.compose.runtime.Composable
        import androidx.navigation.compose.composable
        object Routes { const val TASKS = "tasks" }
        @Composable fun NavGraph() {
            composable(route = Routes.TASKS, content = ::$target)
        }
        @Composable fun TasksScreen() = Unit
    """.trimIndent()

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

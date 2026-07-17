package io.docpilot.core.knowledge

import io.docpilot.core.model.knowledge.KnowledgeNodeKind
import io.docpilot.core.model.knowledge.RelationshipType
import io.docpilot.core.model.source.SourceFile
import io.docpilot.core.model.source.SourceImport
import io.docpilot.core.model.source.SourceIndex
import io.docpilot.core.model.source.SourceLanguage
import io.docpilot.core.model.source.SourceLocation
import io.docpilot.core.model.source.SourceModifier
import io.docpilot.core.model.source.SourceParameter
import io.docpilot.core.model.source.SourceSymbol
import io.docpilot.core.model.source.SourceSymbolKind
import io.docpilot.core.model.source.SourceVisibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultKnowledgeGraphBuilderTest {

    private val builder =
        DefaultKnowledgeGraphBuilder()

    @Test
    fun `builds package file symbol and import knowledge`() {
        val graph = builder.build(
            SourceIndex(
                files = listOf(
                    SourceFile(
                        relativePath =
                            "app/src/main/kotlin/example/Tasks.kt",
                        language = SourceLanguage.KOTLIN,
                        packageName = "example.tasks",
                        imports = listOf(
                            SourceImport(
                                qualifiedName =
                                    "kotlinx.coroutines.flow.Flow",
                                alias = "TaskFlow",
                            ),
                        ),
                        symbols = listOf(
                            SourceSymbol(
                                name = "TasksViewModel",
                                kind = SourceSymbolKind.CLASS,
                                visibility =
                                    SourceVisibility.INTERNAL,
                                location = SourceLocation(
                                    relativePath =
                                        "app/src/main/kotlin/example/Tasks.kt",
                                    lineStart = 8,
                                    columnStart = 1,
                                ),
                                children = listOf(
                                    SourceSymbol(
                                        name = "loadTasks",
                                        kind =
                                            SourceSymbolKind.FUNCTION,
                                        visibility =
                                            SourceVisibility.PUBLIC,
                                        location = SourceLocation(
                                            relativePath =
                                                "app/src/main/kotlin/example/Tasks.kt",
                                            lineStart = 12,
                                            columnStart = 5,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(5, graph.nodeCount)
        assertEquals(4, graph.edgeCount)

        assertNotNull(
            graph.node("package:example.tasks"),
        )
        assertNotNull(
            graph.node(
                "file:app/src/main/kotlin/example/Tasks.kt",
            ),
        )

        assertTrue(
            graph.nodes.any {
                it.kind == KnowledgeNodeKind.CLASS &&
                    it.name == "TasksViewModel"
            },
        )
        assertTrue(
            graph.nodes.any {
                it.kind == KnowledgeNodeKind.FUNCTION &&
                    it.name == "loadTasks"
            },
        )
        assertTrue(
            graph.nodes.any {
                it.kind ==
                    KnowledgeNodeKind.EXTERNAL_TYPE &&
                    it.name ==
                    "kotlinx.coroutines.flow.Flow"
            },
        )

        assertTrue(
            graph.edges.any {
                it.relationship ==
                    RelationshipType.CONTAINS
            },
        )
        assertEquals(
            2,
            graph.edges.count {
                it.relationship ==
                    RelationshipType.DECLARES
            },
        )
        assertTrue(
            graph.edges.any {
                it.relationship ==
                    RelationshipType.IMPORTS
            },
        )
    }

    @Test
    fun `deduplicates shared imported external types`() {
        val sharedImport = SourceImport(
            qualifiedName = "kotlin.collections.List",
        )

        val graph = builder.build(
            SourceIndex(
                files = listOf(
                    SourceFile(
                        relativePath = "A.kt",
                        language = SourceLanguage.KOTLIN,
                        imports = listOf(sharedImport),
                    ),
                    SourceFile(
                        relativePath = "B.kt",
                        language = SourceLanguage.KOTLIN,
                        imports = listOf(sharedImport),
                    ),
                ),
            ),
        )

        assertEquals(
            1,
            graph.nodes.count {
                it.kind ==
                    KnowledgeNodeKind.EXTERNAL_TYPE
            },
        )
        assertEquals(
            2,
            graph.edges.count {
                it.relationship ==
                    RelationshipType.IMPORTS
            },
        )
    }

    @Test
    fun `build output is deterministic`() {
        val sourceIndex = SourceIndex(
            files = listOf(
                SourceFile(
                    relativePath = "Z.kt",
                    language = SourceLanguage.KOTLIN,
                ),
                SourceFile(
                    relativePath = "A.kt",
                    language = SourceLanguage.KOTLIN,
                ),
            ),
        )

        val first = builder.build(sourceIndex)
        val second = builder.build(sourceIndex)

        assertEquals(first, second)
        assertEquals(
            first.nodes.sortedBy { it.id },
            first.nodes,
        )
        assertEquals(
            first.edges.sortedBy { it.id },
            first.edges,
        )
    }
    @Test
    fun `maps constructor and enriched symbol metadata`() {
        val constructor = SourceSymbol(
            name = "<init>",
            kind = SourceSymbolKind.CONSTRUCTOR,
            id = "constructor-id",
            qualifiedName = "example.Repository.<init>",
            signature = "constructor(name: String)",
            modifiers = setOf(SourceModifier.SUSPEND),
            parameters = listOf(SourceParameter("name", "String")),
            location = SourceLocation("Repository.kt", 2, 5, 2, 29),
        )
        val graph = builder.build(
            SourceIndex(
                files = listOf(
                    SourceFile(
                        relativePath = "Repository.kt",
                        language = SourceLanguage.KOTLIN,
                        symbols = listOf(constructor),
                    ),
                ),
            ),
        )

        val node = graph.nodes.single { it.kind == KnowledgeNodeKind.CONSTRUCTOR }
        assertEquals("symbol:constructor-id", node.id)
        assertEquals("example.Repository.<init>", node.attributes["qualifiedName"])
        assertEquals("constructor(name: String)", node.attributes["signature"])
        assertEquals("name:String", node.attributes["parameters"])
        assertTrue(graph.edges.any {
            it.relationship == RelationshipType.DECLARES &&
                it.targetNodeId == node.id
        })
    }

}

package io.docpilot.core.knowledge

import io.docpilot.core.model.evidence.EvidenceType
import io.docpilot.core.model.source.SourceFile
import io.docpilot.core.model.source.SourceImport
import io.docpilot.core.model.source.SourceIndex
import io.docpilot.core.model.source.SourceLanguage
import io.docpilot.core.model.source.SourceLocation
import io.docpilot.core.model.source.SourceSymbol
import io.docpilot.core.model.source.SourceSymbolKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KnowledgeEvidenceIntegrationTest {

    @Test
    fun `build result connects graph references to evidence`() {
        val result = DefaultKnowledgeGraphBuilder()
            .buildWithEvidence(
                SourceIndex(
                    files = listOf(
                        SourceFile(
                            relativePath = "Sample.kt",
                            language = SourceLanguage.KOTLIN,
                            packageName = "sample",
                            imports = listOf(
                                SourceImport(
                                    qualifiedName =
                                        "kotlin.collections.List",
                                ),
                            ),
                            symbols = listOf(
                                SourceSymbol(
                                    name = "Sample",
                                    kind =
                                        SourceSymbolKind.CLASS,
                                    location = SourceLocation(
                                        relativePath = "Sample.kt",
                                        lineStart = 4,
                                        columnStart = 1,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )

        assertEquals(4, result.evidence.size)
        assertTrue(
            result.evidence.items.any {
                it.type ==
                    EvidenceType.SYMBOL_DECLARATION &&
                    it.location.lineStart == 4
            },
        )

        val symbolNode = result.graph.nodes.first {
            it.name == "Sample"
        }

        val evidenceId = symbolNode.evidenceRefs.single()
        assertNotNull(
            result.evidence.items.firstOrNull {
                it.id.value == evidenceId
            },
        )
    }

    @Test
    fun `legacy build method still returns graph`() {
        val graph = DefaultKnowledgeGraphBuilder().build(
            SourceIndex(
                files = listOf(
                    SourceFile(
                        relativePath = "Sample.kt",
                        language = SourceLanguage.KOTLIN,
                    ),
                ),
            ),
        )

        assertEquals(1, graph.nodeCount)
    }
}

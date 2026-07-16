package io.docpilot.core.prompt

import io.docpilot.core.knowledge.DefaultKnowledgeGraphBuilder
import io.docpilot.core.model.source.SourceFile
import io.docpilot.core.model.source.SourceIndex
import io.docpilot.core.model.source.SourceLanguage
import io.docpilot.core.model.source.SourceLocation
import io.docpilot.core.model.source.SourceSymbol
import io.docpilot.core.model.source.SourceSymbolKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultPromptPackageBuilderTest {

    private val builder =
        DefaultPromptPackageBuilder()

    @Test
    fun `builds deterministic prompt package`() {
        val knowledge =
            DefaultKnowledgeGraphBuilder()
                .buildWithEvidence(
                    SourceIndex(
                        files = listOf(
                            SourceFile(
                                relativePath = "Sample.kt",
                                language =
                                    SourceLanguage.KOTLIN,
                                packageName = "sample",
                                symbols = listOf(
                                    SourceSymbol(
                                        name = "Sample",
                                        kind =
                                            SourceSymbolKind.CLASS,
                                        location =
                                            SourceLocation(
                                                relativePath =
                                                    "Sample.kt",
                                                lineStart = 2,
                                                columnStart = 1,
                                            ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )

        val promptPackage = builder.build(knowledge)

        assertEquals(4, promptPackage.artifactCount)
        assertNotNull(
            promptPackage.artifact(
                "prompt-package/overview.md",
            ),
        )
        assertNotNull(
            promptPackage.artifact(
                "prompt-package/knowledge-graph.json",
            ),
        )
        assertNotNull(
            promptPackage.artifact(
                "prompt-package/evidence.json",
            ),
        )
        assertNotNull(
            promptPackage.artifact(
                "prompt-package/instructions.md",
            ),
        )

        val evidence =
            promptPackage.artifact(
                "prompt-package/evidence.json",
            )!!.content

        assertTrue(
            evidence.contains(
                "\"type\": \"SYMBOL_DECLARATION\"",
            ),
        )
        assertTrue(
            evidence.contains(
                "\"lineStart\": 2",
            ),
        )
    }

    @Test
    fun `build output is stable`() {
        val knowledge =
            DefaultKnowledgeGraphBuilder()
                .buildWithEvidence(
                    SourceIndex(
                        files = emptyList(),
                    ),
                )

        assertEquals(
            builder.build(knowledge),
            builder.build(knowledge),
        )
    }
}

package io.docpilot.core.model.evidence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class EvidenceModelTest {

    @Test
    fun `creates evidence collection`() {
        val evidence = Evidence(
            id = EvidenceId("source:Sample.kt:2"),
            type = EvidenceType.SYMBOL_DECLARATION,
            location = EvidenceLocation(
                relativePath = "Sample.kt",
                lineStart = 2,
                columnStart = 1,
            ),
            summary = "Class Sample is declared.",
            attributes = mapOf(
                "symbolName" to "Sample",
            ),
        )

        val collection = EvidenceCollection(
            items = listOf(evidence),
        )

        assertEquals(1, collection.size)
        assertNotNull(
            collection.find(
                EvidenceId("source:Sample.kt:2"),
            ),
        )
    }

    @Test
    fun `rejects invalid evidence locations`() {
        assertFailsWith<IllegalArgumentException> {
            EvidenceLocation(
                relativePath = "Sample.kt",
                lineStart = 0,
            )
        }

        assertFailsWith<IllegalArgumentException> {
            EvidenceLocation(
                relativePath = "Sample.kt",
                lineStart = 10,
                lineEnd = 5,
            )
        }
    }

    @Test
    fun `rejects duplicate evidence IDs`() {
        val first = Evidence(
            id = EvidenceId("same"),
            type = EvidenceType.SOURCE_FILE,
            location = EvidenceLocation("A.kt"),
            summary = "First",
        )
        val second = Evidence(
            id = EvidenceId("same"),
            type = EvidenceType.SOURCE_FILE,
            location = EvidenceLocation("B.kt"),
            summary = "Second",
        )

        assertFailsWith<IllegalArgumentException> {
            EvidenceCollection(
                items = listOf(first, second),
            )
        }
    }

    @Test
    fun `rejects blank evidence summaries`() {
        assertFailsWith<IllegalArgumentException> {
            Evidence(
                id = EvidenceId("evidence:blank"),
                type = EvidenceType.UNKNOWN,
                location = EvidenceLocation("Sample.kt"),
                summary = " ",
            )
        }
    }
}

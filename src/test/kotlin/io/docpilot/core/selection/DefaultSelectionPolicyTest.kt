package io.docpilot.core.selection

import io.docpilot.core.model.selection.SelectionContext
import io.docpilot.core.model.selection.SelectionReason
import io.docpilot.core.model.selection.SelectionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DefaultSelectionPolicyTest {

    private val policy =
        DefaultSelectionPolicy<Candidate> { it.id }

    @Test
    fun `explicit selection has highest priority`() {
        val result = policy.select(
            candidates = candidates(),
            context = SelectionContext(
                explicitCandidateId = "openai",
                preferredCandidateIds = listOf("ollama"),
                priorities = mapOf(
                    "ollama" to 100,
                    "openai" to 90,
                ),
            ),
        )

        val selected =
            assertIs<SelectionResult.Selected<Candidate>>(
                result,
            )

        assertEquals("openai", selected.candidate.id)
        assertEquals(
            SelectionReason.EXPLICIT,
            selected.reason,
        )
    }

    @Test
    fun `preferred candidate is used before priorities`() {
        val result = policy.select(
            candidates = candidates(),
            context = SelectionContext(
                preferredCandidateIds = listOf(
                    "gemini",
                    "ollama",
                ),
                priorities = mapOf(
                    "ollama" to 100,
                    "gemini" to 80,
                ),
            ),
        )

        val selected =
            assertIs<SelectionResult.Selected<Candidate>>(
                result,
            )

        assertEquals("gemini", selected.candidate.id)
        assertEquals(
            SelectionReason.PREFERRED,
            selected.reason,
        )
    }

    @Test
    fun `priority selects highest value with ID tie break`() {
        val result = policy.select(
            candidates = candidates(),
            context = SelectionContext(
                priorities = mapOf(
                    "ollama" to 100,
                    "openai" to 100,
                    "gemini" to 80,
                ),
            ),
        )

        val selected =
            assertIs<SelectionResult.Selected<Candidate>>(
                result,
            )

        assertEquals("ollama", selected.candidate.id)
        assertEquals(
            SelectionReason.PRIORITY,
            selected.reason,
        )
    }

    @Test
    fun `fallback is deterministic by candidate ID`() {
        val result = policy.select(
            candidates = candidates(),
        )

        val selected =
            assertIs<SelectionResult.Selected<Candidate>>(
                result,
            )

        assertEquals("gemini", selected.candidate.id)
        assertEquals(
            SelectionReason.DETERMINISTIC_FALLBACK,
            selected.reason,
        )
    }

    @Test
    fun `missing explicit candidate does not silently fallback`() {
        val result = policy.select(
            candidates = candidates(),
            context = SelectionContext(
                explicitCandidateId = "missing",
            ),
        )

        assertIs<SelectionResult.Unavailable>(result)
    }

    private fun candidates(): List<Candidate> =
        listOf(
            Candidate("openai"),
            Candidate("ollama"),
            Candidate("gemini"),
        )

    private data class Candidate(
        val id: String,
    )
}

package io.docpilot.core.selection

import io.docpilot.core.model.selection.SelectionContext
import io.docpilot.core.model.selection.SelectionReason
import io.docpilot.core.model.selection.SelectionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DefaultSelectionPolicyTest {

    private val policy =
        DefaultSelectionPolicy<Candidate, CandidateId>(
            candidateId = { candidate ->
                candidate.id
            },
        )

    @Test
    fun `selects by typed priority`() {
        val result = policy.select(
            candidates = candidates(),
            context = SelectionContext(
                priorities = mapOf(
                    CandidateId("ollama") to 100,
                    CandidateId("openai") to 90,
                ),
            ),
        )

        val selected =
            assertIs<
                SelectionResult.Selected<Candidate, CandidateId>
            >(result)

        assertEquals(CandidateId("ollama"), selected.candidateId)
        assertEquals(SelectionReason.PRIORITY, selected.reason)
    }

    @Test
    fun `fallback is deterministic`() {
        val result = policy.select(candidates())

        val selected =
            assertIs<
                SelectionResult.Selected<Candidate, CandidateId>
            >(result)

        assertEquals(CandidateId("gemini"), selected.candidateId)
    }

    private fun candidates() = listOf(
        Candidate(CandidateId("openai")),
        Candidate(CandidateId("ollama")),
        Candidate(CandidateId("gemini")),
    )

    @JvmInline
    private value class CandidateId(
        val value: String,
    ) : Comparable<CandidateId> {
        override fun compareTo(other: CandidateId): Int =
            value.compareTo(other.value)
    }

    private data class Candidate(
        val id: CandidateId,
    )
}

package io.docpilot.core.incremental.prompt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DeterministicPromptTokenEstimatorTest {
    @Test
    fun `estimates by deterministic character groups`() {
        val estimator = DeterministicPromptTokenEstimator(charactersPerToken = 4)

        assertEquals(0, estimator.estimate(""))
        assertEquals(1, estimator.estimate("a"))
        assertEquals(1, estimator.estimate("abcd"))
        assertEquals(2, estimator.estimate("abcde"))
    }

    @Test
    fun `requires a positive character ratio`() {
        assertFailsWith<IllegalArgumentException> {
            DeterministicPromptTokenEstimator(0)
        }
    }
}

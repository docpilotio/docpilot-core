package io.docpilot.release

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReleaseGateEvaluatorTest {
    private val evaluator = ReleaseGateEvaluator()

    @Test
    fun `complete evidence passes`() {
        assertEquals(EvidenceResult.PASS, evaluator.evaluate(passingInput()).result)
    }

    @Test
    fun `cached skipped and failed evidence fails closed in stable order`() {
        val input = passingInput().copy(
            testAggregate = TestAggregate(1, 3, 1, 1, 1, fresh = false, cached = true),
        )

        assertEquals(
            listOf(
                GateFailure.CACHED_TEST_EVIDENCE,
                GateFailure.TEST_FAILURES,
                GateFailure.TEST_ERRORS,
                GateFailure.TESTS_SKIPPED,
            ),
            evaluator.evaluate(input).failures,
        )
    }

    @Test
    fun `missing mandatory evidence cannot pass`() {
        val input = passingInput().copy(
            executions = passingInput().executions.drop(1),
            artifacts = passingInput().artifacts.drop(1),
            compatibilityChecks = passingInput().compatibilityChecks.drop(1),
            scopeChecks = passingInput().scopeChecks.copy(documentationSynchronized = false),
        )
        val failures = evaluator.evaluate(input).failures

        assertTrue(GateFailure.MISSING_EXECUTION in failures)
        assertTrue(GateFailure.MISSING_ARTIFACT in failures)
        assertTrue(GateFailure.COMPATIBILITY_FAILED in failures)
        assertTrue(GateFailure.DOCUMENTATION_NOT_SYNCHRONIZED in failures)
    }
}

package io.docpilot.core.specification.claim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClaimTraceabilityCheckerTest {
    private val specification = ClaimFixtures.specification()
    private val entitySubject = ClaimSubject(ClaimSubjectKind.ENTITY, "component:sample")

    private fun evidenceBackedClaim() = ClaimFactory.deterministic(
        specification, entitySubject, "sample-assertion", "Sample is a component.",
        evidenceRefs = setOf(ClaimFixtures.highEvidence.id),
    )

    private fun contractBackedClaim() = ClaimFactory.deterministic(
        specification, entitySubject, "contract-assertion", "Sample exposes a Contract.",
        evidenceRefs = setOf(ClaimFixtures.contract.id),
    )

    @Test
    fun `unchanged specification with valid claims yields no issues`() {
        val claims = listOf(evidenceBackedClaim(), contractBackedClaim())
        val issues = ClaimTraceabilityChecker.check(specification, claims)
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `entity subject removed from a newer specification is orphan`() {
        val claim = evidenceBackedClaim()
        val newer = specification.copy(components = emptyList())
        val issues = ClaimTraceabilityChecker.check(newer, listOf(claim))
        assertEquals(listOf(ClaimTraceabilityIssueReason.ORPHAN), issues.map { it.reason })
    }

    @Test
    fun `evidence summary drift with ref still present is stale`() {
        val claim = evidenceBackedClaim()
        val newer = specification.copy(
            evidence = specification.evidence.map {
                if (it.id == ClaimFixtures.highEvidence.id) it.copy(summary = "Updated summary") else it
            },
        )
        val issues = ClaimTraceabilityChecker.check(newer, listOf(claim))
        assertEquals(listOf(ClaimTraceabilityIssueReason.STALE), issues.map { it.reason })
    }

    @Test
    fun `evidence ref removed entirely is broken, not also stale`() {
        val claim = evidenceBackedClaim()
        val newer = specification.copy(evidence = specification.evidence.filterNot { it.id == ClaimFixtures.highEvidence.id })
        val issues = ClaimTraceabilityChecker.check(newer, listOf(claim))
        assertEquals(listOf(ClaimTraceabilityIssueReason.BROKEN), issues.map { it.reason })
    }

    @Test
    fun `contract displayName drift is stale`() {
        val claim = contractBackedClaim()
        val newer = specification.copy(
            contracts = specification.contracts.map {
                if (it.id == ClaimFixtures.contract.id) it.copy(displayName = "Renamed Contract") else it
            },
        )
        val issues = ClaimTraceabilityChecker.check(newer, listOf(claim))
        assertEquals(listOf(ClaimTraceabilityIssueReason.STALE), issues.map { it.reason })
    }
}

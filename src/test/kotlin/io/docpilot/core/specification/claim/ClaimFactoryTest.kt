package io.docpilot.core.specification.claim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class ClaimFactoryTest {
    private val specification = ClaimFixtures.specification()
    private val entitySubject = ClaimSubject(ClaimSubjectKind.ENTITY, "component:sample")
    private val sectionSubject = ClaimSubject(ClaimSubjectKind.SECTION, "section:overview")

    @Test
    fun `deterministic claim with evidence-only ref succeeds and id matches ClaimIdentity`() {
        val claim = ClaimFactory.deterministic(
            specification, entitySubject, "sample-assertion", "Sample is a component.",
            evidenceRefs = setOf(ClaimFixtures.highEvidence.id),
        )
        assertEquals(ClaimIdentity.of(entitySubject, "sample-assertion"), claim.id.value)
    }

    @Test
    fun `deterministic claim with contract-only ref succeeds`() {
        val claim = ClaimFactory.deterministic(
            specification, entitySubject, "sample-assertion", "Sample exposes a Contract.",
            evidenceRefs = setOf(ClaimFixtures.contract.id),
        )
        assertEquals(setOf(ClaimFixtures.contract.id), claim.evidenceRefs)
    }

    @Test
    fun `empty evidenceRefs is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ClaimFactory.deterministic(specification, entitySubject, "sample-assertion", "Sample.", evidenceRefs = emptySet())
        }
    }

    @Test
    fun `ref not resolving to Evidence or Contract is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ClaimFactory.deterministic(specification, entitySubject, "sample-assertion", "Sample.", evidenceRefs = setOf("evidence:missing"))
        }
    }

    @Test
    fun `only low-confidence evidence is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ClaimFactory.deterministic(specification, entitySubject, "sample-assertion", "Sample.", evidenceRefs = setOf(ClaimFixtures.lowEvidence.id))
        }
    }

    @Test
    fun `unresolvedRefs not present in specification unresolved is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ClaimFactory.deterministic(
                specification, entitySubject, "sample-assertion", "Sample.",
                evidenceRefs = setOf(ClaimFixtures.highEvidence.id),
                unresolvedRefs = setOf("unresolved:missing-entirely"),
            )
        }
    }

    @Test
    fun `same subject and semanticKey yields identical id, different semanticKey differs`() {
        val first = ClaimFactory.deterministic(specification, entitySubject, "sample-assertion", "Sample.", evidenceRefs = setOf(ClaimFixtures.highEvidence.id))
        val second = ClaimFactory.deterministic(specification, entitySubject, "sample-assertion", "Sample, restated.", evidenceRefs = setOf(ClaimFixtures.highEvidence.id))
        val differentKey = ClaimFactory.deterministic(specification, entitySubject, "other-assertion", "Sample.", evidenceRefs = setOf(ClaimFixtures.highEvidence.id))
        assertEquals(first.id, second.id)
        assertNotEquals(first.id, differentKey.id)
    }

    @Test
    fun `deterministic origin may target an entity subject`() {
        val claim = ClaimFactory.deterministic(specification, entitySubject, "sample-assertion", "Sample.", evidenceRefs = setOf(ClaimFixtures.highEvidence.id))
        assertEquals(ClaimOrigin.DETERMINISTIC, claim.origin)
    }

    @Test
    fun `ai narrative claim cannot target an entity subject`() {
        assertFailsWith<IllegalArgumentException> {
            ClaimFactory.aiNarrative(specification, entitySubject, "sample-assertion", "Sample.", evidenceRefs = setOf(ClaimFixtures.highEvidence.id))
        }
    }

    @Test
    fun `ai patch proposal claim cannot target an entity subject`() {
        assertFailsWith<IllegalArgumentException> {
            ClaimFactory.aiPatchProposal(specification, entitySubject, "sample-assertion", "Sample.", evidenceRefs = setOf(ClaimFixtures.highEvidence.id))
        }
    }

    @Test
    fun `ai narrative restating one of its own evidenceRefs is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ClaimFactory.aiNarrative(
                specification, sectionSubject, "overview-narrative",
                "This section relies on ${ClaimFixtures.highEvidence.id} directly.",
                evidenceRefs = setOf(ClaimFixtures.highEvidence.id),
            )
        }
    }

    @Test
    fun `ai narrative redefining a canonical field is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ClaimFactory.aiNarrative(
                specification, sectionSubject, "overview-narrative",
                "Stable ID: component:sample is the owner.",
                evidenceRefs = setOf(ClaimFixtures.highEvidence.id),
            )
        }
    }
}

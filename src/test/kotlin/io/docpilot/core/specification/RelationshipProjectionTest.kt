package io.docpilot.core.specification

import io.docpilot.core.model.RelationshipSpecification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RelationshipProjectionTest {
    @Test
    fun `canonical identity is delimiter safe and stable`() {
        val first = RelationshipIdentity.of("CALLS", "api:a->b", "external:c:d")
        val second = RelationshipIdentity.of("CALLS", "api:a", "b->external:c:d")

        assertNotEquals(first, second)
        assertEquals(first, RelationshipIdentity.of("CALLS", "api:a->b", "external:c:d"))
    }

    @Test
    fun `aggregates evidence before deterministic call threshold`() {
        val candidates = listOf(
            relationship("CALLS", "api:source", "external:z", "e:3"),
            relationship("CALLS", "api:source", "external:a", "e:1"),
            relationship("CALLS", "api:source", "external:a", "e:2"),
        )
        val policy = RelationshipProjectionPolicy(
            maxCallsPerSource = 1,
            maxCallsPerProject = 10,
            maxImportsPerSourcePackage = 10,
            maxImportsPerProject = 10,
        )

        val first = RelationshipProjector.project(candidates, policy)
        val second = RelationshipProjector.project(candidates.reversed(), policy)

        assertEquals(first, second)
        assertEquals(listOf("external:a"), first.relationships.map { it.targetId })
        assertEquals(setOf("e:1", "e:2"), first.relationships.single().evidenceRefs)
        assertEquals(1, first.report.aggregatedOccurrenceCountByKind["CALLS"])
        assertEquals(1, first.report.omittedCountByKind["CALLS"])
        assertEquals(64, first.report.reportSha256.length)
        assertEquals(64, first.report.omittedIdentitySha256ByKind.getValue("CALLS").length)
    }

    @Test
    fun `fails closed on overflow when policy requires it`() {
        val policy = RelationshipProjectionPolicy(
            maxCallsPerSource = 1,
            maxCallsPerProject = 1,
            maxImportsPerSourcePackage = 1,
            maxImportsPerProject = 1,
            overflowBehavior = RelationshipOverflowBehavior.FAIL_CLOSED,
        )

        assertFailsWith<IllegalStateException> {
            RelationshipProjector.project(
                listOf(
                    relationship("IMPORTS", "package:source", "external:a", "e:1"),
                    relationship("IMPORTS", "package:source", "external:b", "e:2"),
                ),
                policy,
            )
        }
    }

    @Test
    fun `never truncates structural relationship kinds`() {
        val policy = RelationshipProjectionPolicy(
            maxCallsPerSource = 1,
            maxCallsPerProject = 1,
            maxImportsPerSourcePackage = 1,
            maxImportsPerProject = 1,
        )
        val structural = listOf("DEPENDS_ON", "EXTENDS", "IMPLEMENTS").flatMap { kind ->
            (1..3).map { relationship(kind, "type:source", "external:$kind:$it", "e:$kind:$it") }
        }

        val result = RelationshipProjector.project(structural, policy)

        assertEquals(9, result.relationships.size)
        assertTrue(result.report.omittedCountByKind.isEmpty())
    }

    private fun relationship(
        type: String,
        source: String,
        target: String,
        evidence: String,
    ) = RelationshipSpecification(
        id = "observation:$evidence",
        type = type,
        sourceId = source,
        targetId = target,
        evidenceRefs = setOf(evidence),
    )
}

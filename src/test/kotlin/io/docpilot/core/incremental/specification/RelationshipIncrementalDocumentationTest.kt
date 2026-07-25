package io.docpilot.core.incremental.specification

import io.docpilot.core.incremental.specification.ai.DefaultSpecificationIncrementalPromptBuilder
import io.docpilot.core.incremental.specification.ai.AiDocumentationPatch
import io.docpilot.core.incremental.specification.review.DefaultDocumentationDiffReviewer
import io.docpilot.core.incremental.specification.review.DocumentationReviewRequest
import io.docpilot.core.model.ApiSpecification
import io.docpilot.core.model.ComponentSpecification
import io.docpilot.core.model.Evidence
import io.docpilot.core.model.EvidenceConfidence
import io.docpilot.core.model.ModuleSpecification
import io.docpilot.core.model.PackageSpecification
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.PropertySpecification
import io.docpilot.core.model.RelationshipSpecification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RelationshipIncrementalDocumentationTest {
    @Test
    fun `diffs relationships by stable id with deterministic change kinds`() {
        val previous = specification(
            relationships = listOf(
                relationship("relationship:modified", "type:one", "type:two", "before", setOf("evidence:one")),
                relationship("relationship:removed", "api:one", "external:old"),
            ),
        )
        val current = specification(
            relationships = listOf(
                relationship("relationship:added", "property:two", "package:one"),
                relationship("relationship:modified", "type:one", "type:two", "after", setOf("evidence:two")),
            ),
        )

        val changes = DefaultSpecificationDiffer().diff(previous, current).relationshipChanges

        assertEquals(
            listOf(
                "relationship:added" to ChangeKind.ADDED,
                "relationship:modified" to ChangeKind.MODIFIED,
                "relationship:removed" to ChangeKind.REMOVED,
            ),
            changes.map { it.id to it.kind },
        )
        assertEquals(
            listOf("property:two", "type:one", "api:one"),
            changes.map { it.parentId },
        )
    }

    @Test
    fun `rejects duplicate relationship stable ids`() {
        val duplicate = relationship("relationship:duplicate", "type:one", "type:two")
        val invalid = specification(relationships = listOf(duplicate, duplicate.copy(description = "other")))

        val error = assertFailsWith<IllegalArgumentException> {
            DefaultSpecificationDiffer().diff(invalid, specification())
        }

        assertTrue(error.message!!.contains("Duplicate stable id 'relationship:duplicate'"))
    }

    @Test
    fun `plans relationship actions and unions previous and current endpoint scopes`() {
        val previous = specification(
            relationships = listOf(
                relationship("relationship:modified", "type:one", "type:two", "before"),
                relationship("relationship:removed", "api:one", "external:old"),
            ),
        )
        val current = specification(
            relationships = listOf(
                relationship("relationship:added", "property:two", "package:one"),
                relationship("relationship:modified", "type:one", "type:two", "after"),
            ),
        )

        val plan = IncrementalDocumentationEngine().analyze(previous, current).plan

        assertEquals(
            listOf(
                "relationship:removed" to ChangeKind.REMOVED,
                "relationship:added" to ChangeKind.ADDED,
                "relationship:modified" to ChangeKind.MODIFIED,
            ),
            plan.actions
                .filter { it.target == IncrementalUpdateTarget.RELATIONSHIP }
                .map { it.id to it.changeKind },
        )
        assertEquals(listOf("type:one", "type:two"), plan.changedTypeIds)
        assertEquals(listOf("package:one", "package:two"), plan.changedPackageIds)
        assertTrue(plan.requiresUpdate)
    }

    @Test
    fun `renders relationship prompt context and unions review evidence`() {
        val previous = specification(
            relationships = listOf(
                relationship(
                    "relationship:modified",
                    "type:one",
                    "external:database",
                    "before",
                    setOf("evidence:one", "evidence:shared"),
                ),
            ),
        )
        val current = specification(
            relationships = listOf(
                relationship(
                    "relationship:modified",
                    "type:one",
                    "external:database",
                    "after",
                    setOf("evidence:shared", "evidence:two"),
                ),
            ),
        )
        val plan = IncrementalDocumentationEngine().analyze(previous, current).plan
        val prompt = DefaultSpecificationIncrementalPromptBuilder()
            .build(previous, current, plan.actions)

        assertTrue(prompt.contains("CHANGE MODIFIED RELATIONSHIP relationship:modified"))
        assertTrue(prompt.contains("BEFORE id=relationship:modified"))
        assertTrue(prompt.contains("AFTER id=relationship:modified"))
        assertTrue(prompt.contains("sourceKind=INTERNAL"))
        assertTrue(prompt.contains("targetKind=EXTERNAL"))
        assertTrue(prompt.contains("evidence=evidence:one, evidence:shared"))

        val proposal = DefaultDocumentationDiffReviewer().propose(
            DocumentationReviewRequest(
                previousSpecification = previous,
                currentSpecification = current,
                updatePlan = plan,
                existingDocumentation = "",
                patches = listOf(AiDocumentationPatch("relationship:modified", "Updated relationship.")),
            ),
        )

        assertEquals(IncrementalUpdateTarget.RELATIONSHIP, proposal.entries.single().target)
        assertEquals(
            listOf("evidence:one", "evidence:shared", "evidence:two"),
            proposal.entries.single().evidenceIds,
        )
    }

    private fun relationship(
        id: String,
        sourceId: String,
        targetId: String,
        description: String? = null,
        evidenceRefs: Set<String> = emptySet(),
    ): RelationshipSpecification = RelationshipSpecification(
        id = id,
        type = "DEPENDS_ON",
        sourceId = sourceId,
        targetId = targetId,
        description = description,
        evidenceRefs = evidenceRefs,
    )

    private fun specification(
        relationships: List<RelationshipSpecification> = emptyList(),
    ): ProjectSpecification = ProjectSpecification(
        schemaVersion = "0.3",
        project = ProjectDescriptor("project:test", "Test"),
        modules = listOf(ModuleSpecification("module:one", "one"), ModuleSpecification("module:two", "two")),
        packages = listOf(
            PackageSpecification("package:one", "one", "sample.one", "module:one"),
            PackageSpecification("package:two", "two", "sample.two", "module:two"),
        ),
        components = listOf(
            ComponentSpecification(
                id = "type:one",
                name = "One",
                moduleId = "module:one",
                packageId = "package:one",
                kind = "CLASS",
                role = "one",
                apis = listOf(ApiSpecification("api:one", "one", "function")),
            ),
            ComponentSpecification(
                id = "type:two",
                name = "Two",
                moduleId = "module:two",
                packageId = "package:two",
                kind = "CLASS",
                role = "two",
                properties = listOf(PropertySpecification("property:two", "two")),
            ),
        ),
        relationships = relationships,
        evidence = listOf(
            Evidence("evidence:one", "source", summary = "one", confidence = EvidenceConfidence.HIGH),
            Evidence("evidence:shared", "source", summary = "shared", confidence = EvidenceConfidence.HIGH),
            Evidence("evidence:two", "source", summary = "two", confidence = EvidenceConfidence.HIGH),
        ),
    )
}

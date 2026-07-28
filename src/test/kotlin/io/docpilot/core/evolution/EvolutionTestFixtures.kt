package io.docpilot.core.evolution

import io.docpilot.core.api.DocumentationArtifactDescriptor
import io.docpilot.core.api.DocumentationArtifactId
import io.docpilot.core.api.DocumentationArtifactKind
import io.docpilot.core.incremental.execution.DefaultSelectiveDocumentationArtifactPlanner
import io.docpilot.core.incremental.execution.DocumentationArtifactPlanningRequest
import io.docpilot.core.incremental.execution.ExistingDocumentationArtifact
import io.docpilot.core.incremental.specification.ChangeKind
import io.docpilot.core.incremental.specification.IncrementalUpdateAction
import io.docpilot.core.incremental.specification.IncrementalUpdatePlan
import io.docpilot.core.incremental.specification.IncrementalUpdateTarget
import io.docpilot.core.incremental.specification.snapshot.JsonSpecificationSnapshotCodec
import io.docpilot.core.incremental.specification.snapshot.SpecificationSnapshotLoadResult
import io.docpilot.core.incremental.specification.snapshot.StoredSpecificationSnapshot
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
import io.docpilot.core.specification.RelationshipIdentity
import io.docpilot.core.specification.RelationshipProjectionIntegrity
import io.docpilot.core.specification.RelationshipProjectionPolicy
import io.docpilot.core.specification.RelationshipProjectionReport

internal object EvolutionTestFixtures {
    const val PROJECT_ID = "project:test"
    const val COMPONENT_ID = "type:service"
    const val API_ID = "api:run"
    const val PROPERTY_ID = "property:count"
    const val RELATIONSHIP_EVIDENCE = "e:relationship"
    val RELATIONSHIP_ID: String = RelationshipIdentity.of("CALLS", API_ID, "external:logger")

    fun request(reverse: Boolean = false): DocumentationEvolutionRequest {
        val before = specification(before = true, reverse = reverse)
        val after = specification(before = false, reverse = reverse)
        val component = descriptor(
            "component:service",
            "docs/service.md",
            listOf(COMPONENT_ID, API_ID, PROPERTY_ID, RELATIONSHIP_ID).sorted(),
        )
        val summary = descriptor(
            "project:summary",
            "docs/project.md",
            listOf(PROJECT_ID),
            dependencies = listOf(component.artifactId),
        )
        val catalog = listOf(component, summary).let { if (reverse) it.reversed() else it }
        val existing = listOf(
            ExistingDocumentationArtifact("docs/service.md", "text/markdown", "before-service"),
            ExistingDocumentationArtifact("docs/project.md", "text/markdown", "before-project"),
        ).let { if (reverse) it.reversed() else it }
        val updatePlan = IncrementalUpdatePlan(
            actions = listOf(
                IncrementalUpdateAction(IncrementalUpdateTarget.TYPE, COMPONENT_ID, changeKind = ChangeKind.MODIFIED),
                IncrementalUpdateAction(IncrementalUpdateTarget.API, API_ID, COMPONENT_ID, ChangeKind.MODIFIED),
                IncrementalUpdateAction(IncrementalUpdateTarget.PROPERTY, PROPERTY_ID, COMPONENT_ID, ChangeKind.MODIFIED),
                IncrementalUpdateAction(IncrementalUpdateTarget.RELATIONSHIP, RELATIONSHIP_ID, API_ID, ChangeKind.ADDED),
            ),
            changedTypeIds = listOf(COMPONENT_ID),
        )
        val plan = DefaultSelectiveDocumentationArtifactPlanner().plan(
            DocumentationArtifactPlanningRequest(before, after, catalog, catalog, updatePlan, existing),
        )
        val beforeStates = listOf(
            state(component.artifactId, component.relativePath, "before-service"),
            state(summary.artifactId, summary.relativePath, "before-project"),
        ).let { if (reverse) it.reversed() else it }
        val afterStates = listOf(
            state(component.artifactId, component.relativePath, "after-service"),
            state(summary.artifactId, summary.relativePath, "after-project"),
        ).let { if (reverse) it.reversed() else it }
        return DocumentationEvolutionRequest(
            beforeSnapshot = snapshot(before),
            afterSnapshot = snapshot(after),
            beforeCatalog = catalog,
            afterCatalog = catalog,
            artifactPlan = plan,
            existingArtifacts = existing,
            beforeRelationshipReport = relationshipReport(0),
            afterRelationshipReport = relationshipReport(1),
            beforeArtifactStates = beforeStates,
            afterArtifactStates = afterStates,
            additionalEvidenceRefs = listOf("validation:test").let { if (reverse) it.reversed() else it },
        )
    }

    fun specification(before: Boolean, reverse: Boolean = false): ProjectSpecification {
        val evidence = listOf(
            evidence("e:module"), evidence("e:package-a"), evidence("e:package-b"),
            evidence("e:component"), evidence("e:api"), evidence("e:property"), evidence(RELATIONSHIP_EVIDENCE),
        ).let { if (reverse) it.reversed() else it }
        val modules = listOf(ModuleSpecification("module:app", "app", "app", evidenceRefs = setOf("e:module")))
        val packages = listOf(
            PackageSpecification("package:a", "a", "example.a", "module:app", evidenceRefs = setOf("e:package-a")),
            PackageSpecification("package:b", "b", "example.b", "module:app", evidenceRefs = setOf("e:package-b")),
        ).let { if (reverse) it.reversed() else it }
        val component = ComponentSpecification(
            id = COMPONENT_ID,
            name = if (before) "Service" else "RenamedService",
            moduleId = "module:app",
            packageId = if (before) "package:a" else "package:b",
            qualifiedName = if (before) "example.a.Service" else "example.b.RenamedService",
            kind = "CLASS",
            role = "service",
            apis = listOf(
                ApiSpecification(
                    API_ID,
                    "run",
                    "FUNCTION",
                    signature = if (before) "run(): Unit" else "run(input: String): Unit",
                    evidenceRefs = setOf("e:api"),
                ),
            ),
            properties = listOf(
                PropertySpecification(
                    PROPERTY_ID,
                    "count",
                    type = if (before) "Int" else "Long",
                    evidenceRefs = setOf("e:property"),
                ),
            ),
            evidenceRefs = setOf("e:component"),
        )
        val relationships = if (before) emptyList() else listOf(
            RelationshipSpecification(
                RELATIONSHIP_ID,
                "CALLS",
                API_ID,
                "external:logger",
                evidenceRefs = setOf(RELATIONSHIP_EVIDENCE),
            ),
        )
        return ProjectSpecification(
            schemaVersion = "0.3",
            project = ProjectDescriptor(PROJECT_ID, "Test"),
            modules = modules,
            packages = packages,
            components = listOf(component),
            relationships = relationships,
            evidence = evidence,
        )
    }

    fun snapshot(specification: ProjectSpecification): StoredSpecificationSnapshot {
        val codec = JsonSpecificationSnapshotCodec()
        return (codec.decode(codec.encode(specification), specification.project.id) as SpecificationSnapshotLoadResult.Valid).snapshot
    }

    fun relationshipReport(count: Int): RelationshipProjectionReport {
        val policy = RelationshipProjectionPolicy()
        val logical = if (count == 0) emptyMap() else mapOf("CALLS" to count)
        val unsigned = RelationshipProjectionReport(
            policyId = policy.policyId,
            policySha256 = RelationshipProjectionIntegrity.policySha256(policy),
            logicalCountByKind = logical,
            emittedCountByKind = logical,
            omittedCountByKind = emptyMap(),
            aggregatedOccurrenceCountByKind = emptyMap(),
            overflowScopes = emptyList(),
            omittedIdentitySha256ByKind = emptyMap(),
            reportSha256 = "",
        )
        return unsigned.copy(reportSha256 = RelationshipProjectionIntegrity.reportSha256(unsigned))
    }

    fun descriptor(
        id: String,
        path: String,
        scopes: List<String>,
        dependencies: List<DocumentationArtifactId> = emptyList(),
    ) = DocumentationArtifactDescriptor(
        DocumentationArtifactId(id),
        path,
        "text/markdown",
        DocumentationArtifactKind.COMPONENT,
        scopes.sorted(),
        dependencies.sortedBy { it.value },
    )

    fun state(id: DocumentationArtifactId, path: String, content: String) = EvolutionArtifactState(
        id,
        path,
        "text/markdown",
        EvolutionCanonicalizer.sha256(content),
    )

    private fun evidence(id: String) = Evidence(
        id,
        "SOURCE",
        file = "src/Test.kt",
        summary = id,
        confidence = EvidenceConfidence.HIGH,
    )
}

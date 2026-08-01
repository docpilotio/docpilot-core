package io.docpilot.core.specification

import io.docpilot.core.api.DocumentationArtifactKind
import io.docpilot.core.documentation.profile.*
import io.docpilot.core.model.*
import io.docpilot.core.render.ContractDocumentationMarkdownRenderer
import kotlin.test.*

class ContractDocumentationRenderingTest {
    private val renderer = ContractDocumentationMarkdownRenderer()

    @Test
    fun `catalog and all nine role details are deterministic and canonical`() {
        val specification = fixture()
        val descriptors = renderer.describe(specification)
        assertEquals(1, descriptors.count { it.kind == DocumentationArtifactKind.CONTRACT_CATALOG })
        assertEquals(9, descriptors.count { it.kind == DocumentationArtifactKind.CONTRACT_DETAIL })
        val first = renderer.render(specification)
        val permuted = renderer.render(specification.copy(contracts = specification.contracts.reversed()))
        assertEquals(first, permuted)
        val catalog = first.single { it.relativePath == "docs/contracts/catalog.md" }.content
        ContractRole.entries.forEach { assertContains(catalog, "- ${it.name}: 1") }
        assertContains(catalog, "Unresolved references: 1")
        assertTrue(first.all { "C:\\" !in it.content })
    }

    @Test
    fun `DIR 05 profile resolves contract catalog and detail while old DIR defers`() {
        val resolver = DefaultDocumentationProfileResolver()
        fun resolve(specification: ProjectSpecification) = resolver.resolve(
            DocumentationProfileResolutionRequest(
                DocumentationProfileId("kotlin-android"), DocumentationProfileVersion(1), specification,
                renderer.describe(specification.copy(schemaVersion = "0.5")), renderer.capabilities(),
            ),
        )
        val current = resolve(fixture())
        assertEquals(DocumentPlanningStatus.READY, current.documents.single { it.type == DocumentType.CONTRACT_CATALOG }.status)
        assertEquals(9, current.documents.count { it.type == DocumentType.CONTRACT_DETAIL })
        val old = resolve(fixture().copy(schemaVersion = "0.4", contracts = emptyList()))
        assertEquals(DocumentPlanningStatus.DEFERRED, old.documents.single { it.type == DocumentType.CONTRACT_CATALOG }.status)
        assertEquals(DocumentPlanningStatus.DEFERRED, old.documents.single { it.type == DocumentType.CONTRACT_DETAIL }.status)
    }

    @Test
    fun `absolute Evidence path fails closed`() {
        val specification = fixture().let { it.copy(evidence = it.evidence.map { evidence -> evidence.copy(file = "C:\\source.kt") }) }
        assertFailsWith<IllegalArgumentException> { renderer.render(specification) }
    }

    private fun fixture(): ProjectSpecification {
        val evidence = Evidence("e:contract", "DECLARATION", "src/Contract.kt", "Contract", 1, 2, "Canonical declaration", EvidenceConfidence.HIGH)
        val unresolved = UnresolvedItem("u:type", "type:Missing", "Ambiguous canonical type", "Resolve in extraction")
        val owner = ContractOwner(ContractOwnerKind.COMPONENT, "component:owner")
        val kinds = listOf(ContractKind.API, ContractKind.API, ContractKind.DATA, ContractKind.DATA, ContractKind.MESSAGE, ContractKind.MESSAGE, ContractKind.NAVIGATION, ContractKind.PERSISTENCE, ContractKind.EXTERNAL)
        val contracts = ContractRole.entries.mapIndexed { index, role ->
            val id = "contract:${role.name.lowercase()}"
            val type = ContractTypeReference(
                if (index == 0) ContractTypeKind.UNRESOLVED else ContractTypeKind.EXTERNAL,
                displayName = if (index == 0) "List<Missing?>" else "String",
                nullable = index == 0,
                unresolvedRefs = if (index == 0) setOf(unresolved.id) else emptySet(),
            )
            ContractSpecification(
                id, role.name.lowercase(), role.name, kinds[index], role, owner, setOf(owner.stableId),
                inputs = listOf(ContractValue("$id:input", "input", "input", type, ContractDirection.INPUT, evidenceRefs = setOf(evidence.id), unresolvedRefs = type.unresolvedRefs)),
                outputs = emptyList(), members = emptyList(), relationships = emptyList(),
                evidenceRefs = setOf(evidence.id), unresolvedRefs = type.unresolvedRefs,
            )
        }.sortedBy { it.id }
        return ProjectSpecification(
            schemaVersion = "0.5", project = ProjectDescriptor("project:test", "Test"),
            modules = listOf(ModuleSpecification("module:app", "app")),
            components = listOf(ComponentSpecification(owner.stableId, "Owner", "module:app", "CLASS", "OWNER")),
            evidence = listOf(evidence), unresolved = listOf(unresolved), contracts = contracts,
        )
    }
}

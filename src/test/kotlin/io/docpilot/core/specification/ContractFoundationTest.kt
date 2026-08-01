package io.docpilot.core.specification

import io.docpilot.core.incremental.specification.DefaultSpecificationDiffer
import io.docpilot.core.incremental.specification.snapshot.*
import io.docpilot.core.model.*
import kotlin.test.*

class ContractFoundationTest {
    @Test
    fun `all nine product contract concepts are represented and snapshot 3 round trips`() {
        val specification = fixture()
        ProjectSpecificationValidator.validate(specification)
        assertEquals(ContractRole.entries.toSet(), specification.contracts.map { it.role }.toSet())

        val encoded = JsonSpecificationSnapshotCodec().encode(specification)
        assertContains(encoded, "\"snapshotFormatVersion\": 3")
        assertContains(encoded, "\"dirSchemaVersion\": \"0.5\"")
        val loaded = assertIs<SpecificationSnapshotLoadResult.Valid>(
            JsonSpecificationSnapshotCodec().decode(encoded, specification.project.id),
        )
        assertEquals(specification, loaded.snapshot.specification)
        assertEquals(encoded, JsonSpecificationSnapshotCodec().encode(loaded.snapshot.specification))
    }

    @Test
    fun `stable identity ignores display and source location but changes with semantic owner`() {
        val owner = ContractOwner(ContractOwnerKind.COMPONENT, "component:owner")
        val first = ContractIdentity.of(ContractKind.API, ContractRole.PUBLIC_API, owner, "cafe\u0301")
        val normalized = ContractIdentity.of(ContractKind.API, ContractRole.PUBLIC_API, owner, "caf\u00e9")
        assertEquals(first, normalized)
        assertNotEquals(first, ContractIdentity.of(ContractKind.API, ContractRole.PUBLIC_API, owner.copy(stableId = "component:other"), "caf\u00e9"))
        assertNotEquals(first, ContractIdentity.of(ContractKind.DATA, ContractRole.DATA_MODEL, owner, "caf\u00e9"))
    }

    @Test
    fun `evidence-free and low-confidence-only contracts fail closed`() {
        val valid = fixture()
        val noEvidence = valid.copy(contracts = valid.contracts.mapIndexed { index, contract ->
            if (index == 0) contract.copy(evidenceRefs = emptySet()) else contract
        })
        assertFailsWith<IllegalArgumentException> { ProjectSpecificationValidator.validate(noEvidence) }
        val low = valid.copy(evidence = valid.evidence.map { it.copy(confidence = EvidenceConfidence.LOW) })
        assertFailsWith<IllegalArgumentException> { ProjectSpecificationValidator.validate(low) }
    }

    @Test
    fun `DIR 04 migration creates snapshot 3 with empty contracts`() {
        val old = fixture().copy(schemaVersion = "0.4", contracts = emptyList())
        val migrated = SpecificationSnapshotMigration.migrateDir04To05(old)
        assertEquals("0.5", migrated.schemaVersion)
        assertTrue(migrated.contracts.isEmpty())
        ProjectSpecificationValidator.validate(migrated)
    }

    @Test
    fun `contract diff is stable-id ordered and distinguishes modification`() {
        val before = fixture()
        val changed = before.contracts.last().copy(displayName = "Renamed")
        val after = before.copy(contracts = before.contracts.dropLast(1) + changed)
        val diff = DefaultSpecificationDiffer().diff(before, after)
        assertEquals(listOf(changed.id), diff.contractChanges.map { it.id })
        assertTrue(diff.hasChanges)
    }

    @Test
    fun `semantic hash is independent of unordered source and evidence collections`() {
        val contract = fixture().contracts.first()
        val reversed = contract.copy(
            sourceEntityStableIds = contract.sourceEntityStableIds.reversed().toSet(),
            evidenceRefs = contract.evidenceRefs.reversed().toSet(),
        )
        assertEquals(ContractCanonicalizer.semanticHash(contract), ContractCanonicalizer.semanticHash(reversed))
        assertNotEquals(ContractCanonicalizer.semanticHash(contract), ContractCanonicalizer.semanticHash(contract.copy(displayName = "Changed")))
    }

    private fun fixture(): ProjectSpecification {
        val evidence = Evidence("e:contract", "DECLARATION", "src/Contract.kt", "Contract", 10, 11, "Explicit contract annotation", EvidenceConfidence.HIGH)
        val owner = ContractOwner(ContractOwnerKind.COMPONENT, "component:owner")
        val roleKinds = mapOf(
            ContractRole.PUBLIC_API to ContractKind.API,
            ContractRole.REPOSITORY_API to ContractKind.API,
            ContractRole.DATA_MODEL to ContractKind.DATA,
            ContractRole.DTO to ContractKind.DATA,
            ContractRole.EVENT to ContractKind.MESSAGE,
            ContractRole.CALLBACK to ContractKind.MESSAGE,
            ContractRole.NAVIGATION_ARGUMENT to ContractKind.NAVIGATION,
            ContractRole.PERSISTENCE_SCHEMA to ContractKind.PERSISTENCE,
            ContractRole.EXTERNAL_SERVICE_BOUNDARY to ContractKind.EXTERNAL,
        )
        val contracts = roleKinds.map { (role, kind) ->
            val key = role.name.lowercase()
            val id = ContractIdentity.of(kind, role, owner, key)
            val type = ContractTypeReference(ContractTypeKind.PRIMITIVE, displayName = "String")
            val value = ContractValue(
                ContractIdentity.nested(id, "input", "value"), "value", "value", type,
                ContractDirection.INPUT, evidenceRefs = setOf(evidence.id),
            )
            val output = ContractValue(
                ContractIdentity.nested(id, "output", "result"), "result", "result", type,
                ContractDirection.OUTPUT, evidenceRefs = setOf(evidence.id),
            )
            val member = ContractMember(
                ContractIdentity.nested(id, "member", "value"), "value", "value", type,
                evidenceRefs = setOf(evidence.id),
            )
            ContractSpecification(
                id, key, role.name, kind, role, owner, setOf(owner.stableId),
                inputs = if (role in setOf(ContractRole.PUBLIC_API, ContractRole.REPOSITORY_API, ContractRole.CALLBACK, ContractRole.EXTERNAL_SERVICE_BOUNDARY)) listOf(value) else emptyList(),
                outputs = if (role in setOf(ContractRole.PUBLIC_API, ContractRole.REPOSITORY_API, ContractRole.EXTERNAL_SERVICE_BOUNDARY)) listOf(output) else emptyList(),
                members = if (role in setOf(ContractRole.DATA_MODEL, ContractRole.DTO, ContractRole.EVENT, ContractRole.NAVIGATION_ARGUMENT, ContractRole.PERSISTENCE_SCHEMA)) listOf(member) else emptyList(),
                evidenceRefs = setOf(evidence.id),
            )
        }.sortedBy { it.id }
        return ProjectSpecification(
            schemaVersion = "0.5",
            project = ProjectDescriptor("project:test", "Test"),
            modules = listOf(ModuleSpecification("module:app", "app")),
            components = listOf(ComponentSpecification("component:owner", "Owner", "module:app", "CLASS", "OWNER")),
            evidence = listOf(evidence),
            contracts = contracts,
        )
    }
}

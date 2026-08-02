package io.docpilot.core.specification.claim

import io.docpilot.core.model.ComponentSpecification
import io.docpilot.core.model.ContractKind
import io.docpilot.core.model.ContractOwner
import io.docpilot.core.model.ContractOwnerKind
import io.docpilot.core.model.ContractRole
import io.docpilot.core.model.ContractSpecification
import io.docpilot.core.model.Evidence
import io.docpilot.core.model.EvidenceConfidence
import io.docpilot.core.model.ModuleSpecification
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.UnresolvedItem
import io.docpilot.core.specification.ContractIdentity

internal object ClaimFixtures {
    val highEvidence = Evidence("evidence:high", "DECLARATION", "src/Sample.kt", "Sample", 1, 2, "High confidence evidence", EvidenceConfidence.HIGH)
    val lowEvidence = Evidence("evidence:low", "DECLARATION", "src/Sample.kt", "Sample", 3, 4, "Low confidence evidence", EvidenceConfidence.LOW)
    val contractOwner = ContractOwner(ContractOwnerKind.COMPONENT, "component:sample")
    val contract: ContractSpecification = ContractSpecification(
        id = ContractIdentity.of(ContractKind.API, ContractRole.PUBLIC_API, contractOwner, "sample"),
        semanticKey = "sample",
        displayName = "Sample Contract",
        kind = ContractKind.API,
        role = ContractRole.PUBLIC_API,
        owner = contractOwner,
        sourceEntityStableIds = setOf("component:sample"),
        evidenceRefs = setOf(highEvidence.id),
    )
    val unresolvedItem = UnresolvedItem("unresolved:missing-target", "component:sample", "What is the missing dependency?")

    fun specification(): ProjectSpecification = ProjectSpecification(
        schemaVersion = "0.5",
        project = ProjectDescriptor("project:sample", "Sample"),
        modules = listOf(ModuleSpecification("module:app", "app")),
        components = listOf(ComponentSpecification("component:sample", "Sample", "module:app", "CLASS", "OWNER")),
        evidence = listOf(highEvidence, lowEvidence),
        contracts = listOf(contract),
        unresolved = listOf(unresolvedItem),
    )
}

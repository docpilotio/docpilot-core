package io.docpilot.core.documentation.profile

import io.docpilot.core.reconciliation.DocumentationOwnership

public object KotlinAndroidDocumentationProfile {
    public val profile: DocumentationProfile = DocumentationProfileValidator.validate(
        DocumentationProfile(
            id = DocumentationProfileId("kotlin-android"),
            version = DocumentationProfileVersion(1),
            displayName = "Kotlin Android Documentation",
            supportedProjectKinds = setOf(DocumentationProjectKind.KOTLIN_ANDROID),
            documentDefinitions = listOf(
                projectOverview(),
                featureCatalog(),
                architectureOverview(),
                moduleArchitecture(),
                featureSpecification(),
                domainModel(),
                databaseSchema(),
                externalApiContract(),
                contractCatalog(),
                contractDetail(),
                testStrategy(),
            ),
        ),
    )

    private fun projectOverview(): DocumentDefinition = definition(
        type = DocumentType.PROJECT_OVERVIEW,
        stableKey = "project-overview",
        purpose = "Explain the project purpose, supported platforms, build boundaries, and navigation for new contributors.",
        primary = setOf(DocumentAudience.NEW_DEVELOPER, DocumentAudience.REVIEWER),
        secondary = setOf(DocumentAudience.MAINTAINER, DocumentAudience.AI_DOCUMENTATION_REVIEWER),
        path = "project/project-overview.md",
        sections = listOf(
            section("overview", "Overview", 10),
            section(
                "purpose", "Purpose", 20,
                evidence = SectionEvidenceRequirement(
                    minimumEvidenceCount = 1,
                    allowedEvidenceClasses = EvidenceClass.entries.toSet(),
                    subject = EvidenceSubject.PROJECT_PURPOSE,
                ),
            ),
            section("platforms", "Platforms and Build Systems", 30),
            section("modules", "Modules", 40),
            section("evidence", "Evidence", 50, capability = RendererCapability.EVIDENCE_REFERENCE_RENDERING),
            section("unknowns", "Unknowns", 60, capability = RendererCapability.UNKNOWN_FINDING_RENDERING),
        ),
    )

    private fun featureCatalog(): DocumentDefinition = definition(
        type = DocumentType.FEATURE_CATALOG,
        stableKey = "feature-catalog",
        purpose = "Provide the canonical inventory of product features and their owning implementation boundaries.",
        primary = setOf(DocumentAudience.DEVELOPER, DocumentAudience.ARCHITECT, DocumentAudience.REVIEWER),
        path = "project/feature-catalog.md",
        requiredModel = DocumentationModelRequirement.FEATURE_MODEL,
        sections = listOf(
            section("overview", "Overview", 10),
            section(
                "features",
                "Features",
                20,
                capability = RendererCapability.FEATURE_DOCUMENTATION_RENDERING,
                missing = MissingDataBehavior.DEFER_DOCUMENT,
            ),
            section("ownership", "Feature Ownership", 30, missing = MissingDataBehavior.DEFER_DOCUMENT),
            section("evidence", "Evidence", 40, capability = RendererCapability.EVIDENCE_REFERENCE_RENDERING),
            section("unknowns", "Unknowns", 50, capability = RendererCapability.UNKNOWN_FINDING_RENDERING),
        ),
    )

    private fun architectureOverview(): DocumentDefinition = definition(
        type = DocumentType.ARCHITECTURE_OVERVIEW,
        stableKey = "architecture-overview",
        purpose = "Explain system context, module boundaries, dependency direction, external boundaries, and known limitations.",
        primary = setOf(DocumentAudience.ARCHITECT, DocumentAudience.NEW_DEVELOPER, DocumentAudience.REVIEWER),
        secondary = setOf(DocumentAudience.MAINTAINER),
        path = "architecture/architecture-overview.md",
        sections = listOf(
            section(
                "evidence-basis", "Evidence Basis", 10,
                evidence = SectionEvidenceRequirement(1, setOf(EvidenceClass.VERIFIED, EvidenceClass.CORE_DERIVED), EvidenceSubject.ARCHITECTURE),
            ),
            section("system-context", "System Context", 20),
            section("module-boundaries", "Module Boundaries", 30),
            section("dependency-direction", "Dependency Direction", 40),
            section("external-boundaries", "External Boundaries", 50),
            section("risks-and-unknowns", "Risks, Constraints, and Unknowns", 60, capability = RendererCapability.UNKNOWN_FINDING_RENDERING),
            optionalSection(
                "module-dependency-diagram", "Module Dependency Diagram", 70,
                RendererCapability.MODULE_DEPENDENCY_DIAGRAM,
            ),
            section("evidence", "Evidence", 80, capability = RendererCapability.EVIDENCE_REFERENCE_RENDERING),
        ),
    )

    private fun moduleArchitecture(): DocumentDefinition = definition(
        type = DocumentType.MODULE_ARCHITECTURE,
        stableKey = "module-architecture",
        purpose = "Describe module responsibilities, package composition, component ownership, and dependency boundaries.",
        primary = setOf(DocumentAudience.DEVELOPER, DocumentAudience.ARCHITECT, DocumentAudience.MAINTAINER),
        path = "architecture/module-architecture.md",
        sections = listOf(
            section("overview", "Overview", 10),
            section(
                "module-inventory", "Module Inventory", 20,
                evidence = SectionEvidenceRequirement(1, setOf(EvidenceClass.VERIFIED, EvidenceClass.CORE_DERIVED), EvidenceSubject.MODULE),
            ),
            section("package-and-component-boundaries", "Package and Component Boundaries", 30),
            section("dependencies", "Dependencies", 40),
            section("evidence", "Evidence", 50, capability = RendererCapability.EVIDENCE_REFERENCE_RENDERING),
            section("unknowns", "Unknowns", 60, capability = RendererCapability.UNKNOWN_FINDING_RENDERING),
        ),
        dependencies = setOf(DocumentDependencyRule(DocumentStableKey("architecture-overview"))),
    )

    private fun featureSpecification(): DocumentDefinition = DocumentDefinition(
        type = DocumentType.FEATURE_SPECIFICATION,
        stableKey = DocumentStableKey("feature-specification"),
        purpose = "Describe one feature's purpose, entry points, participants, primary scenarios, Evidence, and unresolved behavior.",
        primaryAudiences = setOf(DocumentAudience.DEVELOPER, DocumentAudience.REVIEWER, DocumentAudience.TEST_ENGINEER),
        secondaryAudiences = setOf(DocumentAudience.AI_DOCUMENTATION_REVIEWER),
        multiplicity = DocumentMultiplicity.PER_FEATURE,
        pathPolicy = DocumentPathPolicy.Pattern("features/{featureId}-{slug}.md"),
        sections = listOf(
            section("overview", "Overview", 10, missing = MissingDataBehavior.DEFER_DOCUMENT),
            section("purpose", "Purpose", 20, missing = MissingDataBehavior.DEFER_DOCUMENT),
            section("entry-points", "Entry Points", 30, missing = MissingDataBehavior.DEFER_DOCUMENT),
            section("participants", "Participants", 40, missing = MissingDataBehavior.DEFER_DOCUMENT),
            section("primary-scenarios", "Primary Scenarios", 50, missing = MissingDataBehavior.DEFER_DOCUMENT),
            section("evidence", "Evidence", 60, capability = RendererCapability.EVIDENCE_REFERENCE_RENDERING),
            section("unknowns", "Unknowns", 70, capability = RendererCapability.UNKNOWN_FINDING_RENDERING),
            optionalSection("class-diagram", "Class Diagram", 80, RendererCapability.FEATURE_CLASS_DIAGRAM),
            optionalSection("sequence-diagram", "Sequence Diagram", 90, RendererCapability.SEQUENCE_DIAGRAM),
        ),
        rendererCapabilities = setOf(
            RendererCapability.MARKDOWN_SECTION_RENDERING,
            RendererCapability.EVIDENCE_REFERENCE_RENDERING,
            RendererCapability.UNKNOWN_FINDING_RENDERING,
            RendererCapability.FEATURE_DOCUMENTATION_RENDERING,
        ),
        requiredModel = DocumentationModelRequirement.FEATURE_MODEL,
    )

    private fun domainModel(): DocumentDefinition = definition(
        type = DocumentType.DOMAIN_MODEL,
        stableKey = "domain-model",
        purpose = "Describe canonical domain concepts, constraints, relationships, and ownership boundaries.",
        primary = setOf(DocumentAudience.DEVELOPER, DocumentAudience.ARCHITECT, DocumentAudience.REVIEWER),
        path = "contracts/domain-model.md",
        requiredModel = DocumentationModelRequirement.CONTRACT_MODEL,
        sections = contractSections("Domain Concepts"),
    )

    private fun databaseSchema(): DocumentDefinition = definition(
        type = DocumentType.DATABASE_SCHEMA,
        stableKey = "database-schema",
        purpose = "Describe persisted entities, fields, keys, relationships, migrations, and unsupported schema claims.",
        primary = setOf(DocumentAudience.DEVELOPER, DocumentAudience.REVIEWER, DocumentAudience.TEST_ENGINEER),
        path = "contracts/database-schema.md",
        requiredModel = DocumentationModelRequirement.CONTRACT_MODEL,
        sections = contractSections("Schema Inventory"),
    )

    private fun externalApiContract(): DocumentDefinition = definition(
        type = DocumentType.EXTERNAL_API_CONTRACT,
        stableKey = "external-api-contract",
        purpose = "Describe verified external API boundaries, request and response contracts, failure behavior, and Evidence.",
        primary = setOf(DocumentAudience.DEVELOPER, DocumentAudience.ARCHITECT, DocumentAudience.REVIEWER),
        path = "contracts/external-apis.md",
        requiredModel = DocumentationModelRequirement.CONTRACT_MODEL,
        sections = contractSections("External APIs"),
    )

    private fun testStrategy(): DocumentDefinition = definition(
        type = DocumentType.TEST_STRATEGY,
        stableKey = "test-strategy",
        purpose = "Explain observed test boundaries, test levels, execution evidence, coverage gaps, and unresolved quality risks.",
        primary = setOf(DocumentAudience.TEST_ENGINEER, DocumentAudience.DEVELOPER, DocumentAudience.REVIEWER),
        secondary = setOf(DocumentAudience.MAINTAINER),
        path = "quality/test-strategy.md",
        sections = listOf(
            section("overview", "Overview", 10),
            section(
                "observed-test-boundaries", "Observed Test Boundaries", 20,
                evidence = SectionEvidenceRequirement(1, setOf(EvidenceClass.VERIFIED, EvidenceClass.CORE_DERIVED), EvidenceSubject.TEST),
            ),
            section("test-levels", "Test Levels", 30),
            section("execution", "Execution", 40),
            section("coverage-gaps", "Coverage Gaps", 50, capability = RendererCapability.UNKNOWN_FINDING_RENDERING),
            section("evidence", "Evidence", 60, capability = RendererCapability.EVIDENCE_REFERENCE_RENDERING),
        ),
    )

    private fun contractCatalog(): DocumentDefinition = definition(
        type = DocumentType.CONTRACT_CATALOG,
        stableKey = "contract-catalog",
        purpose = "Inventory canonical DIR 0.5 Contracts and link their deterministic detail documents.",
        primary = setOf(DocumentAudience.DEVELOPER, DocumentAudience.ARCHITECT, DocumentAudience.REVIEWER),
        path = "contracts/catalog.md",
        requiredModel = DocumentationModelRequirement.CONTRACT_MODEL,
        sections = listOf(
            section("identity", "Generation Identity", 10),
            section("summary", "Contract Summary", 20),
            section("contracts", "Contracts", 30, capability = RendererCapability.CONTRACT_DOCUMENTATION_RENDERING),
            section("evidence", "Evidence Policy", 40, capability = RendererCapability.EVIDENCE_REFERENCE_RENDERING),
            section("unresolved", "Unresolved Policy", 50, capability = RendererCapability.UNKNOWN_FINDING_RENDERING),
        ),
    ).copy(rendererCapabilities = setOf(
        RendererCapability.MARKDOWN_SECTION_RENDERING,
        RendererCapability.EVIDENCE_REFERENCE_RENDERING,
        RendererCapability.UNKNOWN_FINDING_RENDERING,
        RendererCapability.CONTRACT_DOCUMENTATION_RENDERING,
    ))

    private fun contractDetail(): DocumentDefinition = DocumentDefinition(
        type = DocumentType.CONTRACT_DETAIL,
        stableKey = DocumentStableKey("contract-detail"),
        purpose = "Render one canonical DIR 0.5 Contract without source rescanning or inferred facts.",
        primaryAudiences = setOf(DocumentAudience.DEVELOPER, DocumentAudience.REVIEWER),
        multiplicity = DocumentMultiplicity.PER_CONTRACT,
        pathPolicy = DocumentPathPolicy.Pattern("contracts/details/{contractId}.md"),
        sections = listOf(
            "identity", "classification", "ownership", "source-bindings", "inputs", "outputs",
            "members", "relationships", "evidence", "unresolved", "generation-metadata",
        ).mapIndexed { index, id -> section(id, id.replace('-', ' ').replaceFirstChar(Char::uppercase), (index + 1) * 10) },
        rendererCapabilities = setOf(
            RendererCapability.MARKDOWN_SECTION_RENDERING,
            RendererCapability.EVIDENCE_REFERENCE_RENDERING,
            RendererCapability.UNKNOWN_FINDING_RENDERING,
            RendererCapability.CONTRACT_DOCUMENTATION_RENDERING,
        ),
        requiredModel = DocumentationModelRequirement.CONTRACT_MODEL,
    )

    private fun contractSections(inventoryTitle: String): List<SectionDefinition> = listOf(
        section("overview", "Overview", 10, missing = MissingDataBehavior.DEFER_DOCUMENT),
        section("inventory", inventoryTitle, 20, missing = MissingDataBehavior.DEFER_DOCUMENT),
        section("relationships", "Relationships", 30, missing = MissingDataBehavior.DEFER_DOCUMENT),
        section("constraints", "Constraints", 40, missing = MissingDataBehavior.DEFER_DOCUMENT),
        section("evidence", "Evidence", 50, capability = RendererCapability.EVIDENCE_REFERENCE_RENDERING),
        section("unknowns", "Unknowns", 60, capability = RendererCapability.UNKNOWN_FINDING_RENDERING),
    )

    private fun definition(
        type: DocumentType,
        stableKey: String,
        purpose: String,
        primary: Set<DocumentAudience>,
        secondary: Set<DocumentAudience> = emptySet(),
        path: String,
        sections: List<SectionDefinition>,
        requiredModel: DocumentationModelRequirement = DocumentationModelRequirement.NONE,
        dependencies: Set<DocumentDependencyRule> = emptySet(),
    ): DocumentDefinition = DocumentDefinition(
        type = type,
        stableKey = DocumentStableKey(stableKey),
        purpose = purpose,
        primaryAudiences = primary,
        secondaryAudiences = secondary,
        multiplicity = DocumentMultiplicity.SINGLE,
        pathPolicy = DocumentPathPolicy.Fixed(path),
        sections = sections,
        rendererCapabilities = setOf(
            RendererCapability.MARKDOWN_SECTION_RENDERING,
            RendererCapability.EVIDENCE_REFERENCE_RENDERING,
            RendererCapability.UNKNOWN_FINDING_RENDERING,
        ),
        completenessPolicy = CompletenessPolicy.ALLOW_EXPLICIT_PARTIAL,
        ownershipPolicy = DocumentOwnershipPolicy(
            DocumentationOwnership.DOCPILOT_OWNED,
            OwnershipConflictBehavior.BLOCK,
        ),
        dependencyRules = dependencies,
        requiredModel = requiredModel,
    )

    private fun section(
        id: String,
        title: String,
        order: Int,
        evidence: SectionEvidenceRequirement = SectionEvidenceRequirement(),
        capability: RendererCapability? = null,
        missing: MissingDataBehavior = MissingDataBehavior.MARK_UNKNOWN,
    ): SectionDefinition = SectionDefinition(
        id = SectionId(id),
        title = title,
        order = order,
        required = true,
        evidenceRequirement = evidence,
        requiredCapabilities = capability?.let(::setOf).orEmpty(),
        missingDataBehavior = missing,
    )

    private fun optionalSection(
        id: String,
        title: String,
        order: Int,
        capability: RendererCapability,
    ): SectionDefinition = SectionDefinition(
        id = SectionId(id),
        title = title,
        order = order,
        required = false,
        requiredCapabilities = setOf(capability),
        missingDataBehavior = MissingDataBehavior.OMIT_OPTIONAL_SECTION,
    )
}

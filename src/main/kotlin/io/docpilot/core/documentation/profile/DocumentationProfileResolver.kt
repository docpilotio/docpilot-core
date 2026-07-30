package io.docpilot.core.documentation.profile

import io.docpilot.core.api.DocumentationArtifactDescriptor
import io.docpilot.core.model.Evidence
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.reconciliation.DocumentationOwnership
import io.docpilot.core.reconciliation.DocumentationOwnershipManifest

public data class DocumentationProfileResolutionRequest(
    public val profileId: DocumentationProfileId,
    public val profileVersion: DocumentationProfileVersion,
    public val specification: ProjectSpecification,
    public val artifactCatalog: List<DocumentationArtifactDescriptor> = emptyList(),
    public val rendererCapabilities: Set<RendererCapability> = emptySet(),
    public val ownershipManifests: List<DocumentationOwnershipManifest> = emptyList(),
)

public fun interface DocumentationProfileResolver {
    public fun resolve(request: DocumentationProfileResolutionRequest): DocumentationProfileResolution
}

public class DefaultDocumentationProfileResolver(
    private val registry: DocumentationProfileRegistry = BuiltInDocumentationProfiles,
) : DocumentationProfileResolver {
    override fun resolve(request: DocumentationProfileResolutionRequest): DocumentationProfileResolution {
        val profile = registry.resolve(request.profileId, request.profileVersion)
        val profileSha = DocumentationProfileIntegrity.profileSha256(profile)
        val capabilities = request.rendererCapabilities.sortedBy { it.name }.toCollection(linkedSetOf())
        val manifestsByPath = request.ownershipManifests.associateBy {
            DocumentationProfileCanonicalizer.normalizeRelativePath(it.relativePath)
        }
        require(manifestsByPath.size == request.ownershipManifests.size) {
            "Duplicate Ownership Manifest path."
        }

        var documents = profile.documentDefinitions.flatMap { definition ->
            resolveDefinition(profile, definition, request.specification, capabilities, request.artifactCatalog, manifestsByPath)
        }.sortedBy { it.documentStableId }
        documents = applyResolvedPathConflicts(documents)

        val bindings = ProfileArtifactCompatibility.bind(documents, request.artifactCatalog)
        val bindingFindings = bindings.filter { it.status == ProfileArtifactBindingStatus.UNBOUND }.map { binding ->
            DocumentPlanningFinding(
                kind = DocumentPlanningFindingKind.UNBOUND_LEGACY_ARTIFACT,
                documentStableId = binding.documentStableId,
                subjectId = binding.documentStableId,
                message = binding.message,
                blocking = false,
            )
        }
        val findings = (documents.flatMap { it.findings } + bindingFindings).sortedWith(findingComparator)
        val provisional = DocumentationProfileResolution(
            profileId = profile.id,
            profileVersion = profile.version,
            profileSemanticSha256 = profileSha,
            documents = documents,
            artifactBindings = bindings,
            findings = findings,
            resolutionSha256 = "",
        )
        return provisional.copy(resolutionSha256 = DocumentationProfileIntegrity.resolutionSha256(provisional))
    }

    private fun resolveDefinition(
        profile: DocumentationProfile,
        definition: DocumentDefinition,
        specification: ProjectSpecification,
        capabilities: Set<RendererCapability>,
        catalog: List<DocumentationArtifactDescriptor>,
        manifestsByPath: Map<String, DocumentationOwnershipManifest>,
    ): List<ResolvedDocumentContract> {
        val missingModelFinding = missingModelFinding(profile, definition, specification)
        val scopes = scopes(definition.multiplicity, specification)
        if (scopes.isEmpty()) {
            val kind = when (definition.multiplicity) {
                DocumentMultiplicity.PER_FEATURE -> DocumentPlanningFindingKind.MISSING_FEATURE_MODEL
                else -> DocumentPlanningFindingKind.MISSING_SPECIFICATION_ELEMENT
            }
            val finding = missingModelFinding ?: DocumentPlanningFinding(
                kind = kind,
                documentStableId = documentStableId(profile, definition, "deferred"),
                subjectId = definition.stableKey.value,
                message = "No ${definition.multiplicity.name} source elements are available in DIR ${specification.schemaVersion}.",
                blocking = false,
            )
            return listOf(
                deferredDocument(profile, definition, capabilities, finding),
            )
        }
        return scopes.map { scope ->
            resolveDocument(
                profile,
                definition,
                scope,
                specification,
                capabilities,
                catalog,
                manifestsByPath,
                missingModelFinding,
            )
        }
    }

    private fun resolveDocument(
        profile: DocumentationProfile,
        definition: DocumentDefinition,
        scope: Scope,
        specification: ProjectSpecification,
        capabilities: Set<RendererCapability>,
        catalog: List<DocumentationArtifactDescriptor>,
        manifestsByPath: Map<String, DocumentationOwnershipManifest>,
        missingModelFinding: DocumentPlanningFinding?,
    ): ResolvedDocumentContract {
        val stableId = documentStableId(profile, definition, scope.id)
        val path = resolvePath(definition.pathPolicy, scope)
        val findings = mutableListOf<DocumentPlanningFinding>()
        val evidence = evidenceFor(scope, specification)
        val sections = if (missingModelFinding != null) {
            findings += missingModelFinding.copy(documentStableId = stableId, subjectId = scope.id)
            definition.sections.map {
                ResolvedSectionContract(
                    sectionStableId = sectionStableId(profile, definition, it),
                    sectionId = it.id,
                    title = it.title,
                    required = it.required,
                    status = ResolvedSectionStatus.DEFERRED,
                    evidenceRefs = emptyList(),
                    missingEvidenceCount = it.evidenceRequirement.minimumEvidenceCount,
                    missingCapabilities = emptyList(),
                )
            }
        } else {
            definition.sections.map { section ->
                resolveSection(profile, definition, stableId, section, evidence, capabilities, findings)
            }
        }

        val missingDocumentCapabilities = definition.rendererCapabilities.minus(capabilities).sortedBy { it.name }
        if (missingDocumentCapabilities.isNotEmpty()) {
            findings += DocumentPlanningFinding(
                DocumentPlanningFindingKind.MISSING_RENDERER_CAPABILITY,
                stableId,
                definition.stableKey.value,
                "Missing document Renderer capabilities: ${missingDocumentCapabilities.joinToString { it.name }}.",
                blocking = true,
            )
        }
        addOwnershipFindings(stableId, path, catalog, manifestsByPath, findings)
        val resolvedOwnership = resolveOwnership(path, definition, catalog, manifestsByPath)
        val status = status(definition, sections, findings, missingModelFinding != null)
        val provisional = ResolvedDocumentContract(
            definitionStableId = definitionStableId(profile, definition),
            documentStableId = stableId,
            type = definition.type,
            stableKey = definition.stableKey,
            sourceElementIds = scope.sourceElementIds.sorted(),
            relativePath = path,
            sections = sections.sortedBy { section -> definition.sections.single { it.id == section.sectionId }.order },
            requiredRendererCapabilities = (
                definition.rendererCapabilities + definition.requiredSections.flatMap { it.requiredCapabilities }
                ).distinct().sortedBy { it.name },
            availableRendererCapabilities = capabilities.sortedBy { it.name },
            ownershipPolicy = definition.ownershipPolicy,
            resolvedOwnership = resolvedOwnership,
            dependencyRules = definition.dependencyRules.sortedWith(
                compareBy({ it.documentStableKey.value }, { it.required }),
            ),
            status = status,
            findings = findings.sortedWith(findingComparator),
            semanticSha256 = "",
        )
        return provisional.copy(semanticSha256 = DocumentationProfileIntegrity.documentSha256(provisional))
    }

    private fun deferredDocument(
        profile: DocumentationProfile,
        definition: DocumentDefinition,
        capabilities: Set<RendererCapability>,
        finding: DocumentPlanningFinding,
    ): ResolvedDocumentContract {
        val stableId = documentStableId(profile, definition, "deferred")
        val path = (definition.pathPolicy as? DocumentPathPolicy.Fixed)?.relativePath?.let(
            DocumentationProfileCanonicalizer::normalizeRelativePath,
        )
        val resolvedFinding = finding.copy(documentStableId = stableId)
        val provisional = ResolvedDocumentContract(
            definitionStableId = definitionStableId(profile, definition),
            documentStableId = stableId,
            type = definition.type,
            stableKey = definition.stableKey,
            sourceElementIds = emptyList(),
            relativePath = path,
            sections = definition.sections.map {
                ResolvedSectionContract(
                    sectionStableId = sectionStableId(profile, definition, it),
                    sectionId = it.id,
                    title = it.title,
                    required = it.required,
                    status = ResolvedSectionStatus.DEFERRED,
                    evidenceRefs = emptyList(),
                    missingEvidenceCount = it.evidenceRequirement.minimumEvidenceCount,
                    missingCapabilities = emptyList(),
                )
            },
            requiredRendererCapabilities = (
                definition.rendererCapabilities + definition.requiredSections.flatMap { it.requiredCapabilities }
                ).distinct().sortedBy { it.name },
            availableRendererCapabilities = capabilities.sortedBy { it.name },
            ownershipPolicy = definition.ownershipPolicy,
            resolvedOwnership = definition.ownershipPolicy.defaultOwnership,
            dependencyRules = definition.dependencyRules.sortedWith(
                compareBy({ it.documentStableKey.value }, { it.required }),
            ),
            status = DocumentPlanningStatus.DEFERRED,
            findings = listOf(resolvedFinding),
            semanticSha256 = "",
        )
        return provisional.copy(semanticSha256 = DocumentationProfileIntegrity.documentSha256(provisional))
    }

    private fun resolveSection(
        profile: DocumentationProfile,
        definition: DocumentDefinition,
        documentStableId: String,
        section: SectionDefinition,
        evidence: List<Evidence>,
        capabilities: Set<RendererCapability>,
        findings: MutableList<DocumentPlanningFinding>,
    ): ResolvedSectionContract {
        val missingCapabilities = section.requiredCapabilities.minus(capabilities).sortedBy { it.name }
        val matchingEvidence = evidence.filter { evidenceMatches(it, section.evidenceRequirement) }
            .sortedBy { it.id }
        val missingEvidenceCount = (section.evidenceRequirement.minimumEvidenceCount - matchingEvidence.size).coerceAtLeast(0)
        val status = when {
            missingCapabilities.isNotEmpty() && section.required -> ResolvedSectionStatus.UNSUPPORTED
            missingCapabilities.isNotEmpty() -> ResolvedSectionStatus.OMITTED
            missingEvidenceCount == 0 -> ResolvedSectionStatus.AVAILABLE
            section.missingDataBehavior == MissingDataBehavior.BLOCK_DOCUMENT -> ResolvedSectionStatus.BLOCKED
            section.missingDataBehavior == MissingDataBehavior.DEFER_DOCUMENT -> ResolvedSectionStatus.DEFERRED
            section.missingDataBehavior == MissingDataBehavior.OMIT_OPTIONAL_SECTION -> ResolvedSectionStatus.OMITTED
            else -> ResolvedSectionStatus.UNKNOWN
        }
        if (missingCapabilities.isNotEmpty()) {
            findings += DocumentPlanningFinding(
                DocumentPlanningFindingKind.MISSING_RENDERER_CAPABILITY,
                documentStableId,
                section.id.value,
                "Section requires unsupported Renderer capabilities: ${missingCapabilities.joinToString { it.name }}.",
                blocking = section.required,
            )
        }
        if (missingEvidenceCount > 0) {
            findings += DocumentPlanningFinding(
                DocumentPlanningFindingKind.MISSING_REQUIRED_EVIDENCE,
                documentStableId,
                section.id.value,
                "Section requires $missingEvidenceCount additional ${section.evidenceRequirement.subject.name} Evidence item(s).",
                blocking = status == ResolvedSectionStatus.BLOCKED,
            )
        }
        return ResolvedSectionContract(
            sectionStableId = sectionStableId(profile, definition, section),
            sectionId = section.id,
            title = section.title,
            required = section.required,
            status = status,
            evidenceRefs = matchingEvidence.map { it.id },
            missingEvidenceCount = missingEvidenceCount,
            missingCapabilities = missingCapabilities,
        )
    }

    private fun status(
        definition: DocumentDefinition,
        sections: List<ResolvedSectionContract>,
        findings: List<DocumentPlanningFinding>,
        missingModel: Boolean,
    ): DocumentPlanningStatus = when {
        findings.any { it.kind == DocumentPlanningFindingKind.OWNERSHIP_CONFLICT || it.kind == DocumentPlanningFindingKind.PATH_CONFLICT } ->
            DocumentPlanningStatus.BLOCKED
        missingModel || sections.any { it.status == ResolvedSectionStatus.DEFERRED && it.required } ->
            DocumentPlanningStatus.DEFERRED
        findings.any { it.kind == DocumentPlanningFindingKind.MISSING_RENDERER_CAPABILITY && it.blocking } ||
            sections.any { it.status == ResolvedSectionStatus.UNSUPPORTED && it.required } -> DocumentPlanningStatus.UNSUPPORTED
        sections.any { it.status == ResolvedSectionStatus.BLOCKED && it.required } -> DocumentPlanningStatus.BLOCKED
        definition.completenessPolicy == CompletenessPolicy.REQUIRE_ALL_REQUIRED_SECTIONS &&
            sections.any { it.required && it.status != ResolvedSectionStatus.AVAILABLE } -> DocumentPlanningStatus.BLOCKED
        findings.any { it.kind == DocumentPlanningFindingKind.RECONCILIATION_REQUIRED } ||
            sections.any { it.required && it.status != ResolvedSectionStatus.AVAILABLE } -> DocumentPlanningStatus.PARTIAL
        else -> DocumentPlanningStatus.READY
    }

    private fun addOwnershipFindings(
        stableId: String,
        path: String,
        catalog: List<DocumentationArtifactDescriptor>,
        manifestsByPath: Map<String, DocumentationOwnershipManifest>,
        findings: MutableList<DocumentPlanningFinding>,
    ) {
        val normalizedPath = DocumentationProfileCanonicalizer.normalizeRelativePath(path)
        val catalogCollision = catalog.any {
            DocumentationProfileCanonicalizer.normalizeRelativePath(it.relativePath) == normalizedPath
        }
        val manifest = manifestsByPath[normalizedPath]
        if (!catalogCollision && manifest == null) return
        when (manifest?.ownership ?: DocumentationOwnership.UNKNOWN) {
            DocumentationOwnership.DOCPILOT_OWNED -> Unit
            DocumentationOwnership.SHARED_MANAGED -> findings += DocumentPlanningFinding(
                DocumentPlanningFindingKind.RECONCILIATION_REQUIRED,
                stableId,
                path,
                "Profile path is SHARED_MANAGED and requires RFC-0055 reconciliation.",
                blocking = false,
            )
            DocumentationOwnership.USER_OWNED,
            DocumentationOwnership.UNKNOWN,
            DocumentationOwnership.CONFLICTED,
            -> findings += DocumentPlanningFinding(
                DocumentPlanningFindingKind.OWNERSHIP_CONFLICT,
                stableId,
                path,
                "Profile path ownership does not authorize generator overwrite: ${manifest?.ownership ?: DocumentationOwnership.UNKNOWN}.",
                blocking = true,
            )
        }
    }

    private fun resolveOwnership(
        path: String,
        definition: DocumentDefinition,
        catalog: List<DocumentationArtifactDescriptor>,
        manifestsByPath: Map<String, DocumentationOwnershipManifest>,
    ): DocumentationOwnership {
        val normalizedPath = DocumentationProfileCanonicalizer.normalizeRelativePath(path)
        manifestsByPath[normalizedPath]?.let { return it.ownership }
        return if (catalog.any {
                DocumentationProfileCanonicalizer.normalizeRelativePath(it.relativePath) == normalizedPath
            }
        ) {
            DocumentationOwnership.UNKNOWN
        } else {
            definition.ownershipPolicy.defaultOwnership
        }
    }

    private fun applyResolvedPathConflicts(
        documents: List<ResolvedDocumentContract>,
    ): List<ResolvedDocumentContract> {
        val conflicts = documents.filter { it.relativePath != null }.groupBy { it.relativePath }.filterValues { it.size > 1 }
        if (conflicts.isEmpty()) return documents
        return documents.map { document ->
            val peers = document.relativePath?.let(conflicts::get).orEmpty()
            if (peers.isEmpty()) return@map document
            val finding = DocumentPlanningFinding(
                DocumentPlanningFindingKind.PATH_CONFLICT,
                document.documentStableId,
                document.relativePath.orEmpty(),
                "Resolved Profile path is owned by multiple documents: ${peers.map { it.documentStableId }.sorted().joinToString()}.",
                blocking = true,
            )
            val changed = document.copy(
                status = DocumentPlanningStatus.BLOCKED,
                findings = (document.findings + finding).sortedWith(findingComparator),
                semanticSha256 = "",
            )
            changed.copy(semanticSha256 = DocumentationProfileIntegrity.documentSha256(changed))
        }
    }

    private fun scopes(multiplicity: DocumentMultiplicity, specification: ProjectSpecification): List<Scope> = when (multiplicity) {
        DocumentMultiplicity.SINGLE -> listOf(
            Scope(specification.project.id, specification.project.name, allSourceIds(specification)),
        )
        DocumentMultiplicity.PER_MODULE -> specification.modules.sortedBy { it.id }.map {
            Scope(it.id, it.name, listOf(it.id))
        }
        DocumentMultiplicity.PER_PACKAGE -> specification.packages.sortedBy { it.id }.map {
            Scope(it.id, it.qualifiedName, listOf(it.id, it.moduleId))
        }
        DocumentMultiplicity.PER_COMPONENT -> specification.components.sortedBy { it.id }.map {
            Scope(it.id, it.qualifiedName ?: it.name, listOfNotNull(it.id, it.moduleId, it.packageId))
        }
        DocumentMultiplicity.PER_FEATURE -> specification.features.map {
            Scope(
                it.id,
                it.name,
                (listOf(it.id, it.ownerComponentId) + it.participantComponentIds + it.entryPointIds + it.scenarioIds)
                    .distinct().sorted(),
            )
        }
        DocumentMultiplicity.PER_EXTERNAL_SYSTEM -> specification.relationships.map { it.targetId }
            .filter { it.startsWith("external:") }.distinct().sorted().map {
                Scope(it, it.removePrefix("external:"), listOf(it))
            }
    }

    private fun allSourceIds(specification: ProjectSpecification): List<String> = buildList {
        add(specification.project.id)
        addAll(specification.modules.map { it.id })
        addAll(specification.packages.map { it.id })
        specification.components.forEach { component ->
            add(component.id)
            addAll(component.apis.map { it.id })
            addAll(component.properties.map { it.id })
        }
        addAll(specification.relationships.map { it.id })
        addAll(specification.features.map { it.id })
        addAll(specification.entryPoints.map { it.id })
        specification.scenarios.forEach { scenario ->
            add(scenario.id)
            addAll(scenario.steps.map { it.id })
        }
    }.distinct().sorted()

    private fun evidenceFor(scope: Scope, specification: ProjectSpecification): List<Evidence> {
        val refs = linkedSetOf<String>()
        if (scope.id == specification.project.id) {
            refs += specification.evidence.map { it.id }
        } else {
            specification.modules.filter { it.id in scope.sourceElementIds }.forEach { refs += it.evidenceRefs }
            specification.packages.filter { it.id in scope.sourceElementIds || it.moduleId in scope.sourceElementIds }
                .forEach { refs += it.evidenceRefs }
            specification.components.filter {
                it.id in scope.sourceElementIds || it.moduleId in scope.sourceElementIds || it.packageId in scope.sourceElementIds
            }.forEach { component ->
                refs += component.evidenceRefs
                component.apis.forEach { refs += it.evidenceRefs }
                component.properties.forEach { refs += it.evidenceRefs }
            }
            specification.relationships.filter {
                it.id in scope.sourceElementIds || it.sourceId in scope.sourceElementIds || it.targetId in scope.sourceElementIds
            }.forEach { refs += it.evidenceRefs }
            specification.features.filter { it.id in scope.sourceElementIds }.forEach {
                refs += it.evidenceRefs
                refs += specification.entryPoints.filter { entry -> entry.id in it.entryPointIds }.flatMap { entry -> entry.evidenceRefs }
                refs += specification.scenarios.filter { scenario -> scenario.id in it.scenarioIds }.flatMap { scenario ->
                    scenario.evidenceRefs + scenario.steps.flatMap { step -> step.evidenceRefs }
                }
            }
        }
        return specification.evidence.filter { it.id in refs }.sortedBy { it.id }
    }

    private fun evidenceMatches(evidence: Evidence, requirement: SectionEvidenceRequirement): Boolean {
        if (evidenceClass(evidence) !in requirement.allowedEvidenceClasses) return false
        if (requirement.subject == EvidenceSubject.ANY) return true
        val searchable = listOfNotNull(evidence.type, evidence.file, evidence.symbol, evidence.summary)
            .joinToString(" ").lowercase()
        val tokens = when (requirement.subject) {
            EvidenceSubject.ANY -> emptyList()
            EvidenceSubject.PROJECT_PURPOSE -> listOf("project", "readme", "purpose", "overview", "vision", "manifesto")
            EvidenceSubject.ARCHITECTURE -> listOf("architecture", "module", "relationship", "dependency", "component")
            EvidenceSubject.MODULE -> listOf("module", "gradle", "source set", "build")
            EvidenceSubject.FEATURE -> listOf("feature", "scenario", "use case", "entry point")
            EvidenceSubject.CONTRACT -> listOf("contract", "schema", "database", "api", "request", "response")
            EvidenceSubject.TEST -> listOf("test", "junit", "verification", "coverage")
        }
        return tokens.any(searchable::contains)
    }

    private fun evidenceClass(evidence: Evidence): EvidenceClass = when {
        evidence.type.uppercase() == "AI_INFERRED" || evidence.type.uppercase().startsWith("AI_") ->
            EvidenceClass.AI_INFERRED
        evidence.type.uppercase().contains("DERIVED") || evidence.type.uppercase().contains("KNOWLEDGE") ->
            EvidenceClass.CORE_DERIVED
        else -> EvidenceClass.VERIFIED
    }

    private fun missingModelFinding(
        profile: DocumentationProfile,
        definition: DocumentDefinition,
        specification: ProjectSpecification,
    ): DocumentPlanningFinding? = when (definition.requiredModel) {
        DocumentationModelRequirement.NONE -> null
        DocumentationModelRequirement.FEATURE_MODEL -> if (specification.features.isEmpty()) DocumentPlanningFinding(
            DocumentPlanningFindingKind.MISSING_FEATURE_MODEL,
            documentStableId(profile, definition, "deferred"),
            definition.stableKey.value,
            "No validated DIR 0.4 Feature production entities are available.",
            blocking = false,
        ) else null
        DocumentationModelRequirement.CONTRACT_MODEL -> DocumentPlanningFinding(
            DocumentPlanningFindingKind.MISSING_CONTRACT_MODEL,
            documentStableId(profile, definition, "deferred"),
            definition.stableKey.value,
            "DIR 0.3 does not provide a canonical Contract production model.",
            blocking = false,
        )
    }

    private fun resolvePath(policy: DocumentPathPolicy, scope: Scope): String = when (policy) {
        is DocumentPathPolicy.Fixed -> DocumentationProfileCanonicalizer.normalizeRelativePath(policy.relativePath)
        is DocumentPathPolicy.Pattern -> DocumentationProfileCanonicalizer.normalizeRelativePath(
            policy.relativePathPattern
                .replace("{scopeId}", safeScopeSegment(scope))
                .replace("{scopeHash}", DocumentationProfileCanonicalizer.scopeHash(scope.id))
                .replace("{slug}", DocumentationProfileCanonicalizer.slug(scope.name))
                .replace("{moduleId}", safeScopeSegment(scope))
                .replace("{packageId}", safeScopeSegment(scope))
                .replace("{componentId}", safeScopeSegment(scope))
                .replace("{featureId}", safeScopeSegment(scope))
                .replace("{externalSystemId}", safeScopeSegment(scope)),
        )
    }

    private fun safeScopeSegment(scope: Scope): String =
        "${DocumentationProfileCanonicalizer.slug(scope.name)}-${DocumentationProfileCanonicalizer.scopeHash(scope.id).take(8)}"

    private fun definitionStableId(profile: DocumentationProfile, definition: DocumentDefinition): String =
        "document-definition:${profile.id.value}:${definition.stableKey.value}"

    private fun sectionStableId(
        profile: DocumentationProfile,
        definition: DocumentDefinition,
        section: SectionDefinition,
    ): String = "section:${profile.id.value}:${definition.stableKey.value}:${section.id.value}"

    private fun documentStableId(profile: DocumentationProfile, definition: DocumentDefinition, scopeId: String): String =
        "document:${profile.id.value}:${definition.stableKey.value}:$scopeId"

    private data class Scope(
        val id: String,
        val name: String,
        val sourceElementIds: List<String>,
    )

    private companion object {
        val findingComparator: Comparator<DocumentPlanningFinding> = compareBy(
            { it.kind.ordinal }, { it.documentStableId }, { it.subjectId }, { it.message },
        )
    }
}

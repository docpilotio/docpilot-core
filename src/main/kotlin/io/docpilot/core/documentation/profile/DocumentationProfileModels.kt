package io.docpilot.core.documentation.profile

import io.docpilot.core.api.DocumentationArtifactId
import io.docpilot.core.reconciliation.DocumentationOwnership

@JvmInline
public value class DocumentationProfileId(public val value: String) {
    init {
        require(value.isNotBlank()) { "Documentation profile id must not be blank." }
    }
}

@JvmInline
public value class DocumentationProfileVersion(public val value: Int) {
    init {
        require(value > 0) { "Documentation profile version must be positive." }
    }
}

@JvmInline
public value class DocumentStableKey(public val value: String) {
    init {
        require(value.isNotBlank()) { "Document stable key must not be blank." }
    }
}

@JvmInline
public value class SectionId(public val value: String) {
    init {
        require(value.isNotBlank()) { "Section id must not be blank." }
    }
}

public enum class DocumentationProjectKind {
    KOTLIN_ANDROID,
}

public enum class DocumentationProfileCompatibilityPolicy {
    EXACT_VERSION,
}

public enum class DocumentType {
    PROJECT_OVERVIEW,
    FEATURE_CATALOG,
    ARCHITECTURE_OVERVIEW,
    MODULE_ARCHITECTURE,
    FEATURE_SPECIFICATION,
    DOMAIN_MODEL,
    DATABASE_SCHEMA,
    EXTERNAL_API_CONTRACT,
    TEST_STRATEGY,
}

public enum class DocumentAudience {
    NEW_DEVELOPER,
    DEVELOPER,
    ARCHITECT,
    REVIEWER,
    MAINTAINER,
    TEST_ENGINEER,
    AI_DOCUMENTATION_REVIEWER,
}

public enum class DocumentMultiplicity {
    SINGLE,
    PER_MODULE,
    PER_PACKAGE,
    PER_COMPONENT,
    PER_FEATURE,
    PER_EXTERNAL_SYSTEM,
}

public sealed interface DocumentPathPolicy {
    public data class Fixed(public val relativePath: String) : DocumentPathPolicy

    public data class Pattern(public val relativePathPattern: String) : DocumentPathPolicy
}

public enum class RendererCapability {
    MARKDOWN_SECTION_RENDERING,
    MODULE_DEPENDENCY_DIAGRAM,
    FEATURE_CLASS_DIAGRAM,
    SEQUENCE_DIAGRAM,
    STATE_FLOW_DIAGRAM,
    DATA_FLOW_DIAGRAM,
    EVIDENCE_REFERENCE_RENDERING,
    UNKNOWN_FINDING_RENDERING,
    FEATURE_DOCUMENTATION_RENDERING,
}

public enum class EvidenceClass {
    VERIFIED,
    CORE_DERIVED,
    AI_INFERRED,
}

public enum class EvidenceSubject {
    ANY,
    PROJECT_PURPOSE,
    ARCHITECTURE,
    MODULE,
    FEATURE,
    CONTRACT,
    TEST,
}

public data class SectionEvidenceRequirement(
    public val minimumEvidenceCount: Int = 0,
    public val allowedEvidenceClasses: Set<EvidenceClass> = EvidenceClass.entries.toSet(),
    public val subject: EvidenceSubject = EvidenceSubject.ANY,
)

public enum class MissingDataBehavior {
    MARK_UNKNOWN,
    DEFER_DOCUMENT,
    BLOCK_DOCUMENT,
    OMIT_OPTIONAL_SECTION,
}

public enum class CompletenessPolicy {
    REQUIRE_ALL_REQUIRED_SECTIONS,
    ALLOW_EXPLICIT_PARTIAL,
}

public enum class OwnershipConflictBehavior {
    BLOCK,
    REQUIRE_RECONCILIATION,
}

public data class DocumentOwnershipPolicy(
    public val defaultOwnership: DocumentationOwnership,
    public val conflictBehavior: OwnershipConflictBehavior,
)

public data class DocumentDependencyRule(
    public val documentStableKey: DocumentStableKey,
    public val required: Boolean = true,
)

public enum class DocumentationModelRequirement {
    NONE,
    FEATURE_MODEL,
    CONTRACT_MODEL,
}

public data class SectionDefinition(
    public val id: SectionId,
    public val title: String,
    public val order: Int,
    public val required: Boolean,
    public val evidenceRequirement: SectionEvidenceRequirement = SectionEvidenceRequirement(),
    public val requiredCapabilities: Set<RendererCapability> = emptySet(),
    public val missingDataBehavior: MissingDataBehavior = if (required) {
        MissingDataBehavior.MARK_UNKNOWN
    } else {
        MissingDataBehavior.OMIT_OPTIONAL_SECTION
    },
)

public data class DocumentDefinition(
    public val type: DocumentType,
    public val stableKey: DocumentStableKey,
    public val purpose: String,
    public val primaryAudiences: Set<DocumentAudience>,
    public val secondaryAudiences: Set<DocumentAudience> = emptySet(),
    public val multiplicity: DocumentMultiplicity,
    public val pathPolicy: DocumentPathPolicy,
    public val sections: List<SectionDefinition>,
    public val rendererCapabilities: Set<RendererCapability> = emptySet(),
    public val completenessPolicy: CompletenessPolicy = CompletenessPolicy.ALLOW_EXPLICIT_PARTIAL,
    public val ownershipPolicy: DocumentOwnershipPolicy = DocumentOwnershipPolicy(
        DocumentationOwnership.DOCPILOT_OWNED,
        OwnershipConflictBehavior.BLOCK,
    ),
    public val dependencyRules: Set<DocumentDependencyRule> = emptySet(),
    public val requiredModel: DocumentationModelRequirement = DocumentationModelRequirement.NONE,
) {
    public val requiredSections: List<SectionDefinition> get() = sections.filter(SectionDefinition::required)
    public val optionalSections: List<SectionDefinition> get() = sections.filterNot(SectionDefinition::required)
}

public data class DocumentationProfile(
    public val id: DocumentationProfileId,
    public val version: DocumentationProfileVersion,
    public val displayName: String,
    public val supportedProjectKinds: Set<DocumentationProjectKind>,
    public val documentDefinitions: List<DocumentDefinition>,
    public val compatibilityPolicy: DocumentationProfileCompatibilityPolicy =
        DocumentationProfileCompatibilityPolicy.EXACT_VERSION,
)

public enum class ResolvedSectionStatus {
    AVAILABLE,
    UNKNOWN,
    DEFERRED,
    BLOCKED,
    UNSUPPORTED,
    OMITTED,
}

public data class ResolvedSectionContract(
    public val sectionStableId: String,
    public val sectionId: SectionId,
    public val title: String,
    public val required: Boolean,
    public val status: ResolvedSectionStatus,
    public val evidenceRefs: List<String>,
    public val missingEvidenceCount: Int,
    public val missingCapabilities: List<RendererCapability>,
)

public enum class DocumentPlanningStatus {
    READY,
    PARTIAL,
    DEFERRED,
    BLOCKED,
    UNSUPPORTED,
}

public enum class DocumentPlanningFindingKind {
    MISSING_SPECIFICATION_ELEMENT,
    MISSING_REQUIRED_EVIDENCE,
    MISSING_RENDERER_CAPABILITY,
    MISSING_FEATURE_MODEL,
    MISSING_CONTRACT_MODEL,
    UNSUPPORTED_MULTIPLICITY,
    PATH_CONFLICT,
    OWNERSHIP_CONFLICT,
    RECONCILIATION_REQUIRED,
    PROFILE_VERSION_MISMATCH,
    UNBOUND_LEGACY_ARTIFACT,
}

public data class DocumentPlanningFinding(
    public val kind: DocumentPlanningFindingKind,
    public val documentStableId: String,
    public val subjectId: String,
    public val message: String,
    public val blocking: Boolean,
)

public data class ResolvedDocumentContract(
    public val definitionStableId: String,
    public val documentStableId: String,
    public val type: DocumentType,
    public val stableKey: DocumentStableKey,
    public val sourceElementIds: List<String>,
    public val relativePath: String?,
    public val sections: List<ResolvedSectionContract>,
    public val requiredRendererCapabilities: List<RendererCapability>,
    public val availableRendererCapabilities: List<RendererCapability>,
    public val ownershipPolicy: DocumentOwnershipPolicy,
    public val resolvedOwnership: DocumentationOwnership,
    public val dependencyRules: List<DocumentDependencyRule>,
    public val status: DocumentPlanningStatus,
    public val findings: List<DocumentPlanningFinding>,
    public val semanticSha256: String,
) {
    public val availableSections: List<SectionId>
        get() = sections.filter { it.status == ResolvedSectionStatus.AVAILABLE }.map { it.sectionId }

    public val missingSections: List<SectionId>
        get() = sections.filter { it.required && it.status != ResolvedSectionStatus.AVAILABLE }.map { it.sectionId }
}

public enum class ProfileArtifactBindingStatus {
    EXACT_PATH,
    LEGACY_KIND_COMPATIBLE,
    UNBOUND,
    AMBIGUOUS,
}

public data class ProfileArtifactBinding(
    public val documentStableId: String,
    public val artifactId: DocumentationArtifactId?,
    public val status: ProfileArtifactBindingStatus,
    public val message: String,
)

public data class DocumentationProfileResolution(
    public val profileId: DocumentationProfileId,
    public val profileVersion: DocumentationProfileVersion,
    public val profileSemanticSha256: String,
    public val documents: List<ResolvedDocumentContract>,
    public val artifactBindings: List<ProfileArtifactBinding>,
    public val findings: List<DocumentPlanningFinding>,
    public val resolutionSha256: String,
)

public fun interface DocumentationRendererCapabilityProvider {
    public fun capabilities(): Set<RendererCapability>
}

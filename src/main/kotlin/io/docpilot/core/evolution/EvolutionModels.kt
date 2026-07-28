package io.docpilot.core.evolution

import io.docpilot.core.api.DocumentationArtifactDescriptor
import io.docpilot.core.api.DocumentationArtifactId
import io.docpilot.core.incremental.execution.DocumentationArtifactPlan
import io.docpilot.core.incremental.execution.DocumentationArtifactOperation
import io.docpilot.core.incremental.execution.ExistingDocumentationArtifact
import io.docpilot.core.incremental.specification.snapshot.StoredSpecificationSnapshot
import io.docpilot.core.reconciliation.DocumentationOwnershipManifest
import io.docpilot.core.reconciliation.DocumentationReconciliationPlan
import io.docpilot.core.reconciliation.DocumentationReconciliationResult
import io.docpilot.core.specification.RelationshipProjectionReport

public object DocumentationEvolutionFormat {
    public const val CURRENT_VERSION: Int = 1
}

public enum class EvolutionChangeKind {
    ENTITY_ADDED,
    ENTITY_REMOVED,
    ENTITY_MODIFIED,
    ENTITY_MOVED,
    PROPERTY_CHANGED,
    API_CHANGED,
    RELATIONSHIP_ADDED,
    RELATIONSHIP_REMOVED,
    RELATIONSHIP_MODIFIED,
    ARTIFACT_CREATED,
    ARTIFACT_UPDATED,
    ARTIFACT_RETAINED,
    OWNERSHIP_CHANGED,
    RECONCILIATION_CONFLICTED,
    USER_DECISION_APPLIED,
}

public enum class EvolutionSubjectKind {
    PROJECT,
    MODULE,
    PACKAGE,
    COMPONENT,
    API,
    PROPERTY,
    RELATIONSHIP,
    ARTIFACT,
    OWNERSHIP,
    RECONCILIATION_OPERATION,
    USER_DECISION,
}

public enum class EvolutionConfidenceClass {
    OBSERVED,
    CORE_DERIVED,
    USER_AUTHORIZED,
    OPTIONAL_AI_NARRATIVE,
}

public enum class EvolutionCoverageState {
    COMPLETE,
    PARTIAL_MISSING_OPTIONAL_EVIDENCE,
    BLOCKED_INCOMPATIBLE_FORMAT,
    BLOCKED_MISSING_REQUIRED_EVIDENCE,
    BLOCKED_INTEGRITY_FAILURE,
}

public enum class EvolutionCoverageFindingKind {
    MISSING_SOURCE_EVIDENCE,
    MISSING_RELATIONSHIP_REPORT,
    MISSING_ARTIFACT_STATE,
    MISSING_CAUSAL_PATH,
    INCOMPATIBLE_SNAPSHOT,
    INVALID_SNAPSHOT_INTEGRITY,
    INVALID_ARTIFACT_PLAN,
    INVALID_RELATIONSHIP_REPORT,
    INVALID_RECONCILIATION_EVIDENCE,
    INVALID_OWNERSHIP_MANIFEST,
}

public data class EvolutionCoverageFinding(
    public val kind: EvolutionCoverageFindingKind,
    public val subjectId: String,
    public val message: String,
    public val required: Boolean,
)

public data class EvolutionCoverage(
    public val state: EvolutionCoverageState,
    public val findings: List<EvolutionCoverageFinding> = emptyList(),
)

public data class DocumentationEvolutionChange(
    public val changeId: String,
    public val subjectId: String,
    public val subjectKind: EvolutionSubjectKind,
    public val kind: EvolutionChangeKind,
    public val beforeSha256: String?,
    public val afterSha256: String?,
    public val previousParentId: String? = null,
    public val currentParentId: String? = null,
    public val changedFields: List<String> = emptyList(),
    public val evidenceRefs: List<String> = emptyList(),
    public val causalPredecessorIds: List<String> = emptyList(),
    public val affectedArtifactIds: List<String> = emptyList(),
    public val confidenceClass: EvolutionConfidenceClass,
    public val coverageState: EvolutionCoverageState,
)

public enum class EvolutionGraphNodeKind {
    SOURCE_EVIDENCE,
    SPECIFICATION_CHANGE,
    RELATIONSHIP_CHANGE,
    ARTIFACT,
    ARTIFACT_PLAN_ACTION,
    OWNERSHIP_DECISION,
    USER_DECISION,
    RECONCILIATION_OPERATION,
    APPLIED_RESULT,
    DOCUMENT_STATE,
}

public enum class EvolutionGraphEdgeKind {
    CAUSES,
    SELECTS,
    REFRESHES,
    PRODUCES,
    PERMITS,
    PROHIBITS,
    AUTHORIZES,
    RETAINS,
    CHANGES,
}

public data class DocumentationEvolutionGraphNode(
    public val nodeId: String,
    public val kind: EvolutionGraphNodeKind,
    public val subjectId: String,
    public val evidenceRefs: List<String> = emptyList(),
)

public data class DocumentationEvolutionGraphEdge(
    public val sourceNodeId: String,
    public val targetNodeId: String,
    public val kind: EvolutionGraphEdgeKind,
)

public data class DocumentationEvolutionGraph(
    public val nodes: List<DocumentationEvolutionGraphNode>,
    public val edges: List<DocumentationEvolutionGraphEdge>,
    public val graphSha256: String,
)

public data class EvolutionArtifactImpact(
    public val artifactId: String,
    public val relativePath: String,
    public val operation: DocumentationArtifactOperation,
    public val selectionReasons: List<String>,
    public val sourceChangeIds: List<String>,
    public val causalChangeIds: List<String>,
    public val beforeArtifactSha256: String?,
    public val afterArtifactSha256: String?,
)

public data class EvolutionArtifactState(
    public val artifactId: DocumentationArtifactId,
    public val relativePath: String,
    public val mediaType: String,
    public val contentSha256: String,
) {
    init {
        require(EvolutionCanonicalizer.isSafeRelativePath(relativePath)) { "Unsafe artifact path: $relativePath" }
        require(contentSha256.matches(Regex("[0-9a-f]{64}"))) { "Invalid artifact SHA-256." }
    }
}

public data class DocumentationEvolutionRequest(
    public val beforeSnapshot: StoredSpecificationSnapshot,
    public val afterSnapshot: StoredSpecificationSnapshot,
    public val beforeCatalog: List<DocumentationArtifactDescriptor>,
    public val afterCatalog: List<DocumentationArtifactDescriptor>,
    public val artifactPlan: DocumentationArtifactPlan,
    public val existingArtifacts: List<ExistingDocumentationArtifact>,
    public val beforeRelationshipReport: RelationshipProjectionReport? = null,
    public val afterRelationshipReport: RelationshipProjectionReport? = null,
    public val reconciliationPlan: DocumentationReconciliationPlan? = null,
    public val reconciliationResult: DocumentationReconciliationResult? = null,
    public val beforeOwnershipManifests: List<DocumentationOwnershipManifest> = emptyList(),
    public val afterOwnershipManifests: List<DocumentationOwnershipManifest> = emptyList(),
    public val beforeArtifactStates: List<EvolutionArtifactState> = emptyList(),
    public val afterArtifactStates: List<EvolutionArtifactState> = emptyList(),
    public val additionalEvidenceRefs: List<String> = emptyList(),
)

public data class DocumentationEvolutionReport(
    public val formatVersion: Int = DocumentationEvolutionFormat.CURRENT_VERSION,
    public val projectId: String,
    public val beforeStateSha256: String,
    public val afterStateSha256: String,
    public val changes: List<DocumentationEvolutionChange>,
    public val causalGraph: DocumentationEvolutionGraph,
    public val impactedArtifacts: List<EvolutionArtifactImpact>,
    public val coverage: EvolutionCoverage,
    public val evidenceRefs: List<String>,
    public val reportSha256: String,
)

public fun interface DocumentationEvolutionAnalyzer {
    public fun analyze(request: DocumentationEvolutionRequest): DocumentationEvolutionReport
}

public fun interface EvolutionNarrativeRenderer {
    public fun render(report: DocumentationEvolutionReport): String
}

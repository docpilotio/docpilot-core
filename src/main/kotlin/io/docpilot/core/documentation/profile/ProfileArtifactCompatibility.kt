package io.docpilot.core.documentation.profile

import io.docpilot.core.api.DocumentationArtifactDescriptor
import io.docpilot.core.api.DocumentationArtifactKind

public object ProfileArtifactCompatibility {
    public fun bind(
        documents: List<ResolvedDocumentContract>,
        artifactCatalog: List<DocumentationArtifactDescriptor>,
    ): List<ProfileArtifactBinding> {
        val byPath = artifactCatalog.groupBy { logicalPath(it.relativePath) }
        return documents.sortedBy { it.documentStableId }.map { document ->
            val exact = document.relativePath?.let(::logicalPath)?.let(byPath::get).orEmpty()
            when {
                exact.size == 1 -> ProfileArtifactBinding(
                    document.documentStableId,
                    exact.single().artifactId,
                    ProfileArtifactBindingStatus.EXACT_PATH,
                    "Profile path matches the existing Artifact Catalog.",
                )
                exact.size > 1 -> ProfileArtifactBinding(
                    document.documentStableId,
                    null,
                    ProfileArtifactBindingStatus.AMBIGUOUS,
                    "Multiple legacy Artifacts use the resolved Profile path.",
                )
                else -> legacyKindBinding(document, artifactCatalog)
            }
        }
    }

    private fun logicalPath(path: String): String =
        DocumentationProfileCanonicalizer.normalizeRelativePath(path).removePrefix("docs/")

    private fun legacyKindBinding(
        document: ResolvedDocumentContract,
        artifactCatalog: List<DocumentationArtifactDescriptor>,
    ): ProfileArtifactBinding {
        val compatibleKind = when (document.type) {
            DocumentType.PROJECT_OVERVIEW -> DocumentationArtifactKind.PROJECT_OVERVIEW
            DocumentType.ARCHITECTURE_OVERVIEW -> DocumentationArtifactKind.ARCHITECTURE_OVERVIEW
            DocumentType.FEATURE_CATALOG -> DocumentationArtifactKind.FEATURE_CATALOG
            DocumentType.FEATURE_SPECIFICATION -> DocumentationArtifactKind.FEATURE_DETAIL
            DocumentType.CONTRACT_CATALOG -> DocumentationArtifactKind.CONTRACT_CATALOG
            DocumentType.CONTRACT_DETAIL -> DocumentationArtifactKind.CONTRACT_DETAIL
            else -> null
        }
        val candidates = compatibleKind?.let { kind -> artifactCatalog.filter { it.kind == kind } }.orEmpty()
        return when (candidates.size) {
            1 -> ProfileArtifactBinding(
                document.documentStableId,
                candidates.single().artifactId,
                ProfileArtifactBindingStatus.LEGACY_KIND_COMPATIBLE,
                "Legacy Artifact kind is compatible, but the Profile path is additive and unchanged legacy output is retained.",
            )
            0 -> ProfileArtifactBinding(
                document.documentStableId,
                null,
                ProfileArtifactBindingStatus.UNBOUND,
                "No existing RFC-0052 Artifact is bound to this Profile document contract.",
            )
            else -> ProfileArtifactBinding(
                document.documentStableId,
                null,
                ProfileArtifactBindingStatus.AMBIGUOUS,
                "Multiple legacy Artifacts are compatible with this Profile document contract.",
            )
        }
    }
}

package io.docpilot.core.documentation.profile

public object DocumentationProfileIntegrity {
    public fun profileSha256(profile: DocumentationProfile): String = DocumentationProfileCanonicalizer.sha256(
        profilePayload(DocumentationProfileValidator.validate(profile)),
    )

    public fun documentSha256(document: ResolvedDocumentContract): String = DocumentationProfileCanonicalizer.sha256(
        documentPayload(document, includeSemanticSha = false),
    )

    public fun resolutionSha256(resolution: DocumentationProfileResolution): String =
        DocumentationProfileCanonicalizer.sha256(resolutionPayload(resolution, includeResolutionSha = false))

    public fun verifyProfile(profile: DocumentationProfile, expectedSha256: String): Boolean =
        profileSha256(profile) == expectedSha256

    public fun verifyDocument(document: ResolvedDocumentContract): Boolean =
        documentSha256(document) == document.semanticSha256

    public fun verifyResolution(resolution: DocumentationProfileResolution): Boolean =
        resolution.documents.all(::verifyDocument) && resolutionSha256(resolution) == resolution.resolutionSha256

    private fun profilePayload(profile: DocumentationProfile): String = buildString {
        appendLine("profile|${profile.id.value}|${profile.version.value}|${profile.compatibilityPolicy.name}|${escape(profile.displayName.trim())}")
        appendLine("project-kinds|${profile.supportedProjectKinds.joinToString(",") { it.name }}")
        profile.documentDefinitions.forEach { definition ->
            appendLine(
                "document|${definition.type.name}|${definition.stableKey.value}|${escape(definition.purpose.trim())}|" +
                    "${definition.primaryAudiences.joinToString(",") { it.name }}|" +
                    "${definition.secondaryAudiences.joinToString(",") { it.name }}|" +
                    "${definition.multiplicity.name}|${pathValue(definition.pathPolicy)}|" +
                    "${definition.rendererCapabilities.joinToString(",") { it.name }}|" +
                    "${definition.completenessPolicy.name}|${definition.ownershipPolicy.defaultOwnership.name}|" +
                    "${definition.ownershipPolicy.conflictBehavior.name}|${definition.requiredModel.name}",
            )
            definition.sections.forEach { section ->
                appendLine(
                    "section|${section.id.value}|${escape(section.title)}|${section.order}|${section.required}|" +
                        "${section.evidenceRequirement.minimumEvidenceCount}|" +
                        "${section.evidenceRequirement.allowedEvidenceClasses.joinToString(",") { it.name }}|" +
                        "${section.evidenceRequirement.subject.name}|" +
                        "${section.requiredCapabilities.joinToString(",") { it.name }}|" +
                        section.missingDataBehavior.name,
                )
            }
            definition.dependencyRules.forEach { rule ->
                appendLine("dependency|${rule.documentStableKey.value}|${rule.required}")
            }
        }
    }

    private fun resolutionPayload(
        resolution: DocumentationProfileResolution,
        includeResolutionSha: Boolean,
    ): String = buildString {
        appendLine(
            "resolution|${resolution.profileId.value}|${resolution.profileVersion.value}|" +
                resolution.profileSemanticSha256,
        )
        resolution.documents.forEach { append(documentPayload(it, includeSemanticSha = true)) }
        resolution.artifactBindings.forEach {
            appendLine("binding|${it.documentStableId}|${escape(it.artifactId?.value.orEmpty())}|${it.status.name}|${escape(it.message)}")
        }
        resolution.findings.forEach {
            appendLine(
                "finding|${it.kind.name}|${it.documentStableId}|${escape(it.subjectId)}|${it.blocking}|${escape(it.message)}",
            )
        }
        if (includeResolutionSha) appendLine("sha|${resolution.resolutionSha256}")
    }

    private fun documentPayload(document: ResolvedDocumentContract, includeSemanticSha: Boolean): String = buildString {
        appendLine(
            "resolved-document|${document.definitionStableId}|${document.documentStableId}|${document.type.name}|" +
                "${document.stableKey.value}|${document.sourceElementIds.joinToString(",")}|" +
                "${document.relativePath.orEmpty()}|${document.status.name}|" +
                "${document.requiredRendererCapabilities.joinToString(",") { it.name }}|" +
                "${document.availableRendererCapabilities.joinToString(",") { it.name }}|" +
                "${document.ownershipPolicy.defaultOwnership.name}|${document.ownershipPolicy.conflictBehavior.name}|" +
                "${document.resolvedOwnership.name}",
        )
        document.dependencyRules.forEach {
            appendLine("resolved-dependency|${it.documentStableKey.value}|${it.required}")
        }
        document.sections.forEach {
            appendLine(
                "resolved-section|${it.sectionStableId}|${it.sectionId.value}|${escape(it.title)}|${it.required}|${it.status.name}|" +
                    "${it.evidenceRefs.joinToString(",")}|${it.missingEvidenceCount}|" +
                    it.missingCapabilities.joinToString(",") { capability -> capability.name },
            )
        }
        document.findings.forEach {
            appendLine("document-finding|${it.kind.name}|${escape(it.subjectId)}|${it.blocking}|${escape(it.message)}")
        }
        if (includeSemanticSha) appendLine("document-sha|${document.semanticSha256}")
    }

    private fun pathValue(policy: DocumentPathPolicy): String = when (policy) {
        is DocumentPathPolicy.Fixed -> "fixed:${escape(policy.relativePath)}"
        is DocumentPathPolicy.Pattern -> "pattern:${escape(policy.relativePathPattern)}"
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("|", "\\|")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
}
